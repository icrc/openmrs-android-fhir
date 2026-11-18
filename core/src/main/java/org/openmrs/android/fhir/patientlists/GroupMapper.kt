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
package org.openmrs.android.fhir.patientlists

import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.json.JSONArray
import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListRow

/**
 * A patient list as FHIR says it, and back. The contract (what each request does, what a read adds)
 * is the README of the server module `openmrs-module-fhir2extension`; `docs/dev/features/patient
 * lists.md` says what the app relies on.
 */
object GroupMapper {
  const val EXT = "http://fhir.openmrs.org/ext/group/"
  const val DESCRIPTION = EXT + "description"
  const val LOCATION = EXT + "location"
  const val LIST_TYPE = EXT + "cohort-type"
  const val DATE_CREATED = EXT + "date-created"
  const val CHANGED_BY = EXT + "changed-by"
  const val DATE_CHANGED = EXT + "date-changed"
  const val ATTRIBUTE = EXT + "attribute"

  class Parsed(val row: PatientListRow, val memberIds: List<String>?)

  fun parse(group: Group, now: Long): Parsed {
    val members =
      if (group.hasMember()) group.member.mapNotNull { idOf(it.entity) }.distinct() else null
    val location = group.getExtensionByUrl(LOCATION)?.value
    val type = group.getExtensionByUrl(LIST_TYPE)?.value
    val quantity =
      when {
        group.hasQuantity() -> group.quantity
        members != null -> members.size
        else -> 0
      }
    val row =
      PatientListRow(
        id = group.idElement.idPart,
        name = group.name.orEmpty(),
        description = group.getExtensionByUrl(DESCRIPTION)?.value?.primitiveValue(),
        typeName = (type as? Reference)?.display,
        locationId = location?.let(::uuidOf),
        locationName = (location as? Reference)?.display,
        creatorName = group.managingEntity?.display,
        dateCreated = dateOf(group.getExtensionByUrl(DATE_CREATED)),
        changedByName = (group.getExtensionByUrl(CHANGED_BY)?.value as? Reference)?.display,
        dateChanged = dateOf(group.getExtensionByUrl(DATE_CHANGED))
            ?: group.meta?.lastUpdated?.time,
        quantity = quantity,
        attributesJson = attributesJson(group),
        membersLoaded = false,
        refreshedAt = now,
      )
    return Parsed(row, members)
  }

  fun toCreate(
    change: PatientListChange,
    listType: String? = null,
    creator: Reference? = null,
  ): Group =
    Group().apply {
      id = change.listId
      active = true
      type = Group.GroupType.PERSON
      actual = true
      name = change.name
      change.description?.takeIf { it.isNotBlank() }?.let { text(DESCRIPTION, it) }
      change.locationId?.let { addExtension(Extension(LOCATION, Reference("Location/$it"))) }
      listType?.let { addExtension(Extension(LIST_TYPE, Reference("CohortType/$it"))) }
      creator?.let { managingEntity = it }
      attributes(change.attributesJson)
    }

  /**
   * `Group` to send when the patient list is edited. It has no `member`, which the server reads as
   * "leave the members as they are": the members are changed by [parameters] only.
   */
  fun toUpdate(change: PatientListChange, listType: String? = null): Group =
    Group().apply {
      id = change.listId
      active = true
      type = Group.GroupType.PERSON
      actual = true
      name = change.name
      // An empty description is sent as an empty text so that the server clears it.
      text(DESCRIPTION, change.description.orEmpty())
      change.locationId?.let { addExtension(Extension(LOCATION, Reference("Location/$it"))) }
      listType?.let { addExtension(Extension(LIST_TYPE, Reference("CohortType/$it"))) }
      attributes(change.attributesJson)
    }

  fun parameters(patientIds: Collection<String>): Parameters =
    Parameters().apply {
      patientIds.forEach { addParameter().setName("patient").setValue(Reference("Patient/$it")) }
    }

  fun memberIds(group: Group): List<String> = group.member.mapNotNull { idOf(it.entity) }.distinct()

  fun summary(patient: Patient): PatientSummary {
    val name = patient.nameFirstRep
    val full =
      listOfNotNull(name.givenAsSingleString?.takeIf { it.isNotBlank() }, name.family)
        .joinToString(" ")
        .ifBlank { name.text.orEmpty() }
    return PatientSummary(
      id = patient.idElement.idPart,
      name = full,
      gender = patient.gender?.toCode(),
      birthDate = patient.birthDateElement?.valueAsString,
      identifier =
        patient.identifier.map { it.value }.firstOrNull { !it.isNullOrBlank() && it != "unsynced" },
    )
  }

  private fun Group.text(url: String, value: String) {
    addExtension(Extension(url, StringType(value)))
  }

  private fun Group.attributes(json: String?) {
    pairs(json).forEach { (type, value) ->
      addExtension(
        Extension().apply {
          url = ATTRIBUTE
          addExtension(Extension("type", StringType(type)))
          addExtension(Extension("value", StringType(value)))
        },
      )
    }
  }

  /** The attributes as `[["type","value"], ...]`, an empty value meaning "remove". */
  fun attributesJson(values: Map<String, String>): String? {
    if (values.isEmpty()) return null
    val array = JSONArray()
    values.forEach { (type, value) -> array.put(JSONArray().put(type).put(value)) }
    return array.toString()
  }

  fun pairs(json: String?): List<Pair<String, String>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      val array = JSONArray(json)
      (0 until array.length()).mapNotNull { index ->
        val pair = array.optJSONArray(index) ?: return@mapNotNull null
        pair.optString(0).takeIf { it.isNotBlank() }?.let { it to pair.optString(1) }
      }
    } catch (e: org.json.JSONException) {
      emptyList()
    }
  }

  private fun idOf(reference: Reference?): String? {
    val ref = reference?.reference ?: return null
    if (!ref.startsWith("Patient/") && !ref.contains("/Patient/")) return null
    return ref.substringAfterLast("/").takeIf { it.isNotBlank() }
  }

  private fun uuidOf(value: org.hl7.fhir.r4.model.Type): String? =
    (if (value is Reference) value.reference else value.primitiveValue())
      ?.trim()
      ?.substringAfterLast("/")
      ?.takeIf { it.isNotBlank() }

  private fun dateOf(extension: Extension?): Long? =
    (extension?.value as? DateTimeType)?.value?.time

  private fun attributesJson(group: Group): String? {
    val array = JSONArray()
    group.getExtensionsByUrl(ATTRIBUTE).forEach { extension ->
      val type = extension.getExtensionByUrl("type")?.value?.primitiveValue()
      val value = extension.getExtensionByUrl("value")?.value?.primitiveValue()
      if (!type.isNullOrBlank() && !value.isNullOrBlank()) {
        array.put(JSONArray().put(type).put(value))
      }
    }
    return if (array.length() == 0) null else array.toString()
  }
}
