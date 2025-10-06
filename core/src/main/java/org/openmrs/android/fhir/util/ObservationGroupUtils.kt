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
  private val childObservationsByKey = mutableMapOf<ChildKey, MutableList<Observation>>()

  val originalParentIds: Set<String>

  init {
    observations.forEach { registerParentIfApplicable(it) }
    observations.forEach { categorizeChild(it) }
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

  fun findExisting(
    resource: Observation,
    childInfo: ObservationChildInfo?,
    parentObservation: Observation?,
  ): Observation? {
    val parentContexts = requestedParentContexts(childInfo, parentObservation)
    resource.code.coding.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      parentContexts.forEach { parentContext ->
        val existing = childObservationsByKey[ChildKey(key, parentContext)]?.firstOrNull()
        if (existing != null) {
          consume(existing)
          return existing
        }
      }
    }
    return null
  }

  fun findAllExisting(
    codings: List<Coding>,
    childInfo: ObservationChildInfo?,
    parentObservation: Observation?,
  ): List<Observation> {
    val parentContexts = requestedParentContexts(childInfo, parentObservation)
    val results = linkedSetOf<Observation>()
    codings.forEach { coding ->
      val key = CodingKey.fromCoding(coding)
      if (!key.isValid()) return@forEach
      parentContexts.forEach { parentContext ->
        childObservationsByKey[ChildKey(key, parentContext)]?.let { results.addAll(it) }
      }
    }
    results.forEach { consume(it) }
    return results.toList()
  }

  fun remainingChildObservations(): Set<Observation> =
    childObservationsByKey.values.flatten().toSet()

  private fun registerParentIfApplicable(observation: Observation) {
    val parentCodingKey = determineParentCodingKey(observation) ?: return
    registerParent(ParentKey(parentCodingKey), observation)
  }

  private fun categorizeChild(observation: Observation) {
    if (!observation.hasCode()) {
      return
    }

    val codingKeys =
      observation.code.coding.map { CodingKey.fromCoding(it) }.filter { it.isValid() }
    if (codingKeys.isEmpty()) {
      return
    }

    val parentCodingKey = determineParentCodingKey(observation)
    if (parentCodingKey != null) {
      return
    }

    if (observation.status == Observation.ObservationStatus.CANCELLED) {
      return
    }

    val parentContexts = childParentContexts(observation)
    codingKeys.forEach { key ->
      parentContexts.forEach { parentContext ->
        childObservationsByKey
          .getOrPut(ChildKey(key, parentContext)) { mutableListOf() }
          .add(observation)
      }
    }
  }

  private fun registerParent(parentKey: ParentKey, observation: Observation) {
    parentObservationsByKey[parentKey] = observation
    observation.idPart()?.let { parentObservationsById[it] = observation }
  }

  private fun consume(observation: Observation) {
    val codingKeys =
      observation.code.coding.map { CodingKey.fromCoding(it) }.filter { it.isValid() }
    if (codingKeys.isEmpty()) {
      return
    }
    val parentContexts = childParentContexts(observation)
    codingKeys.forEach { codingKey ->
      parentContexts.forEach { parentContext ->
        val mapKey = ChildKey(codingKey, parentContext)
        val list = childObservationsByKey[mapKey]
        list?.remove(observation)
        if (list != null && list.isEmpty()) {
          childObservationsByKey.remove(mapKey)
        }
      }
    }
  }

  private fun determineParentCodingKey(observation: Observation): CodingKey? {
    if (!observation.hasCode()) {
      return null
    }
    return observation.code.coding
      .map { CodingKey.fromCoding(it) }
      .firstOrNull { parentCodingKeys.contains(it) }
  }

  private fun childParentContexts(observation: Observation): Set<ParentContext> {
    val contexts = mutableSetOf<ParentContext>()
    observation.partOf
      .mapNotNull { it.observationReferenceId() }
      .forEach { parentId ->
        contexts.add(ParentContext.ParentId(parentId))
        parentObservationsById[parentId]
          ?.let { determineParentCodingKey(it) }
          ?.let { contexts.add(ParentContext.ParentCoding(it)) }
      }
    if (contexts.isEmpty()) {
      contexts.add(ParentContext.None)
    }
    return contexts
  }

  private fun requestedParentContexts(
    childInfo: ObservationChildInfo?,
    parentObservation: Observation?,
  ): List<ParentContext> {
    val contexts = mutableListOf<ParentContext>()
    val parentId = parentObservation?.idPart()
    if (!parentId.isNullOrBlank()) {
      contexts.add(ParentContext.ParentId(parentId))
    }

    val parentCodingKey =
      childInfo?.parentCodingKey ?: parentObservation?.let { determineParentCodingKey(it) }
    if (parentCodingKey != null) {
      contexts.add(ParentContext.ParentCoding(parentCodingKey))
    }

    if (contexts.isEmpty()) {
      contexts.add(ParentContext.None)
    }

    return contexts.distinct()
  }

  private data class ChildKey(val codingKey: CodingKey, val parentContext: ParentContext)

  private sealed class ParentContext {
    object None : ParentContext()

    data class ParentId(val id: String) : ParentContext()

    data class ParentCoding(val codingKey: CodingKey) : ParentContext()
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

  data class MatchResult(
    val info: ObservationChildInfo,
    val matchedTokens: Set<String>,
  )

  val matches =
    candidateInfos.map { info ->
      val matchedTokens = observationTokens.intersect(info.expectedValueTokens)
      MatchResult(info, matchedTokens)
    }

  val positiveMatches = matches.filter { it.matchedTokens.isNotEmpty() }
  if (positiveMatches.isNotEmpty()) {
    val bestMatchSize = positiveMatches.maxOf { it.matchedTokens.size }
    return positiveMatches.first { it.matchedTokens.size == bestMatchSize }.info
  }

  val emptyExpectationMatches = matches.filter { it.info.expectedValueTokens.isEmpty() }
  if (emptyExpectationMatches.isNotEmpty()) {
    return emptyExpectationMatches.first().info
  }

  return candidateInfos.first()
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
    val questionnaireGroup: Questionnaire.QuestionnaireItemComponent,
    val responseGroup: QuestionnaireResponse.QuestionnaireResponseItemComponent?,
  )

  val groupStack = mutableListOf<GroupContext>()
  val childInfos = mutableListOf<ObservationChildInfo>()

  fun traverse(
    item: Questionnaire.QuestionnaireItemComponent,
    responseCandidates: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>,
  ) {
    val currentContext = groupStack.lastOrNull()
    val responseItem =
      responseCandidates.firstOrNull { it.linkId == item.linkId }
        ?: responseItemsByLinkId[item.linkId]

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
        currentContext?.let {
          collectSiblingValueTokens(
            it.questionnaireGroup,
            it.responseGroup,
            item,
            responseItemsByLinkId,
          )
        }
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

    val childResponseItems = responseItem.childResponseItems()

    if (item.type == Questionnaire.QuestionnaireItemType.GROUP) {
      groupStack.add(GroupContext(item, responseItem))
      item.item.forEach { traverse(it, childResponseItems) }
      groupStack.removeAt(groupStack.lastIndex)
    } else {
      item.item.forEach { traverse(it, childResponseItems) }
    }
  }

  questionnaire.item.forEach { traverse(it, questionnaireResponse.item) }

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
    item.answer.forEach { collectResponseItems(it.item, result) }
  }
}

