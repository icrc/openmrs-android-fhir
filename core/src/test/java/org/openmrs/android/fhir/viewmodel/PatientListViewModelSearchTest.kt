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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.SearchResult
import com.google.android.fhir.search.Search
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock

/**
 * Regression tests for the patient search flow. They pin the behaviour that a slow, older search
 * must never overwrite the result of a newer one and that a failing search must not leave the
 * screen stuck in the loading state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientListViewModelSearchTest {
  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

  private val dispatcher: TestDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun patient(id: String, family: String) =
    SearchResult(
      Patient().apply {
        this.id = id
        addName().setFamily(family).addGiven("Test")
      },
      null,
      null,
    )

  @Test
  fun latestSearchWins_evenWhenAnOlderSearchFinishesLater() = runTest {
    // Call 1 = initial load from init, call 2 = "a", call 3 = "ab".
    val gates = List(3) { CompletableDeferred<List<SearchResult<Patient>>>() }
    var call = 0
    val fhirEngine =
      mock<FhirEngine> {
        // `search<Patient>` and `search<RiskAssessment>` erase to the same method, so a single
        // stub has to tell them apart by the resource type of the Search.
        onBlocking { search<Patient>(any<Search>()) } doSuspendableAnswer
          { invocation ->
            if (invocation.getArgument<Search>(0).type == ResourceType.Patient) {
              gates[call++].await()
            } else {
              emptyList<SearchResult<Patient>>()
            }
          }
        onBlocking { count(any<Search>()) } doReturn 0L
        onBlocking { getLocalChanges(any<ResourceType>(), any()) } doReturn emptyList()
      }
    val viewModel = PatientListViewModel(fhirEngine, mock())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.searchPatientsByName("a")
    dispatcher.scheduler.advanceUntilIdle()
    viewModel.searchPatientsByName("ab")
    dispatcher.scheduler.advanceUntilIdle()

    // The newest query answers first, then the stale ones arrive late.
    gates[2].complete(listOf(patient("new", "Newest")))
    dispatcher.scheduler.advanceUntilIdle()
    gates[1].complete(listOf(patient("old", "Stale")))
    gates[0].complete(listOf(patient("older", "Staler")))
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("new"), viewModel.uiState.value.patients.map { it.resourceId })
  }

  @Test
  fun failingSearch_doesNotLeaveScreenLoading() = runTest {
    val fhirEngine =
      mock<FhirEngine> {
        onBlocking { search<Patient>(any<Search>()) } doSuspendableAnswer
          { invocation ->
            if (invocation.getArgument<Search>(0).type == ResourceType.Patient) {
              throw IllegalStateException("database unavailable")
            }
            emptyList<SearchResult<Patient>>()
          }
        onBlocking { count(any<Search>()) } doReturn 0L
        onBlocking { getLocalChanges(any<ResourceType>(), any()) } doReturn emptyList()
      }

    val viewModel = PatientListViewModel(fhirEngine, mock())
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.isRefreshing)
    assertEquals(emptyList(), viewModel.uiState.value.patients)
  }
}
