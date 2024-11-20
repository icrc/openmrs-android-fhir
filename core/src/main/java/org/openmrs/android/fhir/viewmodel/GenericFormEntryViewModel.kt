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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.Form
import org.openmrs.android.fhir.MockConstants
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.readFileFromAssets
import org.openmrs.android.fhir.fragments.GenericFormEntryFragment
import org.openmrs.android.helpers.OpenMRSHelper
import org.openmrs.android.helpers.OpenMRSHelper.MiscHelper
import org.openmrs.android.helpers.OpenMRSHelper.UserHelper
import java.time.ZoneId
import java.util.Date
import java.util.UUID


/** ViewModel for Generic questionnaire screen {@link GenericFormEntryFragment}. */
class GenericFormEntryViewModel @AssistedInject constructor(private val applicationContext: Context, private val fhirEngine: FhirEngine, @Assisted val state: SavedStateHandle) :
  ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<GenericFormEntryViewModel> {
    override fun create(
      handle: SavedStateHandle
    ): GenericFormEntryViewModel
  }

  val questionnaire: String
    get() = getQuestionnaireJson()

  val isResourcesSaved = MutableLiveData<Boolean>()

  private val questionnaireResource: Questionnaire
    get() =
      FhirContext.forCached(FhirVersionEnum.R4).newJsonParser().parseResource(questionnaire)
        as Questionnaire

  private var questionnaireJson: String? = null

  suspend fun createWrapperVisit(patientId: String): Encounter {

    val localDate = Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    val localStartOfDay = localDate.atStartOfDay()

    val visitDate = Date.from(localStartOfDay.atZone(ZoneId.systemDefault()).toInstant())

    val visit = Encounter().apply {
      subject = Reference("Patient/$patientId")
      status = Encounter.EncounterStatus.INPROGRESS
      setPeriod(Period().apply { start = visitDate; end = visitDate })
      addParticipant(MiscHelper.createParticipant())
      addLocation(Encounter.EncounterLocationComponent().apply {
        location = Reference("Location/${UserHelper.getCurrentAuthLocation().id}")
      })
      addType(CodeableConcept().apply {
        coding = listOf(
          Coding().apply {
            system = "http://fhir.openmrs.org/code-system/visit-type"
            code = MockConstants.VISIT_TYPE_UUID
            display = "Facility Visit"
          }
        )
      })

    }

    fhirEngine.create(visit)

    return visit
  }

  /**
   * Saves generic encounter questionnaire response into the application database.
   *
   * @param questionnaireResponse generic encounter questionnaire response
   */
  fun saveEncounter(questionnaireResponse: QuestionnaireResponse, form: Form, patientId: String) {
    viewModelScope.launch {
      val bundle = ResourceMapper.extract(questionnaireResource, questionnaireResponse)
      val patientReference = Reference("Patient/$patientId")
      val encounterId = generateUuid()

      val visit : Encounter
      if (MockConstants.WRAP_ENCOUNTER) {
        visit = createWrapperVisit(patientId)
      } else {
        visit = OpenMRSHelper.VisitHelper.getActiveVisit(fhirEngine, patientId, true)!!
      }

      if (isRequiredFieldMissing(bundle)) {
        isResourcesSaved.value = false
        return@launch
      }

      saveResources(bundle, patientReference, form, encounterId, visit.idPart)
      isResourcesSaved.value = true
    }
  }

  private suspend fun saveResources(
    bundle: Bundle,
    patientReference: Reference,
    form: Form,
    encounterId: String,
    visitId: String,
  ) {
    val encounterReference = Reference("Encounter/$encounterId")
    val locationId = applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]

    val encounterDate: Date
    if (MockConstants.WRAP_ENCOUNTER) {
      val localDate = Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
      val localStartOfDay = localDate.atStartOfDay()
      encounterDate = Date.from(localStartOfDay.atZone(ZoneId.systemDefault()).toInstant())
    } else {
      encounterDate = Date()
    }

    bundle.entry
      .mapNotNull { it.resource as? Encounter }
      .forEach { encounter ->
        encounter.apply {
          subject = patientReference
          id = encounterId
          status = Encounter.EncounterStatus.FINISHED
          partOf = Reference("Encounter/$visitId")
          setPeriod(Period().apply { start = encounterDate; end = encounterDate })
          addParticipant(createParticipant())
          addLocation(Encounter.EncounterLocationComponent().apply {
            location = Reference("Location/$locationId")  // Set the location reference
          })

          addType(CodeableConcept().apply {
            coding = listOf(
              Coding().apply {
                system = "http://fhir.openmrs.org/code-system/encounter-type"
                code = form.code
                display = form.display
              }
            )
          })
          saveResourceToDatabase(this)
        }
      }

    bundle.entry.forEach {
      when (val resource = it.resource) {
        is Observation -> {
          if (resource.hasCode() && resource.hasValue()) {

            resource.apply {
              id = generateUuid()
              subject = patientReference
              encounter = encounterReference
              status = Observation.ObservationStatus.FINAL
              effective = DateTimeType(Date())
              value = resource.value
            }
            saveResourceToDatabase(resource)
          }
        }
        is Condition -> {
          if (resource.hasCode()) {
            resource.id = generateUuid()
            resource.subject = patientReference
            resource.encounter = encounterReference
            saveResourceToDatabase(resource)
          }
        }
      }
    }
  }

  fun createParticipant(): EncounterParticipantComponent {
    //TODO Replace this with method to get the authenticated user's reference
    val authenticatedUser = MockConstants.AUTHENTICATED_USER
    val participant = EncounterParticipantComponent()
    participant.individual = Reference("Practitioner/${authenticatedUser.providerUuid}")
    participant.individual.display = authenticatedUser.display
    participant.individual.type = "Practitioner"
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
      applicationContext
        .readFileFromAssets(state[GenericFormEntryFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
    return questionnaireJson!!
  }

  private fun generateUuid(): String {
    return UUID.randomUUID().toString()
  }
}
