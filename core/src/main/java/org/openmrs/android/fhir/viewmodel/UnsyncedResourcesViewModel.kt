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
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.search.search
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableList
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.data.database.model.UnsyncedEncounter
import org.openmrs.android.fhir.data.database.model.UnsyncedObservation
import org.openmrs.android.fhir.data.database.model.UnsyncedPatient
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.data.database.model.UnsyncedResourceModel
import org.openmrs.android.fhir.di.IoDispatcher

class UnsyncedResourcesViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _resources = MutableLiveData<List<UnsyncedResource>>(emptyList())
  val resources: LiveData<List<UnsyncedResource>> = _resources

  private val _downloadResource = MutableLiveData<String>()
  val downloadResource: LiveData<String> = _downloadResource

  private val _isLoading = MutableLiveData<Boolean>(false)
  val isLoading: LiveData<Boolean> = _isLoading

  private var patients: List<UnsyncedPatient> = emptyList()

  private val jsonParser = FhirContext.forR4Cached().newJsonParser()

  init {
    loadUnsyncedResources()
  }

  fun loadUnsyncedResources() {
    _isLoading.value = true
    viewModelScope.launch {
      try {
        val latestPatients =
          withContext(ioDispatcher) { fetchUnsyncedPatientsWithEncounterAndObservations() }
        patients = latestPatients
        updateResourcesList()
      } catch (_: Exception) {
        // Keep previously loaded resources visible on failure.
      } finally {
        _isLoading.value = false
      }
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

    _resources.value = flattenedList.toImmutableList()
  }

  fun togglePatientExpansion(patientId: String) {
    val patient = patients.find { it.logicalId == patientId } ?: return
    patient.isExpanded = !patient.isExpanded
    updateResourcesList()
  }

  fun toggleEncounterExpansion(encounterId: String) {
    patients.forEach { patient ->
      val encounter = patient.encounters.find { it.logicalId == encounterId }
      if (encounter != null) {
        encounter.isExpanded = !encounter.isExpanded
        updateResourcesList()
        return
      }
    }
  }

  /*
   * Code to delete a resource.
   * NOTE: Deletes only unsynced resources
   * Handles UI after delete
   * Purges the resource from the FhirEngine.
   */
  fun deleteResource(resource: UnsyncedResource) {
    _isLoading.value = true
    viewModelScope.launch {
      try {
        val resourceIdMap =
          when (resource) {
            is UnsyncedResource.PatientItem ->
              getUnsyncedResourceCurrentAndChildrenResourceId(
                resource.patient,
                onlyUnSyncedFlag = true,
              )
            is UnsyncedResource.EncounterItem ->
              getUnsyncedResourceCurrentAndChildrenResourceId(
                resource.encounter,
                onlyUnSyncedFlag = true,
              )
            is UnsyncedResource.ObservationItem ->
              getUnsyncedResourceCurrentAndChildrenResourceId(
                resource.observation,
                onlyUnSyncedFlag = true,
              )
          }

        // Purge all related resources
        resourceIdMap.forEach { (resourceType, resourceIds) ->
          fhirEngine.purge(resourceType, resourceIds, forcePurge = true)
        }

        // Handle UI
        when (resource) {
          is UnsyncedResource.PatientItem -> {
            patients = patients.filter { it.logicalId != resource.patient.logicalId }
          }
          is UnsyncedResource.EncounterItem -> {
            val patientId = resource.encounter.patientId
            patients =
              patients.map { patient ->
                if (patient.logicalId == patientId) {
                  patient.copy(
                    encounters =
                      patient.encounters.filter { it.logicalId != resource.encounter.logicalId },
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
                if (patient.logicalId == patientId) {
                  val updatedEncounters =
                    patient.encounters.map { encounter ->
                      if (encounter.logicalId == encounterId) {
                        encounter.copy(
                          observations =
                            encounter.observations.filter {
                              it.logicalId != resource.observation.logicalId
                            },
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
      } finally {
        _isLoading.value = false
      }
    }
  }

  /*
   * Code to download a resource.
   * NOTE: Downloads only unsynced resources
   * Downloads the resource from the FhirEngine.
   */
  fun downloadResource(unsyncedResource: UnsyncedResource) {
    _isLoading.value = true

    val bundle =
      Bundle().apply {
        type = Bundle.BundleType.COLLECTION
        entry = mutableListOf()
      }

    viewModelScope.launch {
      try {
        val resourceIdMap =
          when (unsyncedResource) {
            is UnsyncedResource.PatientItem ->
              getUnsyncedResourceCurrentAndChildrenResourceId(
                unsyncedResource.patient,
                onlyUnSyncedFlag = true,
              )
            is UnsyncedResource.EncounterItem ->
              getUnsyncedResourceCurrentAndChildrenResourceId(
                unsyncedResource.encounter,
                onlyUnSyncedFlag = true,
              )
            is UnsyncedResource.ObservationItem ->
              getUnsyncedResourceCurrentAndChildrenResourceId(
                unsyncedResource.observation,
                onlyUnSyncedFlag = true,
              )
          }

        // Bundle all related resources
        resourceIdMap.forEach { (resourceType, resourceIds) ->
          resourceIds.forEach {
            bundle.addEntry(
              Bundle.BundleEntryComponent().apply { resource = fhirEngine.get(resourceType, it) },
            )
          }
        }

        _downloadResource.value = jsonParser.encodeResourceToString(bundle)
      } finally {
        _isLoading.value = false
      }
    }
  }

  /*
   * Code to download all resources.
   * NOTE: Downloads only unsynced resources
   * Downloads all unsynced resources from the FhirEngine.
   */
  fun downloadAll() {
    _isLoading.value = true

    val bundle =
      Bundle().apply {
        type = Bundle.BundleType.COLLECTION
        entry = mutableListOf()
      }

    viewModelScope.launch {
      try {
        patients.forEach { patient ->
          val resourceIdMap =
            getUnsyncedResourceCurrentAndChildrenResourceId(patient, onlyUnSyncedFlag = true)
          resourceIdMap.forEach { (resourceType, resourceIds) ->
            resourceIds.forEach {
              bundle.addEntry(
                Bundle.BundleEntryComponent().apply { resource = fhirEngine.get(resourceType, it) },
              )
            }
          }
        }

        _downloadResource.value = jsonParser.encodeResourceToString(bundle)
      } finally {
        _isLoading.value = false
      }
    }
  }

  /*
   * Code to delete all resources.
   * NOTE: Deletes only unsynced resources
   * Purges all unsynced resources from the FhirEngine.
   */
  fun deleteAll() {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        patients.forEach { patient ->
          val resourceIdMap =
            getUnsyncedResourceCurrentAndChildrenResourceId(patient, onlyUnSyncedFlag = true)
          resourceIdMap.forEach { (resourceType, resourceIds) ->
            fhirEngine.purge(resourceType, resourceIds, forcePurge = true)
          }
        }
        patients = emptyList()
      } catch (_: Exception) {
        patients = fetchUnsyncedPatientsWithEncounterAndObservations()
      } finally {
        updateResourcesList()
        _isLoading.value = false
      }
    }
  }

  /*
   * Code to fetch Unsynced resources.
   * Note: This doesn't handle case where observation has no encounter or encounter has no patient in the FhirEngine.
   * Since It will not be possible to create the resource for that case.
   */

  private suspend fun fetchUnsyncedPatientsWithEncounterAndObservations(): List<UnsyncedPatient> {
    val unsyncedPatients = mutableListOf<UnsyncedPatient>()
    val encounterToObservationMap = mutableMapOf<String, MutableList<UnsyncedObservation>>()
    val patientToEncounterMap = mutableMapOf<String, MutableList<UnsyncedEncounter>>()
    val localChangesCache = mutableMapOf<ResourceType, MutableMap<String, Boolean>>()

    // Get all observations and check for local changes, group by encounter and add to map
    val observations = fhirEngine.search<Observation> {}.map { it.resource }
    for (observation in observations) {
      if (hasLocalChanges(ResourceType.Observation, observation.logicalId, localChangesCache)) {
        encounterToObservationMap
          .getOrPut(observation.getEncounterId()) { mutableListOf() }
          .add(observation.toObservationItem())
      }
    }

    // Get all encounters and check for local changes
    val encounters = fhirEngine.search<Encounter> {}.map { it.resource }
    for (encounter in encounters) {
      val hasChanges =
        hasLocalChanges(ResourceType.Encounter, encounter.logicalId, localChangesCache)
      if (hasChanges) {
        patientToEncounterMap
          .getOrPut(encounter.getPatientId()) { mutableListOf() }
          .add(
            encounter.toEncounterItem(
              isSynced = false,
              encounterToObservationMap[encounter.logicalId] ?: emptyList(),
            ),
          )
        encounterToObservationMap.remove(encounter.logicalId)
      } else if (encounterToObservationMap.containsKey(encounter.logicalId)) {
        patientToEncounterMap
          .getOrPut(encounter.getPatientId()) { mutableListOf() }
          .add(
            encounter.toEncounterItem(
              isSynced = true,
              encounterToObservationMap[encounter.logicalId] ?: emptyList(),
            ),
          )
        encounterToObservationMap.remove(encounter.logicalId)
      }
    }

    // Get all patients and check for local changes
    val patients = fhirEngine.search<Patient> {}.map { it.resource }
    for (patient in patients) {
      val hasChanges = hasLocalChanges(ResourceType.Patient, patient.logicalId, localChangesCache)
      if (hasChanges) {
        unsyncedPatients.add(
          patient.toPatientItem(
            isSynced = false,
            patientToEncounterMap[patient.logicalId] ?: emptyList(),
          ),
        )
        patientToEncounterMap.remove(patient.logicalId)
      } else if (patientToEncounterMap.containsKey(patient.logicalId)) {
        unsyncedPatients.add(
          patient.toPatientItem(
            isSynced = true,
            patientToEncounterMap[patient.logicalId] ?: emptyList(),
          ),
        )
        patientToEncounterMap.remove(patient.logicalId)
      }
    }

    return unsyncedPatients
  }

  private suspend fun hasLocalChanges(
    resourceType: ResourceType,
    resourceId: String,
    cache: MutableMap<ResourceType, MutableMap<String, Boolean>>,
  ): Boolean {
    val resourceCache = cache.getOrPut(resourceType) { mutableMapOf() }
    return resourceCache.getOrPut(resourceId) {
      fhirEngine.getLocalChanges(resourceType, resourceId).isNotEmpty()
    }
  }

  internal fun Observation.getEncounterId(): String {
    return encounter?.reference?.substringAfterLast("/") ?: ""
  }

  internal fun Observation.toObservationItem(isSynced: Boolean = false): UnsyncedObservation {
    return UnsyncedObservation(
      logicalId = logicalId,
      title = code.coding.firstOrNull()?.display ?: "Observation: ${logicalId.takeLast(5)}",
      encounterId = getEncounterId(),
      patientId = subject.reference.substringAfterLast("/"),
      isSynced = isSynced,
    )
  }

  internal fun Encounter.getPatientId(): String {
    return subject.reference.substringAfterLast("/")
  }

  internal fun Encounter.toEncounterItem(
    isSynced: Boolean = false,
    unsyncedObservations: List<UnsyncedObservation>,
  ): UnsyncedEncounter {
    return UnsyncedEncounter(
      logicalId = logicalId,
      title = type.firstOrNull()?.coding?.firstOrNull()?.display
          ?: "Encounter: ${logicalId.takeLast(5)}",
      patientId = getPatientId(),
      observations = unsyncedObservations,
      isSynced = isSynced,
    )
  }

  internal fun Patient.toPatientItem(
    isSynced: Boolean = false,
    unsyncedEncounters: List<UnsyncedEncounter>,
  ): UnsyncedPatient {
    return UnsyncedPatient(
      logicalId = logicalId,
      name = name.firstOrNull()?.nameAsSingleString ?: "Patient: ${logicalId.takeLast(5)}",
      isExpanded = false,
      isSynced = isSynced,
      encounters = unsyncedEncounters,
    )
  }

  internal fun getUnsyncedResourceCurrentAndChildrenResourceId(
    unsyncedResource: UnsyncedResourceModel,
    onlyUnSyncedFlag: Boolean = false,
  ): Map<ResourceType, Set<String>> {
    val resourceIds = mutableMapOf<ResourceType, MutableSet<String>>()

    when (unsyncedResource) {
      is UnsyncedPatient -> {
        if (!onlyUnSyncedFlag || !unsyncedResource.isSynced) {
          resourceIds
            .getOrPut(ResourceType.Patient) { mutableSetOf<String>() }
            .add(unsyncedResource.logicalId)
        }
        unsyncedResource.encounters.forEach { encounter ->
          if (!onlyUnSyncedFlag || !encounter.isSynced) {
            resourceIds
              .getOrPut(ResourceType.Encounter) { mutableSetOf<String>() }
              .add(encounter.logicalId)
          }
          encounter.observations
            .filter { !onlyUnSyncedFlag || !it.isSynced }
            .forEach { observation ->
              resourceIds
                .getOrPut(ResourceType.Observation) { mutableSetOf<String>() }
                .add(observation.logicalId)
            }
        }
      }
      is UnsyncedEncounter -> {
        if (!onlyUnSyncedFlag || !unsyncedResource.isSynced) {
          resourceIds
            .getOrPut(ResourceType.Encounter) { mutableSetOf<String>() }
            .add(unsyncedResource.logicalId)
        }
        unsyncedResource.observations
          .filter { !onlyUnSyncedFlag || !it.isSynced }
          .forEach { observation ->
            resourceIds
              .getOrPut(ResourceType.Observation) { mutableSetOf<String>() }
              .add(observation.logicalId)
          }
      }
      is UnsyncedObservation -> {
        if (!onlyUnSyncedFlag || !unsyncedResource.isSynced) {
          resourceIds
            .getOrPut(ResourceType.Observation) { mutableSetOf<String>() }
            .add(unsyncedResource.logicalId)
        }
      }
    }
    return resourceIds
  }
}
