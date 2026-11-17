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

package org.openmrs.android.fhir.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.data.remote.CheckResult
import org.openmrs.android.fhir.data.remote.CheckState
import org.openmrs.android.fhir.data.remote.Checks
import org.openmrs.android.fhir.data.remote.ServerCapabilities

data class CompatibilityRow(val key: String, val result: CheckResult?)

/**
 * @property checkedAt when the checks last got an answer, 0 when they never did
 * @property reachable false when the last attempt could not talk to the server
 */
data class ServerCompatibilityUiState(
  val rows: List<CompatibilityRow> = emptyList(),
  val checkedAt: Long = 0L,
  val running: Boolean = false,
  val reachable: Boolean = true,
  val serverUrl: String = "",
  val unreachableDetail: String? = null,
)

class ServerCompatibilityViewModel
@Inject
constructor(private val capabilities: ServerCapabilities) : ViewModel() {

  private val reachable = MutableStateFlow<Pair<Boolean, String?>>(true to null)

  val uiState: StateFlow<ServerCompatibilityUiState> =
    combine(capabilities.report, capabilities.running, reachable) { report, running, reach ->
        ServerCompatibilityUiState(
          rows = Checks.ORDER.map { key -> CompatibilityRow(key, report.result(key)) },
          checkedAt = report.checkedAt,
          running = running,
          reachable = reach.first,
          serverUrl = report.serverUrl,
          unreachableDetail = reach.second,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerCompatibilityUiState())

  init {
    viewModelScope.launch { run { capabilities.check(force = false) } }
  }

  fun checkAgain() {
    viewModelScope.launch {
      AuditLog.record(
        AuditAction.SETTINGS_CHANGED,
        AuditOutcome.SUCCESS,
        "setting" to "server_check",
      )
      run { capabilities.check(force = true) }
    }
  }

  fun testWriting() {
    viewModelScope.launch {
      AuditLog.record(
        AuditAction.SETTINGS_CHANGED,
        AuditOutcome.SUCCESS,
        "setting" to "server_write_test",
      )
      run { capabilities.writeTest() }
    }
  }

  fun reportText(): String {
    val report = capabilities.report.value
    return buildString {
      appendLine("Server: ${report.serverUrl}")
      appendLine(
        "Checked: ${if (report.checkedAt > 0) java.util.Date(report.checkedAt) else "never"}",
      )
      Checks.ORDER.forEach { key ->
        val result = report.result(key)
        append(key).append(": ").append(result?.state?.name ?: "NOT_CHECKED")
        result?.subject?.let { append(" [").append(it).append("]") }
        result?.detail?.let { append(" - ").append(it) }
        appendLine()
      }
    }
  }

  private suspend fun run(step: suspend () -> Unit) {
    step()
    val server = capabilities.report.value.result(Checks.SERVER)
    reachable.value =
      if (server == null || server.state == CheckState.OK) true to null else false to server.detail
  }
}
