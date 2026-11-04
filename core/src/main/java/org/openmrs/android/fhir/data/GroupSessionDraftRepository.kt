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

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft
import org.openmrs.android.fhir.data.database.model.GroupSessionDraftSummary

@Singleton
class GroupSessionDraftRepository @Inject constructor(private val database: AppDatabase) {

  private val lock = Mutex()

  /** What was last written for each form, as a digest: the draft itself is large. */
  private val written = mutableMapOf<String, ByteArray>()

  /**
   * Saves the draft. It holds every answer and both forms, and it is saved on every switch of
   * patient: when nothing changed since it was last written, only its time is updated, which leaves
   * the database exactly as a full write would.
   */
  suspend fun upsertDraft(draft: GroupSessionDraft) =
    lock.withLock {
      val dao = database.dao()
      val now = System.currentTimeMillis()
      val digest = draft.contentDigest()
      val unchanged = written[draft.questionnaireId]?.contentEquals(digest) == true
      // A draft deleted behind our back (rows touched = 0) is written again, whole.
      if (unchanged && dao.touchGroupSessionDraft(draft.questionnaireId, now) > 0) return@withLock
      dao.upsertGroupSessionDraft(draft.copy(lastUpdated = now))
      written[draft.questionnaireId] = digest
    }

  suspend fun getDraft(questionnaireId: String): GroupSessionDraft? {
    return database.dao().getGroupSessionDraft(questionnaireId)
  }

  /** The drafts worth resuming, by form. */
  suspend fun pendingSessions(): Map<String, GroupSessionDraftSummary> =
    database
      .dao()
      .getGroupSessionDraftSummaries()
      .filter { it.hasContent }
      .associateBy { it.questionnaireId }

  /** Drafts with nothing in them are removed. */
  suspend fun deleteEmptyDrafts() {
    database
      .dao()
      .getGroupSessionDraftSummaries()
      .filterNot { it.hasContent }
      .forEach { deleteDraft(it.questionnaireId) }
  }

  suspend fun deleteDraft(questionnaireId: String) =
    lock.withLock {
      written.remove(questionnaireId)
      database.dao().deleteGroupSessionDraft(questionnaireId)
    }
}

/** A digest of everything in the draft that is saved, but when it was. */
private fun GroupSessionDraft.contentDigest(): ByteArray {
  val digest = MessageDigest.getInstance("SHA-256")

  fun add(text: String?) {
    if (text == null) {
      digest.update(-1)
      return
    }
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    digest.update(bytes.size.toString().toByteArray(StandardCharsets.UTF_8))
    digest.update(0)
    digest.update(bytes)
  }

  add(questionnaireId)
  add(sessionId)
  add(patientIds.size.toString())
  patientIds.forEach(::add)
  add(patientResponses.size.toString())
  patientResponses.forEach { (patientId, response) ->
    add(patientId)
    add(response)
  }
  add(screenerResponse)
  add(encounterQuestionnaireJson)
  add(screenerQuestionnaireJson)
  add(sessionDate?.toString())
  add(screenerCompleted.toString())
  return digest.digest()
}
