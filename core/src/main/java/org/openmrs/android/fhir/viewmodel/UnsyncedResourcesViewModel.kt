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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.search.search
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.data.database.model.UnsyncedEncounter
import org.openmrs.android.fhir.data.database.model.UnsyncedObservation
import org.openmrs.android.fhir.data.database.model.UnsyncedPatient
import org.openmrs.android.fhir.data.database.model.UnsyncedResource

class UnsyncedResourcesViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
) : ViewModel() {

  private val _resources = MutableLiveData<List<UnsyncedResource>>()
  val resources: LiveData<List<UnsyncedResource>> = _resources

  private val _isLoading = MutableLiveData<Boolean>()
  val isLoading: LiveData<Boolean> = _isLoading

  private var patients: List<UnsyncedPatient> = emptyList()

  private var unsyncedResourcedIdSet: MutableSet<String> = mutableSetOf()

  init {
    loadUnsyncedResources()
  }

  fun loadUnsyncedResources() {
    _isLoading.value = true
    viewModelScope.launch {
      // Fetch unsynced resources
      //            val unsyncedResourcesMap = findUnsyncedResources()
      //            patients = getPatientsFromUnsyncedResourcesMap(unsyncedResourcesMap)
      patients = fetchMockData()
      updateResourcesList()
      _isLoading.value = false
    }
  }

  private fun updateResourcesList() {
    val flattenedList = mutableListOf<UnsyncedResource>()

    patients.forEach { patient ->
      flattenedList.add(UnsyncedResource.PatientItem(patient))

      if (patient.isExpanded) {
        // Add encounters
        patient.encounters.forEach { encounter ->
          flattenedList.add(UnsyncedResource.EncounterItem(encounter))

          // Add observations if encounter is expanded
          if (encounter.isExpanded) {
            encounter.observations.forEach { observation ->
              flattenedList.add(UnsyncedResource.ObservationItem(observation))
            }
          }
        }
      }
    }

    _resources.value = flattenedList
  }

  fun togglePatientExpansion(patientId: String) {
    val patient = patients.find { it.id == patientId } ?: return
    patient.isExpanded = !patient.isExpanded
    updateResourcesList()
  }

  fun toggleEncounterExpansion(encounterId: String) {
    patients.forEach { patient ->
      val encounter = patient.encounters.find { it.id == encounterId }
      if (encounter != null) {
        encounter.isExpanded = !encounter.isExpanded
        updateResourcesList()
        return
      }
    }
  }

  fun deleteResource(resource: UnsyncedResource) {
    viewModelScope.launch {
      when (resource) {
        is UnsyncedResource.PatientItem -> {
          // Delete patient and all related resources
          patients = patients.filter { it.id != resource.patient.id }
        }
        is UnsyncedResource.EncounterItem -> {
          // Find patient and remove encounter
          val patientId = resource.encounter.patientId
          patients =
            patients.map { patient ->
              if (patient.id == patientId) {
                patient.copy(
                  encounters = patient.encounters.filter { it.id != resource.encounter.id },
                )
              } else {
                patient
              }
            }
        }
        is UnsyncedResource.ObservationItem -> {
          // Find patient and encounter, then remove observation
          val patientId = resource.observation.patientId
          val encounterId = resource.observation.encounterId

          patients =
            patients.map { patient ->
              if (patient.id == patientId) {
                val updatedEncounters =
                  patient.encounters.map { encounter ->
                    if (encounter.id == encounterId) {
                      encounter.copy(
                        observations =
                          encounter.observations.filter { it.id != resource.observation.id },
                      )
                    } else {
                      encounter
                    }
                  }
                patient.copy(encounters = updatedEncounters)
              } else {
                patient
              }
            }
        }
      }
      updateResourcesList()
    }
  }

  // TODO:
  fun downloadResource(resource: UnsyncedResource) {
    viewModelScope.launch {
      when (resource) {
        is UnsyncedResource.PatientItem -> {
          patients = patients.filter { it.id != resource.patient.id }
        }
        is UnsyncedResource.EncounterItem -> {
          val patientId = resource.encounter.patientId
          patients =
            patients.map { patient ->
              if (patient.id == patientId) {
                patient.copy(
                  encounters = patient.encounters.filter { it.id != resource.encounter.id },
                )
              } else {
                patient
              }
            }
        }
        is UnsyncedResource.ObservationItem -> {
          val patientId = resource.observation.patientId
          val encounterId = resource.observation.encounterId

          patients =
            patients.map { patient ->
              if (patient.id == patientId) {
                val updatedEncounters =
                  patient.encounters.map { encounter ->
                    if (encounter.id == encounterId) {
                      encounter.copy(
                        observations =
                          encounter.observations.filter { it.id != resource.observation.id },
                      )
                    } else {
                      encounter
                    }
                  }
                patient.copy(encounters = updatedEncounters)
              } else {
                patient
              }
            }
        }
      }
      updateResourcesList()
    }
  }

  // TODO
  fun downloadAll() {
    viewModelScope.launch {
      // Implement actual download all logic here
      // For now, just clear the list
      patients = emptyList()
      updateResourcesList()
    }
  }

  // TODO
  fun deleteAll() {
    viewModelScope.launch {
      // Implement actual delete all logic here
      patients = emptyList()
      updateResourcesList()
    }
  }

  /*
   * Code to fetch Unsynced resources.
   * @TODO: Refactor
   */

  private suspend fun findUnsyncedResources(): Map<ResourceType, List<Resource>> {
    val unsyncedResources = mutableMapOf<ResourceType, MutableList<Resource>>()

    // Initialize empty lists for each resource type
    unsyncedResources[ResourceType.Patient] = mutableListOf()
    unsyncedResources[ResourceType.Encounter] = mutableListOf()
    unsyncedResources[ResourceType.Observation] = mutableListOf()

    // Get all patients and check for local changes
    val patients = fhirEngine.search<Patient> {}.map { it.resource }
    for (patient in patients) {
      val changes = fhirEngine.getLocalChanges(ResourceType.Patient, patient.logicalId)
      if (changes.isNotEmpty()) {
        unsyncedResourcedIdSet.add(patient.logicalId)
        unsyncedResources[ResourceType.Patient]?.add(patient)
      }
    }

    // Get all encounters and check for local changes
    val encounters = fhirEngine.search<Encounter> {}.map { it.resource }
    for (encounter in encounters) {
      val changes = fhirEngine.getLocalChanges(ResourceType.Encounter, encounter.id)
      if (changes.isNotEmpty()) {
        unsyncedResourcedIdSet.add(encounter.id)
        unsyncedResources[ResourceType.Encounter]?.add(encounter)
      }
    }

    // Get all observations and check for local changes
    val observations = fhirEngine.search<Observation> {}.map { it.resource }
    for (observation in observations) {
      unsyncedResourcedIdSet.add(observation.id)
      val changes = fhirEngine.getLocalChanges(ResourceType.Observation, observation.id)
      if (changes.isNotEmpty()) {
        unsyncedResources[ResourceType.Observation]?.add(observation)
      }
    }

    return unsyncedResources
  }

  private fun getPatientsFromUnsyncedResourcesMap(
    unsyncedResourceMap: Map<ResourceType, List<Resource>>,
  ): List<UnsyncedPatient> {
    val unsyncedObservations =
      unsyncedResourceMap[ResourceType.Observation]?.map { (it as Observation).toObservationItem() }
        ?: emptyList()
    val unsyncedEncounters =
      unsyncedResourceMap[ResourceType.Encounter]?.map {
        (it as Encounter).toEncounterItem(unsyncedObservations)
      }
        ?: emptyList()
    val unsyncedPatients =
      unsyncedResourceMap[ResourceType.Patient]?.map {
        (it as Patient).toPatientItem(unsyncedEncounters)
      }
        ?: emptyList()
    return unsyncedPatients
  }

  internal fun Observation.toObservationItem(): UnsyncedObservation {
    return UnsyncedObservation(
      id = id,
      title = code.coding.firstOrNull()?.display ?: "Observation: ${id.takeLast(5)}",
      encounterId = encounter.reference.substringAfterLast("/"),
      patientId = subject.reference.substringAfterLast("/"),
      isSynced = false,
    )
  }

  internal fun Encounter.toEncounterItem(
    unsyncedObservations: List<UnsyncedObservation>,
  ): UnsyncedEncounter {
    return UnsyncedEncounter(
      id = id,
      title = type.firstOrNull()?.coding?.firstOrNull()?.display ?: "Encounter: ${id.takeLast(5)}",
      patientId = subject.reference.substringAfterLast("/"),
      observations = unsyncedObservations.filter { it.encounterId == id },
    )
  }

  internal fun Patient.toPatientItem(unsyncedEncounters: List<UnsyncedEncounter>): UnsyncedPatient {
    return UnsyncedPatient(
      id = id,
      name = name.firstOrNull()?.text ?: "Patient: ${id.takeLast(5)}",
      isExpanded = false,
      isSynced = false,
      encounters = unsyncedEncounters.filter { it.patientId == id },
    )
  }

  // Mock data for testing
  private fun fetchMockData(): List<UnsyncedPatient> {
    // Create observations
    val obs1 =
      UnsyncedObservation(
        id = "o1",
        title = "Blood Pressure",
        encounterId = "e1",
        patientId = "p1",
        isSynced = false,
      )

    val obs2 =
      UnsyncedObservation(
        id = "o2",
        title = "Temperature",
        encounterId = "e1",
        patientId = "p1",
        isSynced = true,
      )

    val obs3 =
      UnsyncedObservation(
        id = "o3",
        title = "Heart Rate",
        encounterId = "e2",
        patientId = "p1",
        isSynced = false,
      )

    val obs4 =
      UnsyncedObservation(
        id = "o4",
        title = "Respiratory Rate",
        encounterId = "e3",
        patientId = "p2",
        isSynced = false,
      )

    val obs5 =
      UnsyncedObservation(
        id = "o5",
        title = "Weight",
        encounterId = "e3",
        patientId = "p2",
        isSynced = true,
      )

    // Create encounters with observations
    val enc1 =
      UnsyncedEncounter(
        id = "e1",
        title = "Check-up Visit",
        patientId = "p1",
        observations = listOf(obs1, obs2),
        isExpanded = false,
        isSynced = false,
      )

    val enc2 =
      UnsyncedEncounter(
        id = "e2",
        title = "Emergency Visit",
        patientId = "p1",
        observations = listOf(obs3),
        isExpanded = false,
        isSynced = true,
      )

    val enc3 =
      UnsyncedEncounter(
        id = "e3",
        title = "Follow-up Visit",
        patientId = "p2",
        observations = listOf(obs4, obs5),
        isExpanded = false,
        isSynced = false,
      )

    // Create patients with encounters
    return listOf(
      UnsyncedPatient(
        id = "p1",
        name = "John Doe",
        encounters = listOf(enc1, enc2),
        isExpanded = false,
        isSynced = false,
      ),
      UnsyncedPatient(
        id = "p2",
        name = "Jane Smith",
        encounters = listOf(enc3),
        isExpanded = false,
        isSynced = true,
      ),
    )
  }
}
