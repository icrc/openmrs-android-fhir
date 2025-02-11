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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.FileNotFoundException
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.di.ViewModelAssistedFactory

class EditEncounterViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  @Assisted val state: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<EditEncounterViewModel> {
    override fun create(
      handle: SavedStateHandle,
    ): EditEncounterViewModel
  }

  private val encounterId: String =
    requireNotNull(state["encounter_id"])
      ?: throw IllegalArgumentException("Encounter ID is required")
  private val encounterType: String =
    requireNotNull(state["encounter_type"])
      ?: throw IllegalArgumentException("Form resource is required")
  val liveEncounterData = liveData { emit(prepareEditEncounter()) }
  val isResourcesSaved = MutableLiveData<Boolean>()

  private lateinit var questionnaire: Questionnaire
  private lateinit var observations: List<Observation>
  private lateinit var contitions: List<Condition>
  private lateinit var patientReference: Reference

  private suspend fun prepareEditEncounter(): Pair<String, String> {
    // TODO to be improved: if the asset is not present a message should be displayed.
    val encounter = fhirEngine.get<Encounter>(encounterId)
    observations = getObservationsEncounterId(encounterId)
    contitions = getConditionsEncounterId(encounterId)
    patientReference = encounter.subject
    try {
      val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

      questionnaire =
        fhirEngine
          .search<Questionnaire> {}
          .filter { questionnaire -> questionnaire.resource.code.any { it.code == encounterType } }
          .first()
          .resource

      val questionnaireJson = parser.encodeResourceToString(questionnaire)

      val observationBundle =
        Bundle().apply {
          type = Bundle.BundleType.COLLECTION
          observations.forEach { addEntry().resource = it.apply { id = "Observation/$id" } }
        }

      val launchContexts = mapOf("observations" to observationBundle)
      val questionnaireResponse = ResourceMapper.populate(questionnaire, launchContexts)
      val questionnaireResponseJson = parser.encodeResourceToString(questionnaireResponse)
      return questionnaireJson to questionnaireResponseJson
    } catch (e: FileNotFoundException) {
      // TODO add log here
      return Pair("", "")
    }
  }

  fun updateEncounter(questionnaireResponse: QuestionnaireResponse) {
    viewModelScope.launch {
      val bundle = ResourceMapper.extract(questionnaire, questionnaireResponse)
      saveResources(bundle)
      isResourcesSaved.value = true
    }
  }

  private suspend fun saveResources(bundle: Bundle) {
    val encounterReference = Reference("Encounter/$encounterId")
    val encounterSubject = fhirEngine.get<Encounter>(encounterId).subject

    bundle.entry.forEach {
      when (val resource = it.resource) {
        is Observation -> {
          if (resource.hasCode()) {
            handleObservation(resource, encounterReference, patientReference)
          }
        }
        is Condition -> {
          if (resource.hasCode()) {
            handleCondition(resource, encounterReference, encounterSubject)
          }
        }
      }
    }
  }

  private suspend fun handleObservation(
    resource: Observation,
    encounterReference: Reference,
    subjectReference: Reference,
  ) {
    val existingObservation =
      observations.find { obs ->
        obs.code.coding.any { coding -> coding.code == resource.code.codingFirstRep.code }
      }

    if (existingObservation != null && existingObservation.value.equalsDeep(resource.value)) {
      return
    }

    existingObservation?.apply {
      id = existingObservation.id
      status = Observation.ObservationStatus.AMENDED
      value = resource.value
    }

    if (existingObservation != null && existingObservation.hasValue()) {
      updateResourceToDatabase(existingObservation)
    } else {
      resource.apply {
        id = UUID.randomUUID().toString()
        subject = subjectReference
        encounter = encounterReference
        status = Observation.ObservationStatus.FINAL
        effective = DateTimeType(Date())
      }
      if (resource.hasValue()) {
        createResourceToDatabase(resource)
      }
    }
  }

  private suspend fun handleCondition(
    resource: Condition,
    encounterReference: Reference,
    subjectReference: Reference,
  ) {
    val existingCondition =
      contitions.find { cond ->
        cond.code.coding.any { coding -> coding.code == resource.code.codingFirstRep.code }
      }
    if (existingCondition != null) {
      resource.id = existingCondition.id
    } else {
      resource.id = UUID.randomUUID().toString()
    }
    resource.subject = subjectReference
    resource.encounter = encounterReference

    updateResourceToDatabase(resource)
  }

  private suspend fun getObservationsEncounterId(encounterId: String): List<Observation> {
    val searchResult =
      fhirEngine.search<Observation> {
        filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" })
      }
    return searchResult.map { it.resource }
  }

  private suspend fun getConditionsEncounterId(encounterId: String): List<Condition> {
    val searchResult =
      fhirEngine.search<Condition> {
        filter(Condition.ENCOUNTER, { value = "Encounter/$encounterId" })
      }
    return searchResult.map { it.resource }
  }

  private suspend fun updateResourceToDatabase(resource: Resource) {
    fhirEngine.update(resource)
  }

  private suspend fun createResourceToDatabase(resource: Resource) {
    fhirEngine.create(resource)
  }
}
