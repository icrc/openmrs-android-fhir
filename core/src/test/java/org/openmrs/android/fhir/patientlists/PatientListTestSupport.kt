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

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Reference
import org.openmrs.android.fhir.data.database.PatientListsDao
import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListEvent
import org.openmrs.android.fhir.data.database.model.PatientListMemberChange
import org.openmrs.android.fhir.data.database.model.PatientListMemberRow
import org.openmrs.android.fhir.data.database.model.PatientListRow
import org.openmrs.android.fhir.data.database.model.UploadedPatientId

class FakePatientListDao : PatientListsDao {
  val patientListRows = MutableStateFlow<Map<String, PatientListRow>>(emptyMap())
  val memberRows = MutableStateFlow<List<PatientListMemberRow>>(emptyList())
  val changeRows = MutableStateFlow<List<PatientListChange>>(emptyList())
  val memberChangeRows = MutableStateFlow<List<PatientListMemberChange>>(emptyList())

  override fun observePatientLists(): Flow<List<PatientListRow>> =
    patientListRows.map { it.values.toList() }

  override suspend fun patientLists() = patientListRows.value.values.toList()

  override suspend fun patientList(id: String) = patientListRows.value[id]

  override fun observePatientList(id: String): Flow<PatientListRow?> =
    patientListRows.map { it[id] }

  override suspend fun upsertPatientLists(rows: List<PatientListRow>) {
    patientListRows.value = patientListRows.value + rows.associateBy { it.id }
  }

  override suspend fun deletePatientLists(ids: List<String>) {
    patientListRows.value = patientListRows.value - ids.toSet()
  }

  override suspend fun clearPatientLists() {
    patientListRows.value = emptyMap()
  }

  override fun observeMembers(listId: String): Flow<List<PatientListMemberRow>> =
    memberRows.map { list -> list.filter { it.listId == listId }.sortedBy { it.name } }

  override suspend fun members(listId: String) = memberRows.value.filter { it.listId == listId }

  override fun observeMembershipsOf(patientId: String): Flow<List<PatientListMemberRow>> =
    memberRows.map { list -> list.filter { it.patientId == patientId } }

  override suspend fun upsertMembers(rows: List<PatientListMemberRow>) {
    val keys = rows.map { it.listId to it.patientId }.toSet()
    memberRows.value = memberRows.value.filter { (it.listId to it.patientId) !in keys } + rows
  }

  override suspend fun clearMembers(listId: String) {
    memberRows.value = memberRows.value.filter { it.listId != listId }
  }

  override suspend fun removeMembers(listId: String, patientIds: List<String>) {
    memberRows.value =
      memberRows.value.filterNot { it.listId == listId && it.patientId in patientIds }
  }

  override suspend fun clearAllMembers() {
    memberRows.value = emptyList()
  }

  override fun observeChanges(): Flow<List<PatientListChange>> =
    changeRows.map { it.sortedBy { c -> c.createdAt } }

  override suspend fun changes() = changeRows.value.sortedBy { it.createdAt }

  override suspend fun allChanges() = changes()

  override suspend fun change(listId: String) = changeRows.value.firstOrNull { it.listId == listId }

  override suspend fun putChange(change: PatientListChange) {
    changeRows.value =
      changeRows.value.filterNot { it.listId == change.listId && it.owner == change.owner } + change
  }

  override suspend fun removeChange(listId: String) {
    changeRows.value = changeRows.value.filter { it.listId != listId }
  }

  override suspend fun removeChangeFor(listId: String, owner: String) {
    changeRows.value = changeRows.value.filterNot { it.listId == listId && it.owner == owner }
  }

  override suspend fun clearChanges() {
    changeRows.value = emptyList()
  }

  override suspend fun clearChangesFor(owner: String) {
    changeRows.value = changeRows.value.filter { it.owner != owner }
  }

  override suspend fun wipeChanges() {
    changeRows.value = emptyList()
  }

  override fun observeMemberChanges(): Flow<List<PatientListMemberChange>> =
    memberChangeRows.map { it.sortedBy { c -> c.createdAt } }

