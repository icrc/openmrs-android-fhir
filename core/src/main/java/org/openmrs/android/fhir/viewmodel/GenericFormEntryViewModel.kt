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
import com.google.android.fhir.datacapture.extensions.allItems
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.codesystems.ConditionCategory
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.convertDateAnswersToUtcDateTime
import org.openmrs.android.fhir.extensions.findItemByLinkId
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets
import org.openmrs.android.fhir.extensions.nowUtcDateTime
import org.openmrs.android.fhir.util.ParentKey
import org.openmrs.android.fhir.util.buildObservationGroupLookup
import org.openmrs.android.fhir.util.createParentObservation
import org.openmrs.android.fhir.util.updateParentReference

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

  val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
  private val _questionnaire = MutableLiveData<Questionnaire?>()
  val questionnaire: LiveData<Questionnaire?> = _questionnaire
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
      if (_questionnaire.value == null) {
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

  fun prepareEnterEncounter(questionnaire: Questionnaire) {
    val encounterDateItem = findItemByLinkId(questionnaire.item, "encounter-encounterDate")
    encounterDateItem?.apply {
      val initialValue =
        when (type) {
          Questionnaire.QuestionnaireItemType.DATE -> DateType(Date())
          Questionnaire.QuestionnaireItemType.DATETIME -> DateTimeType(Date())
          else -> null
        }

      if (initialValue != null) {
        initial =
          listOf(
            Questionnaire.QuestionnaireItemInitialComponent().apply { value = initialValue },
          )
      }
    }
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

      val questionnaire: Questionnaire? = _questionnaire.value

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

      convertDateAnswersToUtcDateTime(questionnaireResponse)
      val bundle = ResourceMapper.extract(questionnaire, questionnaireResponse)
      val patientReference = Reference("Patient/$patientId")

      val encounterDate =
        sessionDate ?: extractSessionDateFromQuestionnaireResponse(questionnaireResponse)

      val visit: Encounter
      visit =
        if (Constants.WRAP_ENCOUNTER) {
          createWrapperVisit(patientId, encounterDate)
        } else {
          openMRSHelper.getActiveVisit(patientId, true)!!
        }

      saveResources(
        bundle,
        patientReference,
        questionnaire,
        questionnaireResponse,
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
    questionnaireResponse: QuestionnaireResponse,
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

    val observationGroupLookup = buildObservationGroupLookup(questionnaire, questionnaireResponse)
    val parentObservationsByKey = mutableMapOf<ParentKey, Observation>()
    val observationsToSave = mutableListOf<Observation>()

    bundle.entry.forEach { entry ->
      when (val resource = entry.resource) {
        is Observation -> {
          if (resource.hasCode() && resource.hasValue()) {
            val observationEntities =
              createObservationEntities(resource, patientReference, encounterReference)
            observationEntities.forEach { observation ->
              val matchingInfo = observationGroupLookup.findChildInfo(observation)
              if (matchingInfo != null) {
                val parentKey = ParentKey(matchingInfo.parentCodingKey)
                val parentObservation =
                  parentObservationsByKey.getOrPut(parentKey) {
                    createParentObservation(
                      matchingInfo,
                      patientReference,
                      encounterReference,
                    )
                  }
                observation.updateParentReference(parentObservation)
              }
              observationsToSave.add(observation)
            }
          }
        }
        is Condition -> {
          if (resource.hasCode()) {
            resource.id = generateUuid()
            resource.subject = patientReference
            resource.encounter = encounterReference
            // Current requirement for encounter-diagnosis only.
            resource.category =
              listOf(
                CodeableConcept().apply {
                  coding =
                    listOf(
                      Coding().apply {
                        system = Constants.CONDITION_CATEGORY_SYSTEM_URL
                        code = ConditionCategory.ENCOUNTERDIAGNOSIS.toCode()
                        display = ConditionCategory.ENCOUNTERDIAGNOSIS.display
                      },
                    )
                },
              )
            saveResourceToDatabase(resource)
          }
        }
      }
    }

    parentObservationsByKey.values.forEach { saveResourceToDatabase(it) }
    observationsToSave.forEach { saveResourceToDatabase(it) }
  }

  private fun createObservationEntities(
    resource: Observation,
    patientReference: Reference,
    encounterReference: Reference,
  ): List<Observation> {
    val value = resource.value ?: return emptyList()
    val effectiveDateTime = nowUtcDateTime()

    fun createBaseObservation() =
      Observation().apply {
        id = generateUuid()
        code = resource.code.copy()
        subject = patientReference
        encounter = encounterReference
        status = Observation.ObservationStatus.FINAL
        effective = effectiveDateTime.copy()
      }

    return if (value is CodeableConcept && value.coding.size > 1) {
      value.coding.map { coding ->
        createBaseObservation().apply {
          this.value =
            CodeableConcept().apply {
              text = value.text
              addCoding(
                coding.copy().apply {
                  if (!this.hasDisplay() && value.hasText()) {
                    display = value.text
                  }
                },
              )
            }
        }
      }
    } else {
      listOf(
        createBaseObservation().apply { this.value = value.copy() },
      )
    }
  }

  private suspend fun saveResourceToDatabase(resource: Resource) {
    fhirEngine.create(resource)
  }

  fun updateQuestionnaire(updated: Questionnaire) {
    _questionnaire.value = updated
    _questionnaireJson.value = parser.encodeResourceToString(updated)
  }

  private fun extractSessionDateFromQuestionnaireResponse(
    questionnaireResponse: QuestionnaireResponse,
  ): Date {
    val encounterDateAnswer =
      questionnaireResponse.allItems
        .firstOrNull { it.linkId == "encounter-encounterDate" }
        ?.answer
        ?.firstOrNull()

    if (encounterDateAnswer == null) {
      return Date()
    }

    if (encounterDateAnswer.value is DateTimeType) {
      return encounterDateAnswer.valueDateTimeType.value
    } else if (encounterDateAnswer.value is DateType) {
      return encounterDateAnswer.valueDateType.value
    }

    return Date()
  }
}
