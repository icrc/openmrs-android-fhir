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
import org.hl7.fhir.r4.model.IntegerType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.codesystems.ConditionCategory
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.convertDateAnswersToUtcDateTime
import org.openmrs.android.fhir.extensions.convertDateTimeAnswersToDate
import org.openmrs.android.fhir.extensions.convertQuantityObsToIntegerObs
import org.openmrs.android.fhir.extensions.ensurePageGroupsHaveTrailingSpacer
import org.openmrs.android.fhir.extensions.findItemByLinkId
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.getJsonFileNames
import org.openmrs.android.fhir.extensions.hasInvalidAnswersIgnoringPageSpacers
import org.openmrs.android.fhir.extensions.nowUtcDateTime
import org.openmrs.android.fhir.extensions.readFileFromAssets
import org.openmrs.android.fhir.extensions.utcDateToLocalDate
import org.openmrs.android.fhir.util.ExistingObservationIndex
import org.openmrs.android.fhir.util.ObservationChildInfo
import org.openmrs.android.fhir.util.ObservationGroupLookup
import org.openmrs.android.fhir.util.ParentEnsureResult
import org.openmrs.android.fhir.util.ParentObservationTracker
import org.openmrs.android.fhir.util.areSameValue
import org.openmrs.android.fhir.util.buildObservationGroupLookup
import org.openmrs.android.fhir.util.handleUnchangedChild
import org.openmrs.android.fhir.util.idPart
import org.openmrs.android.fhir.util.observationReferenceId
import org.openmrs.android.fhir.util.updateParentReference
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
    override fun create(handle: SavedStateHandle): EditEncounterViewModel
  }

  private val _encounterDataPair = MutableLiveData<Pair<String, String>>()
  val encounterDataPair: LiveData<Pair<String, String>> = _encounterDataPair

  val isResourcesSaved = MutableLiveData<String>()
  val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  private var pendingResourceOperations: MutableList<suspend () -> Unit>? = null

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

        questionnaire?.ensurePageGroupsHaveTrailingSpacer()

        val encounter = fhirEngine.get<Encounter>(encounterId)
        val encounterDate = encounter.period?.start?.let { utcDateToLocalDate(it) }
        if (encounterDate != null) {
          val encounterDateItem = findItemByLinkId(questionnaire?.item, "encounter-encounterDate")
          encounterDateItem?.apply {
            initial =
              listOf(
                Questionnaire.QuestionnaireItemInitialComponent().apply {
                  value = org.hl7.fhir.r4.model.DateTimeType(encounterDate)
                },
              )
            readOnly = true
          }
        }

        val questionnaireJson = parser.encodeResourceToString(questionnaire)

        val observationBundle =
          Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            entry =
              observations.map { obs ->
                if (obs.hasValueQuantity()) {
                  convertQuantityObsToIntegerObs(obs)
                }
                Bundle.BundleEntryComponent()
                  .setResource(obs.apply { id = "Observation/${idPart()}" })
              }
          }

        val launchContexts = mapOf("observations" to observationBundle)
        val questionnaireResponse =
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

      questionnaire?.ensurePageGroupsHaveTrailingSpacer()

      val questionnaireResource =
        questionnaire
          ?: run {
            isResourcesSaved.value = "ERROR"
            return@launch
          }

      convertDateAnswersToUtcDateTime(questionnaireResponse)
      val bundle = ResourceMapper.extract(questionnaireResource, questionnaireResponse)

      if (
        questionnaireResource.hasInvalidAnswersIgnoringPageSpacers(
          questionnaireResponse,
          applicationContext,
        )
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
    pendingResourceOperations = mutableListOf<suspend () -> Unit>()
    try {
      val encounterReference = Reference("Encounter/$encounterId")
      val encounterSubject = fhirEngine.get<Encounter>(encounterId).subject
      val observations = getObservationsEncounterId(encounterId).toMutableList()
      val conditions = getConditionsEncounterId(encounterId)

      val observationGroupLookup = buildObservationGroupLookup(questionnaire, questionnaireResponse)
      removeReplacedParentObservations(bundle, observationGroupLookup, observations)
      val observationIndex =
        ExistingObservationIndex(observationGroupLookup.parentCodingKeys, observations)
      val parentTracker = ParentObservationTracker()

      bundle.entry.forEach { entry ->
        when (val resource = entry.resource) {
          is Observation -> {
            if (resource.hasCode()) {
              handleObservation(
                resource,
                encounterReference,
                encounterSubject,
                observationGroupLookup,
                observationIndex,
                parentTracker,
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

      deleteRemainingChildObservations(observationIndex, parentTracker)

      val parentIdsToDelete = observationIndex.originalParentIds - parentTracker.touchedIds()
      parentIdsToDelete.forEach { parentId ->
        val parentObservation = observationIndex.parentById(parentId) ?: return@forEach
        softDeleteObservationFromDatabase(parentObservation)
      }

      flushPendingResourceOperations()
      isResourcesSaved.value = "SAVED"
    } catch (e: Exception) {
      Timber.e(e.localizedMessage)
      isResourcesSaved.value = "MISSING"
      pendingResourceOperations?.clear()
    } finally {
      pendingResourceOperations = null
    }
  }

  private suspend fun removeReplacedParentObservations(
    bundle: Bundle,
    observationGroupLookup: ObservationGroupLookup,
    observations: MutableList<Observation>,
  ) {
    val processedParentIds = mutableSetOf<String>()
    bundle.entry
      .mapNotNull { it.resource as? Observation }
      .forEach { extractedObservation ->
        val childInfo = observationGroupLookup.findChildInfo(extractedObservation) ?: return@forEach
        val parentObservation =
          observations.firstOrNull { candidate ->
            candidate.code.coding.any { coding -> coding.matchesCoding(childInfo.parentCoding) }
          }
            ?: return@forEach
        val parentId = parentObservation.idPart() ?: return@forEach
        if (!processedParentIds.add(parentId)) {
          return@forEach
        }

        softDeleteObservationFromDatabase(parentObservation)
        observations.remove(parentObservation)

        val childrenToRemove =
          observations.filter { candidate ->
            candidate.partOf.any { it.observationReferenceId() == parentId }
          }
        childrenToRemove.forEach { child -> softDeleteObservationFromDatabase(child) }
        observations.removeAll(childrenToRemove)
      }
  }

  private suspend fun handleObservation(
    resource: Observation,
    encounterReference: Reference,
    subjectReference: Reference,
    observationGroupLookup: ObservationGroupLookup,
    observationIndex: ExistingObservationIndex,
    parentTracker: ParentObservationTracker,
  ) {
    if (!resource.hasCode() || !resource.hasValue()) return

    val matchingInfo = observationGroupLookup.findChildInfo(resource)
    val parentResult =
      if (matchingInfo != null) {
        observationIndex.ensureParentObservation(
          matchingInfo,
          parentTracker,
          subjectReference,
          encounterReference,
          ::createResourceToDatabase,
        )
      } else {
        null
      }

    when (val value = resource.value) {
      is StringType,
      is IntegerType,
      is Quantity, -> {
        upsertSingleValueObservation(
          resource,
          subjectReference,
          encounterReference,
          parentResult,
          observationIndex,
          parentTracker,
          matchingInfo,
        )
      }
      is CodeableConcept -> {
        upsertCodeableConceptObservation(
          resource,
          subjectReference,
          encounterReference,
          value,
          parentResult,
          observationIndex,
          parentTracker,
          matchingInfo,
        )
      }
      else -> {}
    }
  }

  private suspend fun upsertSingleValueObservation(
    template: Observation,
    subjectReference: Reference,
    encounterReference: Reference,
    parentResult: ParentEnsureResult?,
    observationIndex: ExistingObservationIndex,
    parentTracker: ParentObservationTracker,
    childInfo: ObservationChildInfo?,
  ) {
    val value = template.value ?: return
    val parent = parentResult?.parent
    val existing = observationIndex.findExisting(template, childInfo, parent)
    if (existing != null) {
      val parentChanged = existing.updateParentReference(parent)
      if (!parentChanged && existing.value.equalsDeep(value)) {
        parentResult.handleUnchangedChild(parentTracker, ::updateResourceToDatabase)
        return
      }
      existing.status = Observation.ObservationStatus.AMENDED
      existing.value = value.copy()
      existing.effective = nowUtcDateTime()
      if (!parentChanged) {
        existing.updateParentReference(parent)
      }
      updateResourceToDatabase(existing)
    } else {
      val newObservation =
        Observation().apply {
          id = template.bundleGeneratedId()
          code = template.code.copy()
          subject = subjectReference
          encounter = encounterReference
          status = Observation.ObservationStatus.FINAL
          effective = nowUtcDateTime()
          this.value = value.copy()
        }
      newObservation.updateParentReference(parent)
      createResourceToDatabase(newObservation)
    }
  }

  private suspend fun upsertCodeableConceptObservation(
    template: Observation,
    subjectReference: Reference,
    encounterReference: Reference,
    value: CodeableConcept,
    parentResult: ParentEnsureResult?,
    observationIndex: ExistingObservationIndex,
    parentTracker: ParentObservationTracker,
    childInfo: ObservationChildInfo?,
  ) {
    val codings = value.coding
    val parent = parentResult?.parent
    if (codings.size <= 1) {
      val existing = observationIndex.findExisting(template, childInfo, parent)
      if (existing != null) {
        val parentChanged = existing.updateParentReference(parent)
        if (!parentChanged && areSameValue(existing.valueCodeableConcept.coding, value.coding)) {
          parentResult.handleUnchangedChild(parentTracker, ::updateResourceToDatabase)
          return
        }
        existing.status = Observation.ObservationStatus.AMENDED
        existing.value = value.copy()
        existing.effective = nowUtcDateTime()
        if (!parentChanged) {
          existing.updateParentReference(parent)
        }
        updateResourceToDatabase(existing)
      } else {
        val target =
          Observation().apply {
            id = template.bundleGeneratedId()
            code = template.code.copy()
            subject = subjectReference
            encounter = encounterReference
            status = Observation.ObservationStatus.FINAL
            effective = nowUtcDateTime()
            this.value = value.copy()
          }
        target.updateParentReference(parent)
        createResourceToDatabase(target)
      }
    } else {
      val existingMatches = observationIndex.findAllExisting(codings, childInfo, parent)
      existingMatches.forEach { existing -> softDeleteObservationFromDatabase(existing) }

      codings.forEachIndexed { index, coding ->
        val obs =
          Observation().apply {
            id = if (index == 0) template.bundleGeneratedId() else generateUuid()
            code = template.code.copy()
            subject = subjectReference
            encounter = encounterReference
            status = Observation.ObservationStatus.FINAL
            effective = nowUtcDateTime()
            setValue(CodeableConcept().apply { addCoding(coding.copy()) })
          }
        obs.updateParentReference(parent)
        createResourceToDatabase(obs)
      }
    }
  }

  private suspend fun deleteRemainingChildObservations(
    observationIndex: ExistingObservationIndex,
    parentTracker: ParentObservationTracker,
  ) {
    val remainingChildren = observationIndex.remainingChildObservations()
    if (remainingChildren.isEmpty()) {
      return
    }

    val parentMap = observationIndex.parentObservationsById()
    remainingChildren.forEach { child ->
      val parentIdsToAmend =
        child.partOf
          .mapNotNull { it.observationReferenceId() }
          .filter { parentTracker.wasTouched(it) }

      parentIdsToAmend.forEach { parentId ->
        parentMap[parentId]?.let { parent ->
          parentTracker.markAmended(parent, ::updateResourceToDatabase)
        }
      }

      softDeleteObservationFromDatabase(child)
    }
  }

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

    updateResourceToDatabase(resource)
  }

  private suspend fun getObservationsEncounterId(encounterId: String): List<Observation> {
    val searchResult =
      fhirEngine.search<Observation> {
        filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" })
      }
    return searchResult
      .map { it.resource }
      .filter { it.hasStatus() && it.status != Observation.ObservationStatus.CANCELLED }
  }

  private suspend fun getConditionsEncounterId(encounterId: String): List<Condition> {
    val searchResult =
      fhirEngine.search<Condition> {
        filter(Condition.ENCOUNTER, { value = "Encounter/$encounterId" })
      }
    return searchResult.map { it.resource }
  }

  private suspend fun updateResourceToDatabase(resource: Resource) {
    enqueueResourceOperation { fhirEngine.update(resource) }
  }

  private suspend fun createResourceToDatabase(resource: Resource) {
    enqueueResourceOperation { fhirEngine.create(resource) }
  }

  private suspend fun softDeleteObservationFromDatabase(observation: Observation) {
    observation.status = Observation.ObservationStatus.CANCELLED
    updateResourceToDatabase(observation)
  }

  private suspend fun enqueueResourceOperation(operation: suspend () -> Unit) {
    val pending = pendingResourceOperations
    if (pending != null) {
      pending += operation
    } else {
      operation()
    }
  }

  private suspend fun flushPendingResourceOperations() {
    val pending = pendingResourceOperations ?: return
    pending.forEach { it() }
    pending.clear()
  }

  private fun Resource.bundleGeneratedId(): String {
    val idPart = idElement?.idPart
    if (!idPart.isNullOrBlank()) {
      return idPart
    }
    val rawId = id.takeIf { !it.isNullOrBlank() }
    if (rawId != null) {
      return rawId.substringAfterLast("/")
    }
    return generateUuid()
  }

  private fun Coding.matchesCoding(target: Coding): Boolean {
    val systemMatches =
      system.isNullOrBlank() || target.system.isNullOrBlank() || system == target.system
    val codeMatches = code.isNullOrBlank() || target.code.isNullOrBlank() || code == target.code
    return systemMatches && codeMatches
  }
}