  override suspend fun memberChanges() = memberChangeRows.value.sortedBy { it.createdAt }

  override suspend fun allMemberChanges() = memberChanges()

  override suspend fun memberChange(listId: String, patientId: String) =
    memberChangeRows.value.firstOrNull { it.listId == listId && it.patientId == patientId }

  override suspend fun putMemberChanges(changes: List<PatientListMemberChange>) {
    val keys = changes.map { Triple(it.listId, it.patientId, it.owner) }.toSet()
    memberChangeRows.value =
      memberChangeRows.value.filter { Triple(it.listId, it.patientId, it.owner) !in keys } + changes
  }

  override suspend fun removeMemberChanges(listId: String, patientIds: List<String>) {
    memberChangeRows.value =
      memberChangeRows.value.filterNot { it.listId == listId && it.patientId in patientIds }
  }

  override suspend fun removeMemberChangesFor(
    listId: String,
    patientIds: List<String>,
    owner: String,
  ) {
    memberChangeRows.value =
      memberChangeRows.value.filterNot {
        it.listId == listId && it.patientId in patientIds && it.owner == owner
      }
  }

  override suspend fun removeMemberChangesOf(listId: String) {
    memberChangeRows.value = memberChangeRows.value.filter { it.listId != listId }
  }

  override suspend fun removeMemberChangesOfListFor(listId: String, owner: String) {
    memberChangeRows.value =
      memberChangeRows.value.filterNot { it.listId == listId && it.owner == owner }
  }

  override suspend fun clearMemberChanges() {
    memberChangeRows.value = emptyList()
  }

  override suspend fun clearMemberChangesFor(owner: String) {
    memberChangeRows.value = memberChangeRows.value.filter { it.owner != owner }
  }

  override suspend fun wipeMemberChanges() {
    memberChangeRows.value = emptyList()
  }

  override suspend fun pendingCount() = changeRows.value.size + memberChangeRows.value.size

  override fun observePendingCount(): Flow<Int> =
    combine(changeRows, memberChangeRows) { patientLists, members ->
      patientLists.size + members.size
    }

  private val uploaded = mutableMapOf<String, UploadedPatientId>()

  override suspend fun putUploadedId(row: UploadedPatientId) {
    uploaded[row.localId] = row
  }

  override suspend fun uploadedId(localId: String) = uploaded[localId]

  override suspend fun removeUploadedId(localId: String) {
    uploaded.remove(localId)
  }

  override suspend fun clearUploadedIds() = uploaded.clear()

  override suspend fun pruneUploadedIds(before: Long) {
    uploaded.values.removeAll { it.uploadedAt < before }
  }

  val eventRows = MutableStateFlow<List<PatientListEvent>>(emptyList())
  private var nextEventId = 1L

  override suspend fun addEvents(events: List<PatientListEvent>) {
    eventRows.value = eventRows.value + events.map { it.copy(id = nextEventId++) }
  }

  override fun observeEvents(listId: String): Flow<List<PatientListEvent>> =
    eventRows.map { rows -> rows.filter { it.listId == listId }.sortedByDescending { it.id } }

  override suspend fun events(listId: String) =
    eventRows.value.filter { it.listId == listId }.sortedByDescending { it.id }

  override suspend fun trimEvents(listId: String, keep: Int) {
    val drop = events(listId).drop(keep).map { it.id }.toSet()
    eventRows.value = eventRows.value.filterNot { it.id in drop }
  }

  override suspend fun removeEventsOf(listId: String) {
    eventRows.value = eventRows.value.filter { it.listId != listId }
  }

  override suspend fun clearEvents() {
    eventRows.value = emptyList()
  }
}

object NoTransaction : Transactor {
  override suspend fun <T> run(block: suspend () -> T): T = block()
}

class FakeGroupServer : GroupServer {
  val groups = mutableMapOf<String, Group>()
  val patients = mutableMapOf<String, PatientSummary>()
  var listComplete = true
  var unreachable = false

  val answers = mutableMapOf<String, MutableList<ServerGroupWrite>>()
  val requests = mutableListOf<String>()

