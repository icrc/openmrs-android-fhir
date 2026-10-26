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

import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Reference

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
    val reusable = existing?.takeUnless { it.status == Observation.ObservationStatus.CANCELLED }
    return if (reusable != null) {
      tracker.markTouched(reusable)
      ParentEnsureResult(reusable, false)
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
