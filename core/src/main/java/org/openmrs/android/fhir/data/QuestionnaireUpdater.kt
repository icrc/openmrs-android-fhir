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
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.extensions.bundledQuestionnaireIds
import timber.log.Timber

/**
 * Brings questionnaires the server has published since the app was built (or last synced) onto the
 * device. Only questionnaires the app does not have yet are added; existing ones are left exactly
 * as they are.
 */
class QuestionnaireUpdater
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val apiManager: ApiManager,
) {
  sealed interface Outcome {
    /** [added] new questionnaires were stored; the server publishes [available] in total. */
    data class Added(val added: Int, val available: Int) : Outcome

    data class UpToDate(val available: Int) : Outcome

    data class Failed(val reason: String?) : Outcome
  }

  suspend fun fetchMissing(): Outcome =
    withContext(Dispatchers.IO) {
      try {
        val remote = apiManager.fetchQuestionnaires()
        val known = knownQuestionnaireIds()
        val missing = remote.filter { it.idElement.idPart !in known }
        if (missing.isEmpty()) {
          Outcome.UpToDate(remote.size)
        } else {
          // Stored like a download, so no local change is queued for upload: the server owns them.
          fhirEngine.syncDownload(AcceptRemoteConflictResolver) { flowOf(missing) }
          Outcome.Added(missing.size, remote.size)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Fetching questionnaires failed")
        Outcome.Failed(e.localizedMessage)
      }
    }

  /** Ids already in the system: synced into the engine, or bundled with the app. */
  private suspend fun knownQuestionnaireIds(): Set<String> =
    fhirEngine.search<Questionnaire> {}.map { it.resource.idElement.idPart }.toSet() +
      applicationContext.bundledQuestionnaireIds()
}
