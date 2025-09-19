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
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.BooleanType
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.DecimalType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.IntegerType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.TimeType
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.convertDateAnswersToUtcDateTime
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets
import org.openmrs.android.fhir.extensions.nowUtcDateTime

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

      convertDateAnswersToUtcDateTime(questionnaireResponse)
      val bundle = ResourceMapper.extract(questionnaire, questionnaireResponse)
      val patientReference = Reference("Patient/$patientId")

      val encounterDate = sessionDate ?: Date()

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

    val observationChildInfos = collectObservationChildInfos(questionnaire, questionnaireResponse)
    val childInfosByCodingKey =
      observationChildInfos
        .flatMap { info -> info.childCodingKeys.map { codingKey -> codingKey to info } }
        .groupBy({ it.first }, { it.second })
    val parentObservationsByKey = mutableMapOf<ParentKey, Observation>()
    val parentsToSave = mutableListOf<Observation>()
    val observationsToSave = mutableListOf<Observation>()

    bundle.entry.forEach { entry ->
      when (val resource = entry.resource) {
        is Observation -> {
          if (resource.hasCode() && resource.hasValue()) {
            val observationEntities =
              createObservationEntities(resource, patientReference, encounterReference)
            observationEntities.forEach { observation ->
              val matchingInfo = findObservationChildInfo(observation, childInfosByCodingKey)
              if (matchingInfo != null) {
                val parentKey = ParentKey(matchingInfo.parentCodingKey)
                val parentObservation =
                  parentObservationsByKey.getOrPut(parentKey) {
                    createParentObservation(
                        matchingInfo,
                        patientReference,
                        encounterReference,
                      )
                      .also { parentsToSave.add(it) }
                  }
                observation.addPartOf(Reference("Observation/${parentObservation.id}"))
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
            saveResourceToDatabase(resource)
          }
        }
      }
    }

    parentsToSave.forEach { saveResourceToDatabase(it) }
    observationsToSave.forEach { saveResourceToDatabase(it) }
  }

  private fun createObservationEntities(
    resource: Observation,
    patientReference: Reference,
    encounterReference: Reference,
  ): List<Observation> =
    resource.value?.let { value ->
      listOf(
        Observation().apply {
          id = generateUuid()
          code = resource.code.copy()
          subject = patientReference
          encounter = encounterReference
          status = Observation.ObservationStatus.FINAL
          effective = nowUtcDateTime()
          this.value = value.copy()
        },
      )
    }
      ?: emptyList()

  private fun createParentObservation(
    info: ObservationChildInfo,
    patientReference: Reference,
    encounterReference: Reference,
  ): Observation {
    val parentValue = info.parentDisplay?.takeIf { it.isNotBlank() } ?: info.parentCoding.code
    return Observation().apply {
      id = generateUuid()
      code = CodeableConcept().addCoding(info.parentCoding.copy())
      subject = patientReference
      encounter = encounterReference
      status = Observation.ObservationStatus.FINAL
      effective = nowUtcDateTime()
      parentValue?.let { this.value = StringType(it) }
    }
  }

  private fun findObservationChildInfo(
    observation: Observation,
    childInfosByCodingKey: Map<CodingKey, List<ObservationChildInfo>>,
  ): ObservationChildInfo? {
    if (!observation.hasCode()) {
      return null
    }

    val candidateInfos =
      observation.code.coding
        .flatMap { coding -> childInfosByCodingKey[CodingKey.fromCoding(coding)] ?: emptyList() }
        .distinct()

    if (candidateInfos.isEmpty()) {
      return null
    }

    if (candidateInfos.size == 1) {
      return candidateInfos.first()
    }

    val observationTokens = observation.value.toComparisonTokens()
    if (observationTokens.isEmpty()) {
      return candidateInfos.first()
    }

    val matchingInfos =
      candidateInfos.filter { info ->
        info.expectedValueTokens.isEmpty() ||
          observationTokens.any { token -> info.expectedValueTokens.contains(token) }
      }

    return when {
      matchingInfos.size == 1 -> matchingInfos.first()
      matchingInfos.isNotEmpty() -> matchingInfos.first()
      else -> candidateInfos.first()
    }
  }

  private fun collectObservationChildInfos(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): List<ObservationChildInfo> {
    val responseItemsByLinkId =
      mutableMapOf<String, QuestionnaireResponse.QuestionnaireResponseItemComponent>()
    collectResponseItems(questionnaireResponse.item, responseItemsByLinkId)

    data class GroupContext(
      val valueLinkIds: MutableSet<String> = mutableSetOf(),
    )

    val groupStack = mutableListOf<GroupContext>()
    val childInfos = mutableListOf<ObservationChildInfo>()

    fun traverse(item: Questionnaire.QuestionnaireItemComponent) {
      val currentContext = groupStack.lastOrNull()

      if (currentContext != null && item.isObservationValueItem()) {
        currentContext.valueLinkIds.add(item.linkId)
      }

      if (item.hasObservationChildExtension()) {
        val parentCoding = item.observationChildCoding() ?: return
        val parentCodingKey = CodingKey.fromCoding(parentCoding)
        if (!parentCodingKey.isValid()) {
          return
        }
        val childCodingKeys = resolveChildCodingKeys(item, responseItemsByLinkId)
        if (childCodingKeys.isEmpty()) {
          return
        }
        val valueTokens =
          currentContext
            ?.valueLinkIds
            ?.flatMap { linkId -> responseItemsByLinkId[linkId]?.answerTokens() ?: emptySet() }
            ?.toSet()
            ?: emptySet()
        childInfos.add(
          ObservationChildInfo(
            childLinkId = item.linkId,
            childCodingKeys = childCodingKeys,
            parentCoding = parentCoding.copy(),
            parentCodingKey = parentCodingKey,
            parentDisplay = parentCoding.display,
            expectedValueTokens = valueTokens,
          ),
        )
      }

      if (item.type == Questionnaire.QuestionnaireItemType.GROUP) {
        groupStack.add(GroupContext())
        item.item.forEach { traverse(it) }
        groupStack.removeAt(groupStack.lastIndex)
      } else {
        item.item.forEach { traverse(it) }
      }
    }

    questionnaire.item.forEach { traverse(it) }

    return childInfos
  }

  private fun collectResponseItems(
    items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>,
    result: MutableMap<String, QuestionnaireResponse.QuestionnaireResponseItemComponent>,
  ) {
    items.forEach { item ->
      val linkId = item.linkId
      if (!linkId.isNullOrBlank()) {
        result[linkId] = item
      }
      collectResponseItems(item.item, result)
    }
  }

  private fun resolveChildCodingKeys(
    childItem: Questionnaire.QuestionnaireItemComponent,
    responseItemsByLinkId: Map<String, QuestionnaireResponse.QuestionnaireResponseItemComponent>,
  ): Set<CodingKey> {
    val keys = mutableSetOf<CodingKey>()
    responseItemsByLinkId[childItem.linkId]?.answer?.forEach { answer ->
      when (val value = answer.value) {
        is Coding -> {
          val key = CodingKey.fromCoding(value)
          if (key.isValid()) {
            keys.add(key)
          }
        }
        is CodeableConcept -> {
          value.coding.forEach { coding ->
            val key = CodingKey.fromCoding(coding)
            if (key.isValid()) {
              keys.add(key)
            }
          }
        }
      }
    }

    if (keys.isEmpty()) {
      childItem.initial
        .mapNotNull { it.value }
        .forEach { initialValue ->
          when (initialValue) {
            is Coding -> {
              val key = CodingKey.fromCoding(initialValue)
              if (key.isValid()) {
                keys.add(key)
              }
            }
            is CodeableConcept -> {
              initialValue.coding.forEach { coding ->
                val key = CodingKey.fromCoding(coding)
                if (key.isValid()) {
                  keys.add(key)
                }
              }
            }
          }
        }
    }

    return keys
  }

  private fun QuestionnaireResponse.QuestionnaireResponseItemComponent.answerTokens(): Set<String> {
    if (answer.isEmpty()) {
      return emptySet()
    }
    return answer.flatMap { it.value.toComparisonTokens() }.toSet()
  }

  private fun Questionnaire.QuestionnaireItemComponent.isObservationValueItem(): Boolean {
    val definition = definition ?: return false
    return definition.contains("Observation#Observation.value") ||
      definition.contains("Observation.value")
  }

  private fun Questionnaire.QuestionnaireItemComponent.hasObservationChildExtension(): Boolean {
    return getExtensionByUrl(OBSERVATION_CHILD_EXTENSION_URL) != null
  }

  private fun Questionnaire.QuestionnaireItemComponent.observationChildCoding(): Coding? {
    return getExtensionByUrl(OBSERVATION_CHILD_EXTENSION_URL)?.value as? Coding
  }

  private fun Type?.toComparisonTokens(): Set<String> =
    when (this) {
      null -> emptySet()
      is CodeableConcept -> this.coding.firstOrNull()?.let { CodingKey.fromCoding(it).tokens() }
          ?: emptySet()
      is Coding -> CodingKey.fromCoding(this).tokens()
      is StringType -> this.value?.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
      is IntegerType -> this.value?.let { setOf(it.toString()) } ?: emptySet()
      is DecimalType -> this.value?.let { setOf(it.toPlainString()) } ?: emptySet()
      is BooleanType -> this.value?.let { setOf(it.toString()) } ?: emptySet()
      is DateType -> this.valueAsString.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
      is DateTimeType -> this.valueAsString.takeIf { it.isNotBlank() }?.let { setOf(it) }
          ?: emptySet()
      is TimeType -> this.value?.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
      is Quantity -> {
        val tokens = mutableSetOf<String>()
        val valuePart = this.value?.toPlainString()
        val unitPart = this.unit
        if (!valuePart.isNullOrBlank()) {
          tokens.add(valuePart)
        }
        if (!unitPart.isNullOrBlank()) {
          tokens.add(unitPart)
        }
        if (!valuePart.isNullOrBlank() || !unitPart.isNullOrBlank()) {
          tokens.add(listOfNotNull(valuePart, unitPart).joinToString("|"))
        }
        tokens
      }
      else -> setOf(toString())
    }

  private data class ObservationChildInfo(
    val childLinkId: String,
    val childCodingKeys: Set<CodingKey>,
    val parentCoding: Coding,
    val parentCodingKey: CodingKey,
    val parentDisplay: String?,
    val expectedValueTokens: Set<String>,
  )

  private data class ParentKey(
    val parentCodingKey: CodingKey,
  )

  private data class CodingKey(val system: String?, val code: String?) {
    fun isValid(): Boolean = !system.isNullOrBlank() || !code.isNullOrBlank()

    fun tokens(): Set<String> {
      val tokens = mutableSetOf<String>()
      if (!system.isNullOrBlank() && !code.isNullOrBlank()) {
        tokens.add("$system|$code")
      }
      if (!code.isNullOrBlank()) {
        tokens.add(code)
      }
      if (!system.isNullOrBlank()) {
        tokens.add(system)
      }
      return tokens
    }

    companion object {
      fun fromCoding(coding: Coding): CodingKey = CodingKey(coding.system, coding.code)
    }
  }

  private suspend fun saveResourceToDatabase(resource: Resource) {
    fhirEngine.create(resource)
  }

  private companion object {
    private const val OBSERVATION_CHILD_EXTENSION_URL =
      "http://fhir.openmrs.org/ext/observation-child"
  }

  fun updateQuestionnaire(updated: Questionnaire) {
    _questionnaire.value = updated
    _questionnaireJson.value = parser.encodeResourceToString(updated)
  }
}
