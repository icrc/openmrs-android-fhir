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
package org.openmrs.android.fhir.util

import org.hl7.fhir.r4.model.BooleanType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.DecimalType
import org.hl7.fhir.r4.model.IntegerType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.TimeType
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.nowUtcDateTime

internal const val OBSERVATION_CHILD_EXTENSION_URL = "http://fhir.openmrs.org/ext/observation-child"

internal data class ObservationChildInfo(
  val childLinkId: String,
  val childCodingKeys: Set<CodingKey>,
  val parentCoding: Coding,
  val parentCodingKey: CodingKey,
  val expectedValueTokens: Set<String>,
)

internal data class ParentKey(
  val parentCodingKey: CodingKey,
)

internal data class CodingKey(val system: String?, val code: String?) {
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

internal data class ObservationGroupLookup(
  val childInfos: List<ObservationChildInfo>,
  val childInfosByCodingKey: Map<CodingKey, List<ObservationChildInfo>>,
  val parentCodingKeys: Set<CodingKey>,
) {
  fun findChildInfo(observation: Observation): ObservationChildInfo? {
    return findObservationChildInfo(observation, childInfosByCodingKey)
  }
}

internal class ParentObservationTracker {
  private val touchedParentIds = mutableSetOf<String>()
  private val parentUpdatesScheduled = mutableSetOf<String>()

  fun markTouched(parent: Observation) {
    parent.idPart()?.let { touchedParentIds.add(it) }
  }

  fun touchedIds(): Set<String> = touchedParentIds

  fun wasTouched(parentId: String): Boolean = touchedParentIds.contains(parentId)

  suspend fun markAmended(
    parent: Observation,
    update: suspend (Observation) -> Unit,
  ) {
    val parentId = parent.idPart() ?: return
    if (!parentUpdatesScheduled.add(parentId)) {
      return
    }
    parent.status = Observation.ObservationStatus.AMENDED
    parent.effective = nowUtcDateTime()
    update(parent)
  }

  suspend fun ensureActive(parent: Observation, update: suspend (Observation) -> Unit) {
    if (parent.status == Observation.ObservationStatus.CANCELLED) {
      markAmended(parent, update)
    }
  }
}

internal data class ParentEnsureResult(val parent: Observation, val isNew: Boolean)

internal class ExistingObservationIndex(
  private val parentCodingKeys: Set<CodingKey>,
  observations: List<Observation>,
) {
  private val parentObservationsByKey = mutableMapOf<ParentKey, Observation>()
  private val parentObservationsById = mutableMapOf<String, Observation>()
  private val childObservationsByKey = mutableMapOf<CodingKey, MutableList<Observation>>()

  val originalParentIds: Set<String>

  init {
    observations.forEach { categorize(it) }
    originalParentIds = parentObservationsById.keys.toSet()
  }

  fun parentObservationsById(): Map<String, Observation> = parentObservationsById

  fun parentById(parentId: String): Observation? = parentObservationsById[parentId]

  suspend fun ensureParentObservation(
    info: ObservationChildInfo,
    tracker: ParentObservationTracker,
    subjectReference: Reference,
    encounterReference: Reference,
    saveParent: suspend (Observation) -> Unit,
  ): ParentEnsureResult {
    val parentKey = ParentKey(info.parentCodingKey)
    val existing = parentObservationsByKey[parentKey]
    return if (existing != null) {
      tracker.markTouched(existing)
      ParentEnsureResult(existing, false)
    } else {
      val parent = createParentObservation(info, subjectReference, encounterReference)
      registerParent(parentKey, parent)
      tracker.markTouched(parent)
      saveParent(parent)
      ParentEnsureResult(parent, true)
    }
  }

  fun findExisting(resource: Observation): Observation? {
    resource.code.coding.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      val existing = childObservationsByKey[key]?.firstOrNull()
      if (existing != null) {
        consume(existing)
        return existing
      }
    }
    return null
  }