private fun collectSiblingValueTokens(
  parentGroup: Questionnaire.QuestionnaireItemComponent,
  parentResponse: QuestionnaireResponse.QuestionnaireResponseItemComponent?,
  childItem: Questionnaire.QuestionnaireItemComponent,
  responseItemsByLinkId: Map<String, QuestionnaireResponse.QuestionnaireResponseItemComponent>,
): Set<String> {
  if (!childItem.linkId.isNullOrBlank() && childItem !in parentGroup.item) {
    return emptySet()
  }

  val childLinkId = childItem.linkId.orEmpty()
  if (childLinkId.isBlank()) {
    return emptySet()
  }

  val siblingResponsesByLinkId =
    parentResponse
      .childResponseItems()
      .mapNotNull { response ->
        val linkId = response.linkId
        if (linkId.isNullOrBlank()) {
          null
        } else {
          linkId to response
        }
      }
      .groupBy({ it.first }, { it.second })

  val tokens = mutableSetOf<String>()

  parentGroup.item.forEach { sibling ->
    if (sibling === childItem) {
      return@forEach
    }

    if (!sibling.isObservationValueItem()) {
      return@forEach
    }

    val valueLinkId = sibling.linkId
    if (valueLinkId.isNullOrBlank()) {
      return@forEach
    }

    if (!linkIdMatches(childLinkId, valueLinkId)) {
      return@forEach
    }

    siblingResponsesByLinkId[valueLinkId]?.forEach { tokens.addAll(it.collectAnswerTokensDeep()) }
    responseItemsByLinkId[valueLinkId]?.let { tokens.addAll(it.collectAnswerTokensDeep()) }
  }

  return tokens
}

private fun QuestionnaireResponse.QuestionnaireResponseItemComponent.collectAnswerTokensDeep():
  Set<String> {
  val tokens = mutableSetOf<String>()
  tokens.addAll(answerTokens())
  item.forEach { tokens.addAll(it.collectAnswerTokensDeep()) }
  answer.forEach { answerComponent ->
    answerComponent.item.forEach { tokens.addAll(it.collectAnswerTokensDeep()) }
  }
  return tokens
}

private fun linkIdMatches(childLinkId: String, valueLinkId: String): Boolean {
  if (childLinkId == valueLinkId) {
    return true
  }

  val delimiters = listOf("-", "_")
  return delimiters.any { delimiter ->
    childLinkId.startsWith("$valueLinkId$delimiter") ||
      valueLinkId.startsWith("$childLinkId$delimiter")
  }
}

private fun QuestionnaireResponse.QuestionnaireResponseItemComponent?.childResponseItems():
  List<QuestionnaireResponse.QuestionnaireResponseItemComponent> {
  if (this == null) {
    return emptyList()
  }

  val children = mutableListOf<QuestionnaireResponse.QuestionnaireResponseItemComponent>()
  children.addAll(item)
  answer.forEach { children.addAll(it.item) }
  return children
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
