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
import com.google.android.fhir.LocalChange
import com.google.android.fhir.SearchResult
import com.google.android.fhir.search.Search
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.openmrs.android.fhir.data.database.model.UnsyncedEncounter
import org.openmrs.android.fhir.data.database.model.UnsyncedObservation
import org.openmrs.android.fhir.data.database.model.UnsyncedPatient
import org.openmrs.android.fhir.data.database.model.UnsyncedResource

@OptIn(ExperimentalCoroutinesApi::class)
class UnsyncedResourcesViewModelTest {

  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadUnsyncedResources_withNoChanges_keepsResourcesEmpty() =
    runTest(dispatcher) {
      val fhirEngine =
        mock<FhirEngine> {
          onBlocking { search<org.hl7.fhir.r4.model.Observation>(any<Search>()) } doReturn
            emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Encounter>(any<Search>()) } doReturn emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Patient>(any<Search>()) } doReturn emptyList()
          onBlocking { getLocalChanges(any<ResourceType>(), any()) } doReturn emptyList()
        }

      val viewModel = UnsyncedResourcesViewModel(fhirEngine, ioDispatcher = dispatcher)
      viewModel.loadUnsyncedResources()
      advanceUntilIdle()

      assertEquals(emptyList(), viewModel.resources.value)
      assertEquals(false, viewModel.isLoading.value)
    }

  @Test
  fun getUnsyncedResourceCurrentAndChildrenResourceId_filtersSyncedResources() =
    runTest(dispatcher) {
      val fhirEngine =
        mock<FhirEngine> {
          onBlocking { search<org.hl7.fhir.r4.model.Observation>(any<Search>()) } doReturn
            emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Encounter>(any<Search>()) } doReturn emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Patient>(any<Search>()) } doReturn emptyList()
          onBlocking { getLocalChanges(any<ResourceType>(), any()) } doReturn emptyList()
        }
      val viewModel = UnsyncedResourcesViewModel(fhirEngine, ioDispatcher = dispatcher)

      val observation =
        UnsyncedObservation(
          logicalId = "observation-1",
          title = "Observation",
          encounterId = "encounter-1",
          patientId = "patient-1",
          isSynced = false,
        )
      val encounter =
        UnsyncedEncounter(
          logicalId = "encounter-1",
          title = "Encounter",
          patientId = "patient-1",
          observations = listOf(observation.copy(isSynced = false)),
          isSynced = false,
        )
      val patient =
        UnsyncedPatient(
          logicalId = "patient-1",
          name = "Patient",
          encounters = listOf(encounter),
          isExpanded = false,
          isSynced = true,
        )

      val onlyUnsynced =
        viewModel.getUnsyncedResourceCurrentAndChildrenResourceId(
          unsyncedResource = patient,
          onlyUnSyncedFlag = true,
        )

      assertEquals(setOf("observation-1"), onlyUnsynced[ResourceType.Observation])
      assertEquals(null, onlyUnsynced[ResourceType.Patient])
      assertEquals(setOf("encounter-1"), onlyUnsynced[ResourceType.Encounter])
    }

  @Test
  fun getUnsyncedResourceCurrentAndChildrenResourceId_includesUnsyncedObservationForSyncedEncounter() =
    runTest(dispatcher) {
      val fhirEngine =
        mock<FhirEngine> {
          onBlocking { search<org.hl7.fhir.r4.model.Observation>(any<Search>()) } doReturn
            emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Encounter>(any<Search>()) } doReturn emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Patient>(any<Search>()) } doReturn emptyList()
          onBlocking { getLocalChanges(any<ResourceType>(), any()) } doReturn emptyList()
        }
      val viewModel = UnsyncedResourcesViewModel(fhirEngine, ioDispatcher = dispatcher)

      val observation =
        UnsyncedObservation(
          logicalId = "observation-1",
          title = "Observation",
          encounterId = "encounter-1",
          patientId = "patient-1",
          isSynced = false,
        )
      val encounter =
        UnsyncedEncounter(
          logicalId = "encounter-1",
          title = "Encounter",
          patientId = "patient-1",
          observations = listOf(observation),
          isSynced = true,
        )
      val patient =
        UnsyncedPatient(
          logicalId = "patient-1",
          name = "Patient",
          encounters = listOf(encounter),
          isExpanded = false,
          isSynced = true,
        )

      val resourceIds =
        viewModel.getUnsyncedResourceCurrentAndChildrenResourceId(
          unsyncedResource = patient,
          onlyUnSyncedFlag = true,
        )

      assertEquals(setOf("observation-1"), resourceIds[ResourceType.Observation])
      assertEquals(null, resourceIds[ResourceType.Patient])
      assertEquals(null, resourceIds[ResourceType.Encounter])
    }

  @Test
  fun getUnsyncedResourceCurrentAndChildrenResourceId_includesUnsyncedHierarchy() =
    runTest(dispatcher) {
      val fhirEngine =
        mock<FhirEngine> {
          onBlocking { search<org.hl7.fhir.r4.model.Observation>(any<Search>()) } doReturn
            emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Encounter>(any<Search>()) } doReturn emptyList()
          onBlocking { search<org.hl7.fhir.r4.model.Patient>(any<Search>()) } doReturn emptyList()
          onBlocking { getLocalChanges(any<ResourceType>(), any()) } doReturn emptyList()
        }
      val viewModel = UnsyncedResourcesViewModel(fhirEngine, ioDispatcher = dispatcher)

      val observation =
        UnsyncedObservation(
          logicalId = "observation-1",
          title = "Observation",
          encounterId = "encounter-1",
          patientId = "patient-1",
          isSynced = false,
        )
      val encounter =
        UnsyncedEncounter(
          logicalId = "encounter-1",
          title = "Encounter",
          patientId = "patient-1",
          observations = listOf(observation),
          isSynced = false,
        )
      val patient =
        UnsyncedPatient(
          logicalId = "patient-1",
          name = "Patient",
          encounters = listOf(encounter),
          isExpanded = false,
          isSynced = false,
        )

      val resourceIds =
        viewModel.getUnsyncedResourceCurrentAndChildrenResourceId(
          unsyncedResource = patient,
          onlyUnSyncedFlag = true,
        )

      assertEquals(setOf("patient-1"), resourceIds[ResourceType.Patient])
      assertEquals(setOf("encounter-1"), resourceIds[ResourceType.Encounter])
      assertEquals(setOf("observation-1"), resourceIds[ResourceType.Observation])
    }

  /**
   * Characterization of the full scan: which patients show up, how encounters and observations are
   * grouped under them, and which rows are flagged as unsynced.
   */
  @Test
  @Suppress("UNCHECKED_CAST")
  fun loadUnsyncedResources_groupsLocalChangesUnderTheirPatientAndEncounter() =
    runTest(dispatcher) {
      fun patient(id: String) = Patient().apply { this.id = id }

      fun encounter(id: String, patientId: String) =
        Encounter().apply {
          this.id = id
          subject = Reference("Patient/$patientId")
        }

      fun observation(id: String, encounterId: String, patientId: String) =
        Observation().apply {
          this.id = id
          subject = Reference("Patient/$patientId")
          encounter = Reference("Encounter/$encounterId")
        }

      val patients = listOf(patient("p1"), patient("p2"), patient("p3"))
      val encounters = listOf(encounter("e1", "p1"), encounter("e2", "p2"), encounter("e3", "p3"))
      val observations =
        listOf(
          observation("o1", "e1", "p1"),
          observation("o2", "e2", "p2"),
          observation("o3", "e3", "p3"),
        )
      // p1 + e1 + o1 were created locally; o2 was edited on an already synced encounter/patient.
      val withLocalChanges = setOf("p1", "e1", "o1", "o2")

      val fhirEngine =
        mock<FhirEngine> {
          onBlocking { search<Patient>(any<Search>()) } doSuspendableAnswer
            { invocation ->
              val type = invocation.getArgument<Search>(0).type
              when (type) {
                ResourceType.Patient -> patients
                ResourceType.Encounter -> encounters
                ResourceType.Observation -> observations
                else -> emptyList<Resource>()
              }.map { SearchResult(it, null, null) } as List<SearchResult<Patient>>
            }
          onBlocking { getLocalChanges(any<ResourceType>(), any()) } doSuspendableAnswer
            { invocation ->
              if (invocation.getArgument<String>(1) in withLocalChanges) {
                listOf(mock<LocalChange>())
              } else {
                emptyList()
              }
            }
        }

      val viewModel = UnsyncedResourcesViewModel(fhirEngine, ioDispatcher = dispatcher)
      advanceUntilIdle()

      val items =
        viewModel.resources.value.orEmpty().map { (it as UnsyncedResource.PatientItem).patient }

      // p3 has no local change anywhere in its tree and must not be listed.
      assertEquals(listOf("p1", "p2"), items.map { it.logicalId })
      assertEquals(listOf(false, true), items.map { it.isSynced })

      val p1 = items[0]
      assertEquals(listOf("e1"), p1.encounters.map { it.logicalId })
      assertEquals(listOf(false), p1.encounters.map { it.isSynced })
      assertEquals(listOf("o1"), p1.encounters[0].observations.map { it.logicalId })

      val p2 = items[1]
      assertEquals(listOf("e2"), p2.encounters.map { it.logicalId })
      assertEquals(listOf(true), p2.encounters.map { it.isSynced })
      assertEquals(listOf("o2"), p2.encounters[0].observations.map { it.logicalId })
    }
}