  fun answer(key: String, vararg writes: ServerGroupWrite) {
    answers[key] = writes.toMutableList()
  }

  private fun next(key: String, general: String = key): ServerGroupWrite? {
    requests += key
    val list = answers[key] ?: answers[general] ?: return null
    return if (list.size > 1) list.removeAt(0) else list.firstOrNull()
  }

  private fun failIfOffline() {
    if (unreachable) throw IOException("offline")
  }

  override suspend fun groups(): ServerGroups {
    requests += "list"
    failIfOffline()
    return ServerGroups(groups.values.toList(), listComplete)
  }

  override suspend fun group(id: String): ServerGroupRead {
    requests += "read:$id"
    failIfOffline()
    return groups[id]?.let { ServerGroupRead.Found(it) } ?: ServerGroupRead.Gone
  }

  override suspend fun patients(ids: List<String>): List<PatientSummary> {
    requests += "patients:${ids.joinToString(",")}"
    failIfOffline()
    return ids.mapNotNull { patients[it] }
  }

  override suspend fun create(group: Group): ServerGroupWrite {
    val answer = next("create")
    if (unreachable) return ServerGroupWrite.Unreachable("offline")
    if (answer != null) return answer
    groups[group.idElement.idPart] = group
    return ServerGroupWrite.Done(group)
  }

  override suspend fun update(group: Group): ServerGroupWrite {
    val answer = next("update:${group.idElement.idPart}")
    if (unreachable) return ServerGroupWrite.Unreachable("offline")
    return answer ?: ServerGroupWrite.Done(null)
  }

  override suspend fun delete(id: String): ServerGroupWrite {
    val answer = next("delete:$id")
    if (unreachable) return ServerGroupWrite.Unreachable("offline")
    return answer ?: ServerGroupWrite.Done(null)
  }

  override suspend fun addMembers(id: String, patientIds: List<String>): ServerGroupWrite {
    val key = "add-members:$id:${patientIds.joinToString(",")}"
    val answer = next(key, "add-members:$id")
    if (unreachable) return ServerGroupWrite.Unreachable("offline")
    return answer ?: ServerGroupWrite.Done(null)
  }

  override suspend fun removeMembers(id: String, patientIds: List<String>): ServerGroupWrite {
    val key = "remove-members:$id:${patientIds.joinToString(",")}"
    val answer = next(key, "remove-members:$id")
    if (unreachable) return ServerGroupWrite.Unreachable("offline")
    return answer ?: ServerGroupWrite.Done(null)
  }
}

fun group(
  id: String,
  name: String,
  members: List<String>? = null,
  quantity: Int? = members?.size,
  location: String? = null,
  creator: String? = null,
  changed: Long? = null,
): Group =
  Group().apply {
    this.id = id
    this.name = name
    active = true
    actual = true
    type = Group.GroupType.PERSON
    quantity?.let { this.quantity = it }
    members?.forEach { addMember().entity = Reference("Patient/$it") }
    location?.let {
      addExtension(Extension(GroupMapper.LOCATION, Reference("Location/loc-$it").setDisplay(it)))
    }
    creator?.let { managingEntity = Reference().setDisplay(it) }
    changed?.let {
      addExtension(Extension(GroupMapper.DATE_CHANGED, DateTimeType(java.util.Date(it))))
    }
  }

fun patient(id: String, name: String = "Patient $id") =
  PatientSummary(id, name, "female", "1990-01-01", "ID-$id")

fun row(
  id: String,
  name: String = "Patient list $id",
  quantity: Int = 0,
  membersLoaded: Boolean = false,
  dateChanged: Long? = null,
) =
  PatientListRow(
    id = id,
    name = name,
    description = null,
    typeName = null,
    locationId = null,
    locationName = null,
    creatorName = null,
    dateCreated = null,
    changedByName = null,
    dateChanged = dateChanged,
    quantity = quantity,
    attributesJson = null,
    membersLoaded = membersLoaded,
    refreshedAt = 0,
  )

fun memberRow(listId: String, patientId: String, name: String = "Patient $patientId") =
  PatientListMemberRow(listId, patientId, name, "female", "1990-01-01", "ID-$patientId")
