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
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.TimeType
import org.hl7.fhir.r4.model.Type

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
