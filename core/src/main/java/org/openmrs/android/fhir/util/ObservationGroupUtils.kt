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

import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
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
    parent.status = Observation.ObservationStatus.FINAL
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

/** Returns true if any CodeableConcept in listA shares a code with any CodeableConcept in listB. */
fun areSameValue(listA: List<Coding>?, listB: List<Coding>?): Boolean {
  if (listA.isNullOrEmpty() || listB.isNullOrEmpty()) return false

  val codesA = listA.mapNotNull { it.code }.toSet()
  val codesB = listB.mapNotNull { it.code }.toSet()

  return codesA.any { it in codesB }
}
