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
package org.openmrs.android.fhir.data

import android.content.Context
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.AcceptRemoteConflictResolver
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.data.remote.ApiManager
import timber.log.Timber

/**
 * Brings what the server has published onto the device: questionnaires the device does not hold
 * yet, and newer versions of the ones it holds. Read only towards the server; what is stored is
 * stored like a download, so nothing is queued for upload.
 */
class QuestionnaireUpdater
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val apiManager: ApiManager,
) {
  sealed interface Outcome {
    /**
     * [added] new questionnaires and [updated] newer versions were stored; the server publishes
     * [available] in total.
     */
    data class Added(val added: Int, val updated: Int, val available: Int) : Outcome

    data class UpToDate(val available: Int) : Outcome

    data class Failed(val reason: String?) : Outcome
  }

  suspend fun fetchMissing(): Outcome =
    withContext(Dispatchers.IO) {
      try {
        val remote = apiManager.fetchQuestionnaires()
        val stored =
          fhirEngine
            .search<Questionnaire> {}
            .associate { it.resource.idElement.idPart to it.resource }
        val missing = remote.filter { it.idElement.idPart !in stored }
        val newer =
          remote.filter { candidate ->
            stored[candidate.idElement.idPart]?.let { candidate.isNewerThan(it) } == true
          }
        val toStore = missing + newer
        if (toStore.isEmpty()) {
          AuditLog.record(
            AuditAction.FORMS_FETCHED,
            AuditOutcome.SUCCESS,
            "added" to 0,
            "updated" to 0,
            "available" to remote.size,
          )
          Outcome.UpToDate(remote.size)
        } else {
          // Stored like a download, so no local change is queued for upload: the server owns them.
          fhirEngine.syncDownload(AcceptRemoteConflictResolver) { flowOf(toStore) }
          AuditLog.record(
            AuditAction.FORMS_FETCHED,
            AuditOutcome.SUCCESS,
            "added" to missing.size,
            "updated" to newer.size,
            "available" to remote.size,
          )
          Outcome.Added(missing.size, newer.size, remote.size)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Fetching questionnaires failed")
        AuditLog.failure(AuditAction.FORMS_FETCHED, "error" to e.javaClass.simpleName)
        Outcome.Failed(e.localizedMessage)
      }
    }

  /** Published after the copy held here: by its update time, or else by its version label. */
  private fun Questionnaire.isNewerThan(held: Questionnaire): Boolean {
    val mine = meta.lastUpdated
    val theirs = held.meta.lastUpdated
    return when {
      mine != null && theirs != null -> mine.after(theirs)
      mine != null -> true
      else -> hasVersion() && version != held.version
    }
  }
}
