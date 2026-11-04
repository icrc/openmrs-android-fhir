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

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.Dao
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft

class GroupSessionDraftRepositoryTest {
  private val dao = mock<Dao>()
  private val database = mock<AppDatabase> { on { dao() } doReturn dao }
  private val repository = GroupSessionDraftRepository(database)

  private fun draft(
    answers: Map<String, String> = mapOf("p1" to "{\"a\":1}", "p2" to "{}"),
    screener: String? = "{screener}",
    completed: Boolean = true,
  ) =
    GroupSessionDraft(
      questionnaireId = "q",
      sessionId = "s",
      patientIds = listOf("p1", "p2"),
      patientResponses = answers,
      screenerResponse = screener,
      encounterQuestionnaireJson = "{form}",
      screenerQuestionnaireJson = "{screenerForm}",
      sessionDate = 5L,
      screenerCompleted = completed,
    )

  @Test
  fun theFirstSaveWritesTheWholeDraft() = runTest {
    repository.upsertDraft(draft())

    verify(dao).upsertGroupSessionDraft(any())
    verify(dao, never()).touchGroupSessionDraft(any(), any())
  }

  @Test
  fun aSaveWithNothingNewOnlyUpdatesTheTimeOfTheDraft() = runTest {
    whenever(dao.touchGroupSessionDraft(eq("q"), any())).thenReturn(1)

    repository.upsertDraft(draft())
    repository.upsertDraft(draft())
    repository.upsertDraft(draft())

    verify(dao, times(1)).upsertGroupSessionDraft(any())
    verify(dao, times(2)).touchGroupSessionDraft(eq("q"), any())
  }

  @Test
  fun everyKindOfChangeIsWritten() = runTest {
    whenever(dao.touchGroupSessionDraft(any(), any())).thenReturn(1)
    val changes =
      listOf(
        draft(answers = mapOf("p1" to "{\"a\":2}", "p2" to "{}")),
        draft(answers = mapOf("p1" to "{\"a\":2}")),
        draft(answers = mapOf("p1" to "{\"a\":2}"), screener = null),
        draft(answers = mapOf("p1" to "{\"a\":2}"), screener = null, completed = false),
        draft(answers = mapOf("p1" to "{\"a\":2}"), screener = null, completed = false)
          .copy(sessionDate = 6L),
        draft(answers = mapOf("p1" to "{\"a\":2}"), screener = null, completed = false)
          .copy(sessionDate = 6L, encounterQuestionnaireJson = "{form2}"),
      )

    repository.upsertDraft(draft())
    changes.forEach { repository.upsertDraft(it) }

    verify(dao, times(1 + changes.size)).upsertGroupSessionDraft(any())
    verify(dao, never()).touchGroupSessionDraft(any(), any())
  }

  @Test
  fun answersMovedBetweenPatientsAreNotTakenForTheSameDraft() = runTest {
    whenever(dao.touchGroupSessionDraft(any(), any())).thenReturn(1)

    repository.upsertDraft(draft(answers = mapOf("p1" to "ab", "p2" to "c")))
    repository.upsertDraft(draft(answers = mapOf("p1" to "a", "p2" to "bc")))

    verify(dao, times(2)).upsertGroupSessionDraft(any())
  }

  @Test
  fun aDraftThatWasDeletedMeanwhileIsWrittenAgainInFull() = runTest {
    // Nothing to touch: the row is gone (for example the data was erased).
    whenever(dao.touchGroupSessionDraft(any(), any())).thenReturn(0)

    repository.upsertDraft(draft())
    repository.upsertDraft(draft())

    verify(dao, times(2)).upsertGroupSessionDraft(any())
  }

  @Test
  fun aDraftDeletedThroughTheRepositoryIsWrittenAgainInFull() = runTest {
    whenever(dao.touchGroupSessionDraft(any(), any())).thenReturn(1)

    repository.upsertDraft(draft())
    repository.deleteDraft("q")
    repository.upsertDraft(draft())

    verify(dao).deleteGroupSessionDraft("q")
    verify(dao, times(2)).upsertGroupSessionDraft(any())
    verify(dao, never()).touchGroupSessionDraft(any(), any())
  }

  @Test
  fun theWrittenDraftCarriesTheTimeOfTheSave() = runTest {
    val before = System.currentTimeMillis()

    repository.upsertDraft(draft())

    val written = argumentCaptor<GroupSessionDraft>()
    verify(dao).upsertGroupSessionDraft(written.capture())
    assertTrue(written.firstValue.lastUpdated >= before)
    assertEquals("{form}", written.firstValue.encounterQuestionnaireJson)
  }
}