  fun findAllExisting(codings: List<Coding>): List<Observation> {
    val results = linkedSetOf<Observation>()
    codings.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      childObservationsByKey[key]?.let { results.addAll(it) }
    }
    results.forEach { consume(it) }
    return results.toList()
  }

  fun remainingChildObservations(): Set<Observation> =
    childObservationsByKey.values.flatten().toSet()

  private fun categorize(observation: Observation) {
    if (!observation.hasCode()) {
      return
    }

    val codingKeys =
      observation.code.coding.map { CodingKey.fromCoding(it) }.filter { it.isValid() }
    if (codingKeys.isEmpty()) {
      return
    }

    val parentKey = codingKeys.firstOrNull { parentCodingKeys.contains(it) }
    if (parentKey != null) {
      registerParent(ParentKey(parentKey), observation)
    } else if (observation.status != Observation.ObservationStatus.CANCELLED) {
      codingKeys.forEach { key ->
        childObservationsByKey.getOrPut(key) { mutableListOf() }.add(observation)
      }
    }
  }

  private fun registerParent(parentKey: ParentKey, observation: Observation) {
    parentObservationsByKey[parentKey] = observation
    observation.idPart()?.let { parentObservationsById[it] = observation }
  }

  private fun consume(observation: Observation) {
    observation.code.coding.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      val list = childObservationsByKey[key]
      list?.remove(observation)
      if (list != null && list.isEmpty()) {
        childObservationsByKey.remove(key)
      }
    }
  }
}

/*
 *
 * Returns the ObservationChildInfo definition that corresponds to the supplied Observation.
 * Candidate definitions are gathered by matching each coding on the Observation against the
 * precomputed child-info map, then value-comparison tokens are used to disambiguate when multiple
 * candidates share the same coding. Null is returned when no child definition matches.
 */
internal fun findObservationChildInfo(
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

/**
 * Walks the questionnaire (and its response) to enumerate every observation-child item. For each
 * child question it captures the question’s link ID, all codings found in the response or initial
 * value, the parent observation coding advertised by the extension, and any comparison tokens
 * derived from sibling value items in the same group.
 */
internal fun collectObservationChildInfos(
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
      val parentCoding = item.observationParentCoding() ?: return
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

private fun Questionnaire.QuestionnaireItemComponent.observationParentCoding(): Coding? {
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

internal fun buildObservationGroupLookup(
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
): ObservationGroupLookup {
  val childInfos = collectObservationChildInfos(questionnaire, questionnaireResponse)
  val childInfosByCodingKey =
    childInfos
      .flatMap { info -> info.childCodingKeys.map { codingKey -> codingKey to info } }
      .groupBy({ it.first }, { it.second })
  val parentCodingKeys = childInfos.map { it.parentCodingKey }.toSet()
  return ObservationGroupLookup(childInfos, childInfosByCodingKey, parentCodingKeys)
}

internal suspend fun ParentEnsureResult?.markExistingParentAmended(
  tracker: ParentObservationTracker,
  update: suspend (Observation) -> Unit,
) {
  if (this == null || isNew) {
    return
  }
  tracker.markAmended(parent, update)
}

internal suspend fun ParentEnsureResult?.handleUnchangedChild(
  tracker: ParentObservationTracker,
  update: suspend (Observation) -> Unit,
) {
  if (this == null || isNew) {
    return
  }
  tracker.ensureActive(parent, update)
}

internal fun createParentObservation(
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

internal fun Observation.idPart(): String? {
  val idPart = idElement?.idPart
  if (!idPart.isNullOrBlank()) {
    return idPart
  }
  return id.takeIf { !it.isNullOrBlank() }
}

internal fun Observation.updateParentReference(parent: Observation?): Boolean {
  val desiredParentId = parent?.idPart()
  val desiredReference = desiredParentId?.let { "Observation/$it" }

  val preservedReferences = mutableListOf<Reference>()
  var hasDesiredReference = false
  var changed = false

  partOf.forEach { existingReference ->
    val existingParentId = existingReference.observationReferenceId()
    if (existingParentId == null) {
      preservedReferences.add(existingReference)
      return@forEach
    }

    if (desiredParentId != null && existingParentId == desiredParentId && !hasDesiredReference) {
      preservedReferences.add(existingReference)
      hasDesiredReference = true
    } else {
      changed = true
    }
  }

  if (desiredReference != null && !hasDesiredReference) {
    preservedReferences.add(Reference(desiredReference))
    changed = true
  }

  if (desiredReference == null && partOf.any { it.observationReferenceId() != null }) {
    changed = true
  }

  if (changed) {
    partOf.clear()
    partOf.addAll(preservedReferences)
  }

  return changed
}

internal fun Reference.observationReferenceId(): String? {
  val element = referenceElement
  val resourceType = element.resourceType
  if (!resourceType.isNullOrBlank() && resourceType != ResourceType.Observation.name) {
    return null
  }
  return element.idPart.takeIf { !it.isNullOrBlank() }
}
