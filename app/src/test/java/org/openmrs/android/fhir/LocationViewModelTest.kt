package org.openmrs.android.fhir

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.SearchResult
import com.google.android.fhir.search.search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.hl7.fhir.r4.model.Location
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.openmrs.android.fhir.viewmodel.LocationViewModel

@RunWith(MockitoJUnitRunner::class)
class LocationViewModelTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()

    @Mock
    private lateinit var fhirEngine: FhirEngine

    @Mock
    private lateinit var application: Application

    private lateinit var locationViewModel: LocationViewModel

    @Mock
    private lateinit var observer: Observer<List<LocationViewModel.LocationItem>>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        locationViewModel = LocationViewModel(application, fhirEngine)
        locationViewModel.locations.observeForever(observer)
    }

    @ExperimentalCoroutinesApi
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetLocations() = runBlockingTest {
        val location = Location().apply {
            id = "1"
            name = "Location 1"
            status = Location.LocationStatus.ACTIVE
        }

        val searchResult = mutableListOf(SearchResult(location, mapOf(),mapOf()).apply {  })

        Mockito.`when`(fhirEngine.search<Location> {
            Mockito.any()
        }).thenReturn(searchResult)

        locationViewModel.getLocations()

        testDispatcher.scheduler.advanceUntilIdle()

        Mockito.verify(observer).onChanged(Mockito.anyList())
        assert(locationViewModel.locations.value?.size == 1)
        assert(locationViewModel.locations.value?.get(0)?.name == "Location 1")
    }


}
