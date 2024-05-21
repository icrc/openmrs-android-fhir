/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.android.fhir.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.extensions.readFileFromAssets
import org.openmrs.android.fhir.fragments.GenericFormEntryFragment
import java.util.Date
import java.util.UUID


/** ViewModel for Generic questionnaire screen {@link GenericFormEntryFragment}. */
class GenericFormEntryViewModel(application: Application, private val state: SavedStateHandle) :
  AndroidViewModel(application) {
  val questionnaire: String
    get() = getQuestionnaireJson()

  val isResourcesSaved = MutableLiveData<Boolean>()

  private val questionnaireResource: Questionnaire
    get() =
      FhirContext.forCached(FhirVersionEnum.R4).newJsonParser().parseResource(questionnaire)
        as Questionnaire

  private var questionnaireJson: String? = null
  private var fhirEngine: FhirEngine = FhirApplication.fhirEngine(application.applicationContext)

  /**
   * Saves generic encounter questionnaire response into the application database.
   *
   * @param questionnaireResponse generic encounter questionnaire response
   */
  fun saveEncounter(questionnaireResponse: QuestionnaireResponse, patientId: String) {
    viewModelScope.launch {
      val bundle = ResourceMapper.extract(questionnaireResource, questionnaireResponse)
      val subjectReference = Reference("Patient/$patientId")
      val encounterId = generateUuid()
      if (isRequiredFieldMissing(bundle)) {
        isResourcesSaved.value = false
        return@launch
      }

      saveResources(bundle, subjectReference, encounterId)
      isResourcesSaved.value = true
    }
  }

  private suspend fun saveResources(
    bundle: Bundle,
    subjectReference: Reference,
    encounterId: String,
  ) {
    val encounterReference = Reference("Encounter/$encounterId")
    bundle.entry.forEach {
      when (val resource = it.resource) {
        is Observation -> {
          if (resource.hasCode()) {
            resource.id = generateUuid()
            resource.subject = subjectReference
            resource.encounter = encounterReference
            saveResourceToDatabase(resource)
          }
        }
        is Condition -> {
          if (resource.hasCode()) {
            resource.id = generateUuid()
            resource.subject = subjectReference
            resource.encounter = encounterReference
            saveResourceToDatabase(resource)
          }
        }
        is Encounter -> {
          //TODO Popular with live data
          resource.subject = subjectReference
          resource.id = encounterId
          resource.status = Encounter.EncounterStatus.FINISHED
          resource.addParticipant(createParticipant("test", subjectReference))
          val location = Encounter.EncounterLocationComponent()
          location.location = Reference("Location/123")
          val period = Period()
          period.start = Date()
          val encounterTypeCoding = Coding()
          encounterTypeCoding.system = "http://terminology.hl7.org/CodeSystem/v3-ActCode"
          encounterTypeCoding.code = "AMB"
          val encounterType = CodeableConcept()
          encounterType.coding = listOf(encounterTypeCoding)
          resource.addLocation(location)
          resource.setPeriod(period)
          resource.addType(encounterType)

          saveResourceToDatabase(resource)
        }
      }
    }
  }

  fun createParticipant(name: String, reference: Reference): EncounterParticipantComponent {
    val participant = EncounterParticipantComponent()
    participant.individual = reference
    participant.individual.display = name // Display name for the participant
    return participant
  }

  private fun isRequiredFieldMissing(bundle: Bundle): Boolean {
    bundle.entry.forEach {
      when (val resource = it.resource) {
        is Observation -> {
          if (resource.hasValueQuantity() && !resource.valueQuantity.hasValueElement()) {
            return true
          }
        }
        // TODO check other resources inputs
      }
    }
    return false
  }

  private suspend fun saveResourceToDatabase(resource: Resource) {
    fhirEngine.create(resource)
  }

  private fun getQuestionnaireJson(): String {
    questionnaireJson?.let {
      return it
    }
    questionnaireJson =
      getApplication<Application>()
        .readFileFromAssets(state[GenericFormEntryFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
    return questionnaireJson!!
  }

  private fun generateUuid(): String {
    return UUID.randomUUID().toString()
  }
}