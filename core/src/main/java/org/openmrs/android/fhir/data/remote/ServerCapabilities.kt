/*
* BSD 3-Clause License
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
*    contributors may be used to endorse or promote products derived from
*    this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.openmrs.android.fhir.data.remote

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openmrs.android.fhir.config.AppConfig

/**
 * What the server of this device supports, from the checks run against it ([ServerChecker]) and
 * from what it answered to the changes the app sent. The app asks [current] before it offers
 * something that needs the server: a feature the server was found not to support is left out, so
 * the app only does what the server can do. One report is kept, for the server in use.
 */
@Singleton
class ServerCapabilities
@Inject
constructor(
  context: Context,
  private val probe: ApiManager,
) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val lock = Mutex()

  private val enabled = AppConfig.get(appContext).serverCompatibility.enabled

  private val _report = MutableStateFlow(load())
  val report: StateFlow<ServerReport> = _report.asStateFlow()

  private val _features = MutableStateFlow(ServerFeatures.of(_report.value))
  val features: StateFlow<ServerFeatures> = _features.asStateFlow()

  private val _running = MutableStateFlow(false)
  val running: StateFlow<Boolean> = _running.asStateFlow()

  fun current(): ServerFeatures = if (enabled) _features.value else ServerFeatures()

  fun reload() {
    publish(load())
  }

  suspend fun check(force: Boolean = true) {
    if (!enabled) return
    lock.withLock {
      val stale = System.currentTimeMillis() - _report.value.checkedAt > STALE_MS
      if (!force && !stale) return
      _running.value = true
      try {
        val results = checker().readOnly()
        val server = results.firstOrNull { it.key == Checks.SERVER }
        // A server that did not answer says nothing about what it supports: what was found stays.
        publish(
          if (server?.state == CheckState.OK) {
            _report.value.withResults(results, System.currentTimeMillis())
          } else {
            _report.value.withResults(listOfNotNull(server), _report.value.checkedAt)
          },
        )
      } finally {
        _running.value = false
      }
    }
  }

  suspend fun writeTest() {
    if (!enabled) return
    lock.withLock {
      _running.value = true
      try {
        val result = checker().writeTest()
        publish(_report.value.withResults(listOf(result), System.currentTimeMillis()))
      } finally {
        _running.value = false
      }
    }
  }

  /**
   * The server refused a change to a patient list: if the answer says it cannot do this at all, the
   * feature is left out from now on, until a check finds the server able to.
   */
  fun learnRefusal(code: Int, text: String?, members: Boolean) {
    if (!enabled || !ServerChecker.refusalMeansUnsupported(code, text)) return
    val key = if (members) Checks.GROUP_MEMBERS else Checks.GROUPS_WRITE
    if (_report.value.result(key)?.state == CheckState.UNSUPPORTED) return
    val result =
      CheckResult(
        key,
        CheckState.UNSUPPORTED,
        "Refused when sent: $code${text?.takeIf { it.isNotBlank() }?.let { ": ${it.take(300)}" }.orEmpty()}",
        at = System.currentTimeMillis(),
      )
    publish(_report.value.withResults(listOf(result), System.currentTimeMillis()))
  }

  private fun checker(): ServerChecker {
    val concepts = AppConfig.get(appContext).groupSessions
    return ServerChecker(
      probe,
      ServerEndpoints(ServerSettings.fhirBaseUrl(appContext), ServerSettings.restUrl(appContext)),
      concepts.cohortIdentifierConcept,
      concepts.cohortNameConcept,
    )
  }

  private fun load(): ServerReport {
    val url = ServerSettings.activeUrl(appContext)
    return ServerReport.fromJson(prefs.getString(KEY_REPORT, null))?.takeIf { it.serverUrl == url }
      ?: ServerReport(url)
  }

  private fun publish(report: ServerReport) {
    _report.value = report
    _features.value = ServerFeatures.of(report)
    prefs.edit().putString(KEY_REPORT, report.toJson()).apply()
  }

  private companion object {
    const val PREFS = "server_capabilities"
    const val KEY_REPORT = "report"
    const val STALE_MS = 12L * 60 * 60 * 1000
  }
}
