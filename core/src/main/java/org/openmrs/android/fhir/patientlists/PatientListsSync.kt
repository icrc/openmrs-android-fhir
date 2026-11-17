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
package org.openmrs.android.fhir.patientlists

import android.content.Context
import com.google.android.fhir.FhirEngineProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.auth.GuestSession
import timber.log.Timber

/**
 * When the patient lists are sent and read, outside the patient list screens: after a sync (what it
 * was asked to do decides: a download-only run sends nothing, an upload-only run reads nothing) and
 * with the live upload. A guest session has no server to talk to.
 */
object PatientListsSync {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  suspend fun run(context: Context, send: Boolean, refresh: Boolean): SendResult? {
    if (GuestSession.isActive(context)) return null
    val repository =
      FhirApplication.appComponent(context.applicationContext).patientListRepository()
    var result: SendResult? = null
    // Lists created before a location was chosen get it when they are sent (`send`), and wait
    // until there is one.
    if (send) {
      repository.releaseUploaded(
        repository.uploadedPatientFinder(
          FhirEngineProvider.getInstance(context.applicationContext),
        ),
      )
    }
    if (send && repository.pendingNow() > 0) {
      result = repository.send()
      if (result.unreachable) return result
    }
    if (refresh) repository.refreshList()
    return result
  }

  fun runInBackground(context: Context, send: Boolean, refresh: Boolean) {
    scope.launch {
      try {
        run(context, send, refresh)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.w(e, "Could not send or read the patient lists")
      }
    }
  }
}
