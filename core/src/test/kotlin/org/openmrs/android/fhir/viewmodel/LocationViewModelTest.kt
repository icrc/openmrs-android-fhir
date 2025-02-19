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
import androidx.lifecycle.Observer
import com.google.android.fhir.FhirEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.openmrs.android.fhir.data.remote.ApiManager

@RunWith(MockitoJUnitRunner::class)
class LocationViewModelTest {

  @get:Rule var rule: TestRule = InstantTaskExecutorRule()

  private val testDispatcher = TestCoroutineDispatcher()

  @Mock private lateinit var fhirEngine: FhirEngine

  @Mock private lateinit var apiManager: ApiManager

  private lateinit var locationViewModel: LocationViewModel

  @Mock private lateinit var observer: Observer<List<LocationViewModel.LocationItem>>

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)
    Dispatchers.setMain(testDispatcher)
    //    locationViewModel = LocationViewModel(applicationContext, fhirEngine, apiManager)
    locationViewModel.locations.observeForever(observer)
  }

  @ExperimentalCoroutinesApi
  @After
  fun tearDown() {
    Dispatchers.resetMain()
    testDispatcher.cleanupTestCoroutines()
  }

  // TODO: update the test
  //  @OptIn(ExperimentalCoroutinesApi::class)
  //  @Test
  //  fun testGetLocations() = runBlockingTest {
  //    val location =
  //      Location().apply {
  //        id = "1"
  //        name = "Location 1"
  //        status = Location.LocationStatus.ACTIVE
  //      }
  //
  //    val searchResult = mutableListOf(SearchResult(location, mapOf(), mapOf()).apply {})
  //
  //    Mockito.`when`(
  //        fhirEngine.search<Location> { Mockito.any() },
  //      )
  //      .thenReturn(searchResult)
  //
  //    locationViewModel.getLocations(online = false)
  //
  //    testDispatcher.scheduler.advanceUntilIdle()
  //
  //    Mockito.verify(observer).onChanged(Mockito.anyList())
  //    assert(locationViewModel.locations.value?.size == 1)
  //    assert(locationViewModel.locations.value?.get(0)?.name == "Location 1")
  //  }
}
