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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.convertDateAnswersToDateTime
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets
import org.openmrs.android.helpers.OpenMRSHelper

/** ViewModel for Generic questionnaire screen {@link GenericFormEntryFragment}. */
class GenericFormEntryViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val openMRSHelper: OpenMRSHelper,
  @Assisted val state: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<GenericFormEntryViewModel> {
    override fun create(handle: SavedStateHandle): GenericFormEntryViewModel
  }

  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
  private val _questionnaire = MutableLiveData<Questionnaire>()
  val questionnaire: LiveData<Questionnaire> = _questionnaire
  private val _questionnaireJson = MutableLiveData<String>()
  val questionnaireJson: LiveData<String> = _questionnaireJson
  val isResourcesSaved = MutableLiveData<String>()
  val encounterType = getEncounterTypeValue()
  val isLoading = MutableLiveData<Boolean>()

  fun getEncounterQuestionnaire(questionnaireId: String) {
    viewModelScope.launch {
      _questionnaire.value =
        fhirEngine.getQuestionnaireOrFromAssets(
          questionnaireId,
          applicationContext,
          parser,
        )
      if (questionnaire == null) {
        _questionnaireJson.value = ""
      } else {
        _questionnaireJson.value = parser.encodeResourceToString(_questionnaire.value)
      }
    }
  }

  fun getEncounterTypeValue(): String? {
    return _questionnaire.value
      ?.code
      ?.firstOrNull { it.system == "http://fhir.openmrs.org/code-system/encounter-type" }
      ?.code
  }

  suspend fun createWrapperVisit(patientId: String, visitDate: Date): Encounter {
    val visit =
      Encounter().apply {
        subject = Reference("Patient/$patientId")
        status = Encounter.EncounterStatus.INPROGRESS
        setPeriod(
          Period().apply {
            start = visitDate
            end = visitDate
          },
        )

        addParticipant(openMRSHelper.createVisitParticipant())
        addLocation(
          Encounter.EncounterLocationComponent().apply {
            location = openMRSHelper.getCurrentAuthLocation()
          },
        )
        addType(
          CodeableConcept().apply {
            coding =
              listOf(
                Coding().apply {
                  system = Constants.VISIT_TYPE_CODE_SYSTEM
                  code = Constants.VISIT_TYPE_UUID
                  display = "Facility Visit"
                },
              )
          },
        )
      }

    fhirEngine.create(visit)

    return visit
  }

  /**
   * Saves generic encounter questionnaire response into the application database.
   *
   * @param questionnaireResponse generic encounter questionnaire response
   */
  fun saveEncounter(
    questionnaireResponse: QuestionnaireResponse,
    patientId: String,
    encounterId: String,
    sessionDate: Date? = null,
  ) {
    viewModelScope.launch {
      val questionnaireId = state.get<String>("questionnaire_id")

      if (questionnaireId.isNullOrBlank()) {
        throw IllegalArgumentException("No questionnaire ID provided")
      }

      val questionnaire: Questionnaire? =
        fhirEngine.getQuestionnaireOrFromAssets(
          questionnaireId,
          applicationContext,
          parser,
        )

      if (questionnaire == null) {
        throw IllegalStateException("No questionnaire resource found with ID: $questionnaireId")
      }

      if (
        QuestionnaireResponseValidator.validateQuestionnaireResponse(
            questionnaire,
            questionnaireResponse,
            applicationContext,
          )
          .values
          .flatten()
          .any { it is Invalid }
      ) {
        isResourcesSaved.value = "MISSING/$patientId"
        return@launch
      }

      convertDateAnswersToDateTime(questionnaireResponse)
      val bundle = ResourceMapper.extract(questionnaire, questionnaireResponse)
      val patientReference = Reference("Patient/$patientId")

      val encounterDate =
        if (Constants.WRAP_ENCOUNTER) {
          val localDate =
            (sessionDate ?: Date()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
          Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
        } else {
          sessionDate ?: Date()
        }

      val visit: Encounter
      if (Constants.WRAP_ENCOUNTER) {
        visit = createWrapperVisit(patientId, encounterDate)
      } else {
        visit = openMRSHelper.getActiveVisit(patientId, true)!!
      }

      saveResources(
        bundle,
        patientReference,
        questionnaire,
        encounterId,
        visit.idPart,
        encounterDate,
      )
      isResourcesSaved.value = "SAVED/$patientId"
    }
  }

  private suspend fun saveResources(
    bundle: Bundle,
    patientReference: Reference,
    questionnaire: Questionnaire,
    encounterId: String,
    visitId: String,
    encounterDate: Date,
  ) {
    val encounterReference = Reference("Encounter/$encounterId")
    val locationId = applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
    val encounterType =
      questionnaire.code.firstOrNull {
        it.system == "http://fhir.openmrs.org/code-system/encounter-type"
      }
    val omrsForm =
      questionnaire.code.firstOrNull {
        it.system == "http://fhir.openmrs.org/core/StructureDefinition/omrs-form"
      }

    bundle.entry
      .mapNotNull { it.resource as? Encounter }
      .forEach { encounter ->
        encounter.apply {
          subject = patientReference
          id = encounterId
          status = Encounter.EncounterStatus.FINISHED
          partOf = Reference("Encounter/$visitId")
          setPeriod(
            Period().apply {
              start = encounterDate
              end = encounterDate
            },
          )
          addParticipant(openMRSHelper.createEncounterParticipant())
          addLocation(
            Encounter.EncounterLocationComponent().apply {
              location = Reference("Location/$locationId")
            },
          )

          addType(
            CodeableConcept().apply {
              coding =
                listOf(
                  Coding().apply {
                    system = encounterType?.system
                    code = encounterType?.code
                    display = encounterType?.display
                  },
                )
            },
          )
          addType(
            CodeableConcept().apply {
              coding =
                listOf(
                  Coding().apply {
                    system = omrsForm?.system
                    code = omrsForm?.code
                    display = omrsForm?.display
                  },
                )
            },
          )
          saveResourceToDatabase(this)
        }
      }

    bundle.entry.forEach {
      when (val resource = it.resource) {
        is Observation -> {
          if (resource.hasCode() && resource.hasValue()) {
            when (val value = resource.value) {
              is CodeableConcept -> {
                val codings = value.coding
                codings.forEach { coding ->
                  val obs =
                    Observation().apply {
                      id = generateUuid()
                      code = resource.code
                      subject = patientReference
                      encounter = encounterReference
                      status = Observation.ObservationStatus.FINAL
                      effective = DateTimeType(Date())
                      this.value = CodeableConcept().addCoding(coding)
                    }
                  saveResourceToDatabase(obs)
                }
              }
              else -> {
                val obs =
                  Observation().apply {
                    id = generateUuid()
                    code = resource.code
                    subject = patientReference
                    encounter = encounterReference
                    status = Observation.ObservationStatus.FINAL
                    effective = DateTimeType(Date())
                    this.value = value
                  }
                saveResourceToDatabase(obs)
              }
            }
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

  private suspend fun saveResourceToDatabase(resource: Resource) {
    fhirEngine.create(resource)
  }

  fun updateQuestionnaire(updated: Questionnaire) {
    _questionnaire.value = updated
    _questionnaireJson.value = parser.encodeResourceToString(updated)
  }
}
