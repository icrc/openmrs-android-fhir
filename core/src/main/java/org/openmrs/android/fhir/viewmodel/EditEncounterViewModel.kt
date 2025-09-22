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
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.UUID
import kotlin.collections.forEach
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.convertDateAnswersToUtcDateTime
import org.openmrs.android.fhir.extensions.convertDateTimeAnswersToDate
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.getJsonFileNames
import org.openmrs.android.fhir.extensions.nowUtcDateTime
import org.openmrs.android.fhir.extensions.readFileFromAssets
import org.openmrs.android.fhir.util.CodingKey
import org.openmrs.android.fhir.util.ObservationChildInfo
import org.openmrs.android.fhir.util.ParentKey
import org.openmrs.android.fhir.util.collectObservationChildInfos
import org.openmrs.android.fhir.util.findObservationChildInfo
import timber.log.Timber

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

  private val _encounterDataPair = MutableLiveData<Pair<String, String>>()
  val encounterDataPair: LiveData<Pair<String, String>> = _encounterDataPair

  val isResourcesSaved = MutableLiveData<String>()
  val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  fun prepareEditEncounter(encounterId: String, encounterType: String) {
    viewModelScope.launch {
      val observations = getObservationsEncounterId(encounterId)
      try {
        var questionnaire =
          fhirEngine
            .search<Questionnaire> {}
            .firstOrNull { questionnaire ->
              questionnaire.resource.code.any { it.code == encounterType }
            }
            ?.resource
        if (questionnaire == null) {
          // Look into assets folder
          val assetQuestionnaireFileNames = applicationContext.getJsonFileNames()
          assetQuestionnaireFileNames.forEach {
            val questionnaireString = applicationContext.readFileFromAssets(it)
            if (questionnaireString.isNotEmpty()) {
              val assetsQuestionnaire =
                parser.parseResource(Questionnaire::class.java, questionnaireString)
              if (assetsQuestionnaire.hasCode()) {
                assetsQuestionnaire.code.forEach {
                  if (
                    it.hasSystem() and
                      (it?.system.toString() ==
                        "http://fhir.openmrs.org/code-system/encounter-type") and
                      it.hasCode() and
                      (it.code == encounterType)
                  ) {
                    questionnaire = assetsQuestionnaire
                    return@forEach
                  }
                }
              }
            }
          }
        }

        val questionnaireJson = parser.encodeResourceToString(questionnaire)

        val observationBundle =
          Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            observations.forEach { addEntry().resource = it.apply { id = "Observation/$id" } }
          }

        val launchContexts = mapOf("observations" to observationBundle)
        var questionnaireResponse =
          ResourceMapper.populate(
            questionnaire!!,
            launchContexts,
          ) // if questionnaire is null it'll throw exception while encoding to string.
        convertDateTimeAnswersToDate(questionnaireResponse)
        val questionnaireResponseJson = parser.encodeResourceToString(questionnaireResponse)
        _encounterDataPair.value = questionnaireJson to questionnaireResponseJson
      } catch (e: Exception) {
        Timber.e(e.localizedMessage)
        _encounterDataPair.value = Pair("", "")
      }
    }
  }

  fun updateEncounter(
    questionnaireResponse: QuestionnaireResponse,
    encounterId: String,
    encounterType: String,
  ) {
    viewModelScope.launch {
      var questionnaire =
        fhirEngine
          .search<Questionnaire> {}
          .firstOrNull { questionnaire ->
            questionnaire.resource.code.any { it.code == encounterType }
          }
          ?.resource
      if (questionnaire == null) {
        // Look into assets folder
        val assetQuestionnaireFileNames = applicationContext.getJsonFileNames()
        assetQuestionnaireFileNames.forEach {
          val questionnaireString = applicationContext.readFileFromAssets(it)
          if (questionnaireString.isNotEmpty()) {
            val assetsQuestionnaire =
              parser.parseResource(Questionnaire::class.java, questionnaireString)
            if (assetsQuestionnaire.hasCode()) {
              assetsQuestionnaire.code.forEach {
                if (
                  it.hasSystem() and
                    (it?.system.toString() ==
                      "http://fhir.openmrs.org/code-system/encounter-type") and
                    it.hasCode() and
                    (it.code == encounterType)
                ) {
                  questionnaire = assetsQuestionnaire
                  return@forEach
                }
              }
            }
          }
        }
      }

      val questionnaireResource =
        questionnaire
          ?: run {
            isResourcesSaved.value = "ERROR"
            return@launch
          }

      convertDateAnswersToUtcDateTime(questionnaireResponse)
      val bundle = ResourceMapper.extract(questionnaireResource, questionnaireResponse)

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
        isResourcesSaved.value = "MISSING"
        return@launch
      }

      saveResources(encounterId, bundle, questionnaireResource, questionnaireResponse)
    }
  }

  private suspend fun saveResources(
    encounterId: String,
    bundle: Bundle,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ) {
    try {
      val encounterReference = Reference("Encounter/$encounterId")
      val encounterSubject = fhirEngine.get<Encounter>(encounterId).subject
      val observations = getObservationsEncounterId(encounterId)
      val conditions = getConditionsEncounterId(encounterId)

      val observationChildInfos = collectObservationChildInfos(questionnaire, questionnaireResponse)
      val childInfosByCodingKey =
        observationChildInfos
          .flatMap { info -> info.childCodingKeys.map { codingKey -> codingKey to info } }
          .groupBy({ it.first }, { it.second })
      val parentCodingKeys = observationChildInfos.map { it.parentCodingKey }.toSet()
      val parentObservationsByKey = mutableMapOf<ParentKey, Observation>()
      val existingParentObservationsById = mutableMapOf<String, Observation>()
      val existingChildObservationsByKey = mutableMapOf<CodingKey, MutableList<Observation>>()

      observations.forEach { observation ->
        if (!observation.hasCode()) return@forEach
        val codingKeys =
          observation.code.coding.map { CodingKey.fromCoding(it) }.filter { it.isValid() }
        if (codingKeys.isEmpty()) return@forEach

        val parentKey = codingKeys.firstOrNull { parentCodingKeys.contains(it) }
        if (parentKey != null) {
          parentObservationsByKey[ParentKey(parentKey)] = observation
          observationIdPart(observation)?.let { existingParentObservationsById[it] = observation }
        } else {
          if (observation.status != Observation.ObservationStatus.CANCELLED) {
            codingKeys.forEach { key ->
              existingChildObservationsByKey.getOrPut(key) { mutableListOf() }.add(observation)
            }
          }
        }
      }

      val originalParentIds = existingParentObservationsById.keys.toSet()
      val touchedParentIds = mutableSetOf<String>()
      val parentUpdatesScheduled = mutableSetOf<String>()

      bundle.entry.forEach { entry ->
        when (val resource = entry.resource) {
          is Observation -> {
            if (resource.hasCode()) {
              handleObservation(
                resource,
                encounterReference,
                encounterSubject,
                existingChildObservationsByKey,
                childInfosByCodingKey,
                parentObservationsByKey,
                touchedParentIds,
                parentUpdatesScheduled,
              )
            }
          }
          is Condition -> {
            if (resource.hasCode()) {
              handleCondition(resource, encounterReference, encounterSubject, conditions)
            }
          }
        }
      }

      cancelRemainingChildObservations(
        existingChildObservationsByKey,
        existingParentObservationsById,
        touchedParentIds,
        parentUpdatesScheduled,
      )

      val parentIdsToCancel = originalParentIds - touchedParentIds
      parentIdsToCancel.forEach { parentId ->
        val parentObservation = existingParentObservationsById[parentId] ?: return@forEach
        parentObservation.status = Observation.ObservationStatus.CANCELLED
        parentObservation.effective = nowUtcDateTime()
        updateResourceToDatabase(parentObservation)
      }

      isResourcesSaved.value = "SAVED"
    } catch (e: Exception) {
      Timber.e(e.localizedMessage)
      isResourcesSaved.value = "MISSING"
    }
  }

  private suspend fun handleObservation(
    resource: Observation,
    encounterReference: Reference,
    subjectReference: Reference,
    existingChildObservationsByKey: MutableMap<CodingKey, MutableList<Observation>>,
    childInfosByCodingKey: Map<CodingKey, List<ObservationChildInfo>>,
    parentObservationsByKey: MutableMap<ParentKey, Observation>,
    touchedParentIds: MutableSet<String>,
    parentUpdatesScheduled: MutableSet<String>,
  ) {
    if (!resource.hasCode() || !resource.hasValue()) return

    val matchingInfo = findObservationChildInfo(resource, childInfosByCodingKey)
    val parentResult =
      if (matchingInfo != null) {
        ensureParentObservation(
          matchingInfo,
          parentObservationsByKey,
          subjectReference,
          encounterReference,
          touchedParentIds,
        )
      } else {
        null
      }

    when (val value = resource.value) {
      is StringType,
      is Quantity, -> {
        val existing = findExistingObservation(resource, existingChildObservationsByKey)
        val parent = parentResult?.parent
        if (existing != null) {
          val needsParentLink = requiresPartOfLink(existing, parent)
          if (!needsParentLink && existing.value.equalsDeep(value)) {
            parentResult.handleUnchangedChild(parentUpdatesScheduled)
            return
          }
          parentResult.markExistingParentAmended(parentUpdatesScheduled)
          existing.status = Observation.ObservationStatus.AMENDED
          existing.value = value
          existing.effective = nowUtcDateTime()
          if (parent != null) {
            existing.ensurePartOf(parent)
          }
          updateResourceToDatabase(existing)
        } else {
          val newObservation =
            Observation().apply {
              id = generateUuid()
              code = resource.code.copy()
              subject = subjectReference
              encounter = encounterReference
              status = Observation.ObservationStatus.FINAL
              effective = nowUtcDateTime()
              this.value = resource.value.copy()
            }
          parentResult.markExistingParentAmended(parentUpdatesScheduled)
          parent?.let { newObservation.ensurePartOf(it) }
          createResourceToDatabase(newObservation)
        }
      }
      is CodeableConcept -> {
        val codings = value.coding
        if (codings.size <= 1) {
          val existing = findExistingObservation(resource, existingChildObservationsByKey)
          val parent = parentResult?.parent
          if (existing != null) {
            val needsParentLink = requiresPartOfLink(existing, parent)
            if (!needsParentLink && existing.value.equalsDeep(value)) {
              parentResult.handleUnchangedChild(parentUpdatesScheduled)
              return
            }
            parentResult.markExistingParentAmended(parentUpdatesScheduled)
            val target = existing
            target.status = Observation.ObservationStatus.AMENDED
            target.value = value.copy()
            target.effective = nowUtcDateTime()
            if (parent != null) {
              target.ensurePartOf(parent)
            }
            updateResourceToDatabase(target)
          } else {
            val target =
              Observation().apply {
                id = generateUuid()
                code = resource.code.copy()
                subject = subjectReference
                encounter = encounterReference
                status = Observation.ObservationStatus.FINAL
                effective = nowUtcDateTime()
                this.value = value.copy()
              }
            parentResult.markExistingParentAmended(parentUpdatesScheduled)
            parent?.let { target.ensurePartOf(it) }
            createResourceToDatabase(target)
          }
        } else {
          val existingMatches = findAllExistingObservations(codings, existingChildObservationsByKey)
          existingMatches.forEach { existing ->
            val idPart = existing.idElement.idPart
            if (!idPart.isNullOrBlank()) {
              fhirEngine.purge(existing.resourceType, idPart)
            }
          }

          val parent = parentResult?.parent
          parentResult.markExistingParentAmended(parentUpdatesScheduled)
          codings.forEach { coding ->
            val obs =
              Observation().apply {
                id = generateUuid()
                code = resource.code.copy()
                subject = subjectReference
                encounter = encounterReference
                status = Observation.ObservationStatus.FINAL
                effective = nowUtcDateTime()
                setValue(CodeableConcept().apply { addCoding(coding.copy()) })
              }
            parent?.let { obs.ensurePartOf(it) }
            createResourceToDatabase(obs)
          }
        }
      }
      else -> {}
    }
  }

  private fun findExistingObservation(
    resource: Observation,
    existingChildObservationsByKey: MutableMap<CodingKey, MutableList<Observation>>,
  ): Observation? {
    resource.code.coding.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      val existing = existingChildObservationsByKey[key]?.firstOrNull()
      if (existing != null) {
        consumeExistingObservation(existing, existingChildObservationsByKey)
        return existing
      }
    }
    return null
  }

  private fun findAllExistingObservations(
    codings: List<Coding>,
    existingChildObservationsByKey: MutableMap<CodingKey, MutableList<Observation>>,
  ): List<Observation> {
    val results = linkedSetOf<Observation>()
    codings.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      existingChildObservationsByKey[key]?.let { results.addAll(it) }
    }
    results.forEach { consumeExistingObservation(it, existingChildObservationsByKey) }
    return results.toList()
  }

  private fun consumeExistingObservation(
    observation: Observation,
    existingChildObservationsByKey: MutableMap<CodingKey, MutableList<Observation>>,
  ) {
    observation.code.coding.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      val list = existingChildObservationsByKey[key]
      list?.remove(observation)
      if (list != null && list.isEmpty()) {
        existingChildObservationsByKey.remove(key)
      }
    }
  }

  private suspend fun ensureParentObservation(
    info: ObservationChildInfo,
    parentObservationsByKey: MutableMap<ParentKey, Observation>,
    subjectReference: Reference,
    encounterReference: Reference,
    touchedParentIds: MutableSet<String>,
  ): ParentEnsureResult {
    val parentKey = ParentKey(info.parentCodingKey)
    val existingParent = parentObservationsByKey[parentKey]
    return if (existingParent != null) {
      observationIdPart(existingParent)?.let { touchedParentIds.add(it) }
      ParentEnsureResult(existingParent, false)
    } else {
      val parent = createParentObservation(info, subjectReference, encounterReference)
      parentObservationsByKey[parentKey] = parent
      observationIdPart(parent)?.let { touchedParentIds.add(it) }
      createResourceToDatabase(parent)
      ParentEnsureResult(parent, true)
    }
  }

  private suspend fun markParentAmended(
    parent: Observation,
    parentUpdatesScheduled: MutableSet<String>,
  ) {
    val parentId = observationIdPart(parent) ?: return
    if (!parentUpdatesScheduled.add(parentId)) {
      return
    }
    parent.status = Observation.ObservationStatus.AMENDED
    parent.effective = nowUtcDateTime()
    updateResourceToDatabase(parent)
  }

  private suspend fun ParentEnsureResult?.markExistingParentAmended(
    parentUpdatesScheduled: MutableSet<String>,
  ) {
    if (this == null || isNew) {
      return
    }
    markParentAmended(parent, parentUpdatesScheduled)
  }

  private suspend fun ParentEnsureResult?.handleUnchangedChild(
    parentUpdatesScheduled: MutableSet<String>,
  ) {
    if (this == null || isNew) {
      return
    }
    if (parent.status == Observation.ObservationStatus.CANCELLED) {
      markParentAmended(parent, parentUpdatesScheduled)
    }
  }

  private fun Observation.ensurePartOf(parent: Observation): Boolean {
    val parentId = observationIdPart(parent) ?: return false
    val reference = "Observation/$parentId"
    if (partOf.any { it.reference == reference }) {
      return false
    }
    addPartOf(Reference(reference))
    return true
  }

  private fun requiresPartOfLink(child: Observation, parent: Observation?): Boolean {
    if (parent == null) {
      return false
    }
    val parentId = observationIdPart(parent) ?: return false
    val reference = "Observation/$parentId"
    return child.partOf.none { it.reference == reference }
  }

  private fun observationIdPart(observation: Observation): String? {
    val idPart = observation.idElement?.idPart
    if (!idPart.isNullOrBlank()) {
      return idPart
    }
    return observation.id.takeIf { !it.isNullOrBlank() }
  }

  private fun Reference.observationReferenceId(): String? {
    val element = referenceElement
    val resourceType = element.resourceType
    if (!resourceType.isNullOrBlank() && resourceType != ResourceType.Observation.name) {
      return null
    }
    return element.idPart.takeIf { !it.isNullOrBlank() }
  }

  private suspend fun cancelRemainingChildObservations(
    existingChildObservationsByKey: MutableMap<CodingKey, MutableList<Observation>>,
    existingParentObservationsById: Map<String, Observation>,
    touchedParentIds: Set<String>,
    parentUpdatesScheduled: MutableSet<String>,
  ) {
    val remainingChildren = existingChildObservationsByKey.values.flatten().toSet()
    if (remainingChildren.isEmpty()) {
      return
    }

    remainingChildren.forEach { child ->
      val parentIdsToAmend =
        child.partOf
          .mapNotNull { it.observationReferenceId() }
          .filter { touchedParentIds.contains(it) }

      parentIdsToAmend.forEach { parentId ->
        existingParentObservationsById[parentId]?.let {
          markParentAmended(it, parentUpdatesScheduled)
        }
      }

      if (child.status != Observation.ObservationStatus.CANCELLED) {
        child.status = Observation.ObservationStatus.CANCELLED
        child.effective = nowUtcDateTime()
        updateResourceToDatabase(child)
      }
    }
  }

  private fun createParentObservation(
    info: ObservationChildInfo,
    subjectReference: Reference,
    encounterReference: Reference,
  ): Observation {
    return Observation().apply {
      id = generateUuid()
      code = CodeableConcept().addCoding(info.parentCoding.copy())
      subject = subjectReference
      encounter = encounterReference
      status = Observation.ObservationStatus.FINAL
      effective = nowUtcDateTime()
    }
  }

  private data class ParentEnsureResult(val parent: Observation, val isNew: Boolean)

  private suspend fun handleCondition(
    resource: Condition,
    encounterReference: Reference,
    subjectReference: Reference,
    conditions: List<Condition>,
  ) {
    val existingCondition =
      conditions.find { cond ->
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
