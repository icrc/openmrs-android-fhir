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
import kotlin.test.assertTrue
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

/** The patient list shows a page at a time and "Show more" brings the next one. */
/** The size of a page of the list (`PatientListViewModel.PAGE_SIZE`). */
private const val PAGE_SIZE = 100

@OptIn(ExperimentalCoroutinesApi::class)
class PatientListViewModelPagingTest {
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

  /** A device with [total] patients: a search returns as many as it is asked for. */
  private fun viewModelWith(total: Int): PatientListViewModel {
    val fhirEngine =
      mock<FhirEngine> {
        onBlocking { search<Patient>(any<Search>()) } doSuspendableAnswer
          { invocation ->
            val search = invocation.getArgument<Search>(0)
            if (search.type == ResourceType.Patient) {
              (1..total).take(search.count ?: total).map { number ->
                SearchResult(
                  Patient().apply {
                    id = "p$number"
                    addName().setFamily("Family$number").addGiven("Given$number")
                  },
                  null,
                  null,
                )
              }
            } else {
              emptyList<SearchResult<Patient>>()
            }
          }
        onBlocking { count(any<Search>()) } doReturn total.toLong()
        onBlocking { getLocalChanges(any<ResourceType>(), any()) } doReturn emptyList()
      }
    return PatientListViewModel(fhirEngine, mock()).also { it.backgroundDispatcher = dispatcher }
  }

  @Test
  fun aShortListHasNothingMore() = runTest {
    val viewModel = viewModelWith(total = 30)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(30, viewModel.uiState.value.patients.size)
    assertFalse(viewModel.uiState.value.hasMore)
  }

  @Test
  fun exactlyOnePageHasNothingMore() = runTest {
    val viewModel = viewModelWith(total = PAGE_SIZE)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(PAGE_SIZE, viewModel.uiState.value.patients.size)
    assertFalse(viewModel.uiState.value.hasMore)
  }

  @Test
  fun showMoreBringsTheNextPageUntilTheEnd() = runTest {
    val page = PAGE_SIZE
    val viewModel = viewModelWith(total = page * 2 + 50)
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(page, viewModel.uiState.value.patients.size)
    assertTrue(viewModel.uiState.value.hasMore)

    viewModel.showMore()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(page * 2, viewModel.uiState.value.patients.size)
    assertTrue(viewModel.uiState.value.hasMore)

    viewModel.showMore()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(page * 2 + 50, viewModel.uiState.value.patients.size)
    assertFalse(viewModel.uiState.value.hasMore)
  }

  @Test
  fun aNewSearchOrSortStartsAgainFromTheFirstPage() = runTest {
    val page = PAGE_SIZE
    val viewModel = viewModelWith(total = page * 3)
    dispatcher.scheduler.advanceUntilIdle()
    viewModel.showMore()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(page * 2, viewModel.uiState.value.patients.size)

    viewModel.setSort(PatientSort.NAME_DESC)
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(page, viewModel.uiState.value.patients.size)
    assertEquals(PatientSort.NAME_DESC, viewModel.uiState.value.sort)

    viewModel.showMore()
    dispatcher.scheduler.advanceUntilIdle()
    viewModel.searchPatientsByName("Family")
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(page, viewModel.uiState.value.patients.size)
  }
}
