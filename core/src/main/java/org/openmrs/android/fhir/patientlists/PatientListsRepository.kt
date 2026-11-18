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

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hl7.fhir.r4.model.Group
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.PatientListsDao
import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListChangeKind
import org.openmrs.android.fhir.data.database.model.PatientListEvent
import org.openmrs.android.fhir.data.database.model.PatientListEventAction
import org.openmrs.android.fhir.data.database.model.PatientListMemberAction
import org.openmrs.android.fhir.data.database.model.PatientListMemberChange
import org.openmrs.android.fhir.data.database.model.PatientListMemberRow
import org.openmrs.android.fhir.data.database.model.PatientListRow
import org.openmrs.android.fhir.data.database.model.UploadedPatientId
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerCapabilities
import org.openmrs.android.fhir.data.remote.ServerFeatures
import org.openmrs.android.fhir.forms.savedLocation
import org.openmrs.android.fhir.unidentified.UnidentifiedStore
import timber.log.Timber

interface Transactor {
  suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactor(private val database: RoomDatabase) : Transactor {
  override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}

/**
 * The patient lists of the device, offline first.
 *
 * What the user does (create, rename, delete a patient list; add or remove a patient) is written at
 * once to `patient_list_change` and `patient_list_member_change` and shown with the server's last
 * word (see [PatientListOverlay]); it is sent later, by [send], when there is a connection (a sync,
 * live mode, or the user's "send now"). The server's word is read by [refreshList] and
 * [refreshMembers] and kept in `patient_list` and `patient_list_member`.
 *
 * Sending is made safe to repeat: a create carries the id the device chose and the server answers
 * 409 when it has it; members are added and removed by what changes (`$add-members`,
 * `$remove-members`), never by sending the whole list, so two devices do not undo each other.
 */
@Singleton
class PatientListsRepository
internal constructor(
  private val dao: PatientListsDao,
  private val server: GroupServer,
  private val transactor: Transactor,
  private val clock: () -> Long,
  private val newId: () -> String,
  private val toIdentify: suspend () -> Set<String>,
  private val user: ActiveUser = ActiveUser { "" },
  private val supports: () -> ServerFeatures = { ServerFeatures() },
  private val onRefused: (Int, String, Boolean) -> Unit = { _, _, _ -> },
  /** Without a session (a guest) nothing is asked of the server or sent: the changes wait. */
  private val signedIn: () -> Boolean = { true },
  /** The cohort type of the lists the app creates (the one that limits the download), if any. */
  private val listType: () -> String? = { null },
  /** The location chosen on this device (id and name), if one is. */
  private val savedLocation: suspend () -> Pair<String, String?>? = { null },
  /** A list is not sent before it has a location: the server needs it with the list. */
  private val needsLocation: Boolean = false,
) {
  @Inject
  constructor(
    context: Context,
    database: AppDatabase,
    apiManager: ApiManager,
    unidentified: UnidentifiedStore,
    capabilities: ServerCapabilities,
  ) : this(
    UserScopedPatientLists(database.patientListDao(), ActiveUser.of(context)),
    apiManager,
    RoomTransactor(database),
    System::currentTimeMillis,
    { UUID.randomUUID().toString() },
    { unidentified.ids() },
    ActiveUser.of(context),
    capabilities::current,
    capabilities::learnRefusal,
    { !GuestSession.isActive(context) },
    { AppConfig.get(context).sync.cohortType.trim().takeIf { it.isNotEmpty() } },
    {
      val saved = context.savedLocation()
      saved.id?.takeIf { it.isNotBlank() }?.let { it to saved.name }
    },
    needsLocation = true,
  )

  private val gate = Mutex()

  val items: Flow<List<PatientListEntry>> =
    combine(dao.observePatientLists(), dao.observeChanges(), dao.observeMemberChanges()) {
      rows,
      changes,
      memberChanges,
      ->
      PatientListOverlay.items(rows, changes, memberChanges, toIdentify())
    }

  fun item(id: String): Flow<PatientListEntry?> =
    items.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

  suspend fun nameOf(id: String): String? = item(id).first()?.name

  fun members(listId: String): Flow<List<PatientListMember>> =
    combine(dao.observeMembers(listId), dao.observeMemberChanges()) { rows, changes ->
      PatientListOverlay.members(rows, changes.filter { it.listId == listId }, toIdentify())
    }

  fun membershipsOf(patientId: String): Flow<List<PatientMembership>> =
    combine(items, dao.observeMembershipsOf(patientId), dao.observeMemberChanges()) {
      lists,
      rows,
      changes,
      ->
      val inRows = rows.mapTo(HashSet()) { it.listId }
      val mine = changes.filter { it.patientId == patientId }.associateBy { it.listId }
      lists
        .filter { it.state != PatientListState.DELETING }
        .map { list ->
          val change = mine[list.id]
          when {
            change?.action == PatientListMemberAction.ADD ->
              PatientMembership(list, Membership.IN, MemberPending.ADD)
            change?.action == PatientListMemberAction.REMOVE ->
              PatientMembership(list, Membership.OUT, MemberPending.REMOVE)
            list.id in inRows -> PatientMembership(list, Membership.IN, null)
            list.membersLoaded || list.state == PatientListState.NEW ->
              PatientMembership(list, Membership.OUT, null)
            else -> PatientMembership(list, Membership.UNKNOWN, null)
          }
        }
    }

  suspend fun memberIds(listId: String): Set<String> {
    val ids = dao.members(listId).mapTo(HashSet()) { it.patientId }
    dao
      .memberChanges()
      .filter { it.listId == listId }
      .forEach {
        if (it.action == PatientListMemberAction.ADD) ids += it.patientId else ids -= it.patientId
      }
    return ids
  }

  /**
   * Changes waiting to be sent (patient lists and members); an add held for an identification is
   * not one.
   */
  val pendingCount: Flow<Int> =
    combine(dao.observePendingCount(), dao.observeMemberChanges()) { count, changes ->
      val held = toIdentify()
      count - changes.count { PatientListOverlay.run { it.isHeld(held) } }
    }

  suspend fun pendingNow(): Int {
    val held = toIdentify()
    return dao.pendingCount() -
      dao.memberChanges().count { PatientListOverlay.run { it.isHeld(held) } }
  }

  suspend fun create(
    name: String,
    description: String?,
    locationId: String?,
    locationName: String?,
  ): PatientListEdit =
    transactor.run {
      val clean = name.trim()
      when {
        clean.isEmpty() -> PatientListEdit.NameMissing
        nameTaken(clean, exceptId = null) -> PatientListEdit.NameTaken
        else -> {
          val id = newId()
          dao.putChange(
            PatientListChange(
              listId = id,
              kind = PatientListChangeKind.CREATE,
              name = clean,
              description = description.cleaned(),
              locationId = locationId,
              locationName = locationName,
              createdAt = clock(),
              error = null,
            ),
          )
          record(id, PatientListEventAction.LIST_CREATED, detail = clean)
          PatientListEdit.Saved(id)
        }
      }
    }

  suspend fun edit(id: String, name: String, description: String?): PatientListEdit =
    transactor.run {
      val clean = name.trim()
      val change = dao.change(id)
      val row = dao.patientList(id)
      when {
        clean.isEmpty() -> PatientListEdit.NameMissing
        change?.kind == PatientListChangeKind.DELETE -> PatientListEdit.NotFound
        nameTaken(clean, exceptId = id) -> PatientListEdit.NameTaken
        change?.kind == PatientListChangeKind.CREATE -> {
          dao.putChange(
            change.copy(name = clean, description = description.cleaned(), error = null),
          )
          if (clean != change.name) record(id, PatientListEventAction.LIST_RENAMED, detail = clean)
          PatientListEdit.Saved(id)
        }
        row == null -> PatientListEdit.NotFound
        clean == row.name && description.cleaned() == row.description.cleaned() -> {
          if (change != null) dao.removeChange(id)
          PatientListEdit.Saved(id)
        }
        else -> {
          dao.putChange(
            PatientListChange(
              listId = id,
              kind = PatientListChangeKind.UPDATE,
              name = clean,
              description = description.cleaned(),
              locationId = null,
              locationName = null,
              createdAt = change?.createdAt ?: clock(),
              error = null,
            ),
          )
          if (clean != row.name) record(id, PatientListEventAction.LIST_RENAMED, detail = clean)
          PatientListEdit.Saved(id)
        }
      }
    }

  suspend fun delete(id: String) {
    transactor.run {
      val change = dao.change(id)
      val row = dao.patientList(id)
      if (change?.kind == PatientListChangeKind.CREATE) {
        record(id, PatientListEventAction.LIST_DELETED, detail = change.name)
        dao.removeChange(id)
        dao.removeMemberChangesOf(id)
      } else if (row != null) {
        record(id, PatientListEventAction.LIST_DELETED, detail = row.name)
        dao.removeMemberChangesOf(id)
        dao.putChange(
          PatientListChange(
            listId = id,
            kind = PatientListChangeKind.DELETE,
            name = row.name,
            description = null,
            locationId = null,
            locationName = null,
            createdAt = clock(),
            error = null,
          ),
        )
      }
    }
  }

  suspend fun undoDelete(id: String) {
    if (dao.change(id)?.kind == PatientListChangeKind.DELETE) dao.removeChange(id)
  }

  fun history(listId: String): Flow<List<PatientListEvent>> = dao.observeEvents(listId)

  private suspend fun record(
    listId: String,
    action: String,
    patient: PatientSummary? = null,
    detail: String? = null,
  ) {
    dao.addEvents(
      listOf(
        PatientListEvent(
          listId = listId,
          action = action,
          patientId = patient?.id,
          patientName = patient?.name,
          detail = detail,
          user = user.name(),
          at = clock(),
        ),
      ),
    )
    dao.trimEvents(listId, HISTORY_KEPT)
  }

  suspend fun addMembers(
    listId: String,
    patients: List<PatientSummary>,
    heldForUpload: Set<String> = emptySet(),
  ) {
    transactor.run {
      if (dao.change(listId)?.kind == PatientListChangeKind.DELETE) return@run
      val onServer = dao.members(listId).mapTo(HashSet()) { it.patientId }
      val queued = dao.memberChanges().filter { it.listId == listId }.associateBy { it.patientId }
      val cancelled = mutableListOf<String>()
      val added = mutableListOf<PatientListMemberChange>()
      for (patient in patients) {
        val earlier = queued[patient.id]
        when {
          earlier?.action == PatientListMemberAction.REMOVE -> cancelled += patient.id
          earlier != null -> Unit
          patient.id in onServer -> Unit
          else ->
            added +=
              PatientListMemberChange(
                listId = listId,
                patientId = patient.id,
                action = PatientListMemberAction.ADD,
                name = patient.name,
                gender = patient.gender,
                birthDate = patient.birthDate,
                identifier = patient.identifier,
                createdAt = clock(),
                error = null,
                heldForUpload = patient.id in heldForUpload,
              )
        }
      }
      if (cancelled.isNotEmpty()) dao.removeMemberChanges(listId, cancelled)
      if (added.isNotEmpty()) dao.putMemberChanges(added)
      val recorded =
        patients.filter { p -> p.id in cancelled || added.any { it.patientId == p.id } }
      recorded.forEach { record(listId, PatientListEventAction.MEMBER_ADDED, it) }
    }
  }

  suspend fun removeMember(listId: String, patientId: String) {
    transactor.run {
      if (dao.change(listId)?.kind == PatientListChangeKind.DELETE) return@run
      val queued = dao.memberChange(listId, patientId)
      val row = dao.members(listId).firstOrNull { it.patientId == patientId }
      val summary =
        queued?.let {
          PatientSummary(it.patientId, it.name, it.gender, it.birthDate, it.identifier)
        }
          ?: row?.let {
            PatientSummary(it.patientId, it.name, it.gender, it.birthDate, it.identifier)
          }
      when {
        queued?.action == PatientListMemberAction.ADD -> {
          dao.removeMemberChanges(listId, listOf(patientId))
          record(listId, PatientListEventAction.MEMBER_REMOVED, summary)
        }
        queued != null || row == null -> Unit
        else -> {
          record(listId, PatientListEventAction.MEMBER_REMOVED, summary)
          dao.putMemberChanges(
            listOf(
              PatientListMemberChange(
                listId = listId,
                patientId = patientId,
                action = PatientListMemberAction.REMOVE,
                name = row.name,
                gender = row.gender,
                birthDate = row.birthDate,
                identifier = row.identifier,
                createdAt = clock(),
                error = null,
              ),
            ),
          )
        }
      }
    }
  }

  /**
   * The adds that wait for the upload of a patient registered here: for a patient that has been
   * uploaded the add now names them under the id they have, and is sent like any other; for one
   * that cannot be found any more it is dropped.
   */
  suspend fun releaseUploaded(finder: UploadedPatients) {
    for (change in dao.allMemberChanges().filter { it.heldForUpload }) {
      when (val found = finder.find(change)) {
        Uploaded.Waiting -> Unit
        Uploaded.Missing -> forgetMemberChange(change)
        Uploaded.Ambiguous ->
          if (change.error != AMBIGUOUS_UPLOAD) {
            dao.putMemberChanges(listOf(change.copy(error = AMBIGUOUS_UPLOAD)))
          }
        is Uploaded.Now ->
          transactor.run {
            forgetMemberChange(change)
            val real = found.patient
            val already =
              dao.members(change.listId).any { it.patientId == real.id } ||
                dao.allMemberChanges().any {
                  it.listId == change.listId && it.patientId == real.id && it.owner == change.owner
                }
            if (!already) {
              dao.putMemberChanges(
                listOf(
                  change.copy(
                    patientId = real.id,
                    name = real.name,
                    gender = real.gender,
                    birthDate = real.birthDate,
                    identifier = real.identifier,
                    heldForUpload = false,
                    error = null,
                  ),
                ),
              )
            }
          }
      }
    }
    dao.pruneUploadedIds(clock() - UPLOADED_IDS_KEPT_MS)
  }

  /**
   * The patient to identify [placeholderId] was identified as [real]: what was added to lists for
   * them is added for the real patient, and sent like any other change. Where the real patient is
   * already in the list, or already queued, there is nothing to do. [heldForUpload]: the patient is
   * one registered on this device and not uploaded (the records of the patient to identify were
   * linked to them): what was added waits for their upload, like any add for such a patient.
   */
  /**
   * What a guest changed in the patient lists (kept without an owner) becomes the changes of the
   * user who signed in, to be sent with their session. Where the user already has a change for the
   * same list or patient, theirs is kept.
   */
  suspend fun adoptGuestChanges() {
    val owner = user.name()
    if (owner.isEmpty()) return
    transactor.run {
      val own = dao.allChanges().filter { it.owner == owner }.mapTo(HashSet()) { it.listId }
      dao
        .allChanges()
        .filter { it.owner.isEmpty() }
        .forEach { change ->
          dao.removeChangeFor(change.listId, "")
          if (change.listId !in own) dao.putChange(change.copy(owner = owner))
        }
      val ownMembers =
        dao
          .allMemberChanges()
          .filter { it.owner == owner }
          .mapTo(HashSet()) { it.listId to it.patientId }
      dao
        .allMemberChanges()
        .filter { it.owner.isEmpty() }
        .forEach { change ->
          dao.removeMemberChangesFor(change.listId, listOf(change.patientId), "")
          if ((change.listId to change.patientId) !in ownMembers) {
            dao.putMemberChanges(listOf(change.copy(owner = owner)))
          }
        }
    }
  }

  /** The lists created before a location was chosen get the one chosen now, if there is one. */
  suspend fun assignSavedLocation() {
    savedLocation()?.let { (id, name) -> completeLocation(id, name) }
  }

  /** The lists created before a location was chosen get it, so the server has every field. */
  suspend fun completeLocation(locationId: String, locationName: String?) {
    dao
      .allChanges()
      .filter { it.kind == PatientListChangeKind.CREATE && it.locationId == null }
      .forEach { dao.putChange(it.copy(locationId = locationId, locationName = locationName)) }
  }

  suspend fun waitingByUser(): Map<String, Int> =
    (dao.allChanges().map { it.owner } + dao.allMemberChanges().map { it.owner })
      .groupingBy { it }
      .eachCount()

  fun uploadedPatientFinder(fhirEngine: com.google.android.fhir.FhirEngine) =
    UploadedPatientFinder(fhirEngine, dao)

  /**
   * The upload gave these patients new ids ((id on the device, id on the server) pairs): noted for
   * the patients something waits for, so the add follows them exactly ([releaseUploaded]).
   */
  suspend fun noteUploaded(pairs: List<Pair<String, String>>) {
    if (pairs.isEmpty()) return
    val waiting =
      dao.allMemberChanges().filter { it.heldForUpload }.mapTo(HashSet()) { it.patientId }
    pairs
      .filter { (local, _) -> local in waiting }
      .forEach { (local, server) -> dao.putUploadedId(UploadedPatientId(local, server, clock())) }
  }

  private suspend fun forgetMemberChange(change: PatientListMemberChange) =
    dao.removeMemberChangesFor(change.listId, listOf(change.patientId), change.owner)

  suspend fun replacePlaceholder(
    placeholderId: String,
    real: PatientSummary,
    heldForUpload: Boolean = false,
  ) {
    transactor.run {
      val held = dao.allMemberChanges().filter { it.patientId == placeholderId }
      for (change in held) {
        forgetMemberChange(change)
        val already =
          dao.members(change.listId).any { it.patientId == real.id } ||
            dao.allMemberChanges().any {
              it.listId == change.listId && it.patientId == real.id && it.owner == change.owner
            }
        if (change.action == PatientListMemberAction.ADD && !already) {
          dao.putMemberChanges(
            listOf(
              change.copy(
                patientId = real.id,
                name = real.name,
                gender = real.gender,
                birthDate = real.birthDate,
                identifier = real.identifier,
                error = null,
                heldForUpload = heldForUpload,
              ),
            ),
          )
        }
      }
    }
  }

  suspend fun undoMemberChange(listId: String, patientId: String) {
    dao.removeMemberChanges(listId, listOf(patientId))
  }

  suspend fun discardChanges(listId: String) {
    transactor.run {
      dao.removeChange(listId)
      dao.removeMemberChangesOf(listId)
    }
  }

  suspend fun discardRefused() {
    transactor.run {
      val refused = dao.changes().filter { it.error != null }
      refused.forEach {
        dao.removeChange(it.listId)
        if (it.kind == PatientListChangeKind.CREATE) dao.removeMemberChangesOf(it.listId)
      }
      val members = dao.memberChanges().filter { it.error != null }
      members
        .groupBy { it.listId }
        .forEach { (listId, list) -> dao.removeMemberChanges(listId, list.map { it.patientId }) }
    }
  }

  suspend fun clearAll() {
    transactor.run {
      dao.clearPatientLists()
      dao.clearAllMembers()
      dao.wipeChanges()
      dao.wipeMemberChanges()
      dao.clearEvents()
      dao.clearUploadedIds()
    }
  }

  suspend fun clearPatientData() {
    transactor.run {
      dao.clearAllMembers()
      dao.clearMemberChanges()
      dao.upsertPatientLists(dao.patientLists().map { it.copy(membersLoaded = false) })
    }
  }

  private suspend fun nameTaken(name: String, exceptId: String?): Boolean =
    PatientListOverlay.items(dao.patientLists(), dao.changes(), emptyList()).any {
      it.id != exceptId && it.state != PatientListState.DELETING && it.name.equals(name, true)
    }

  private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

  suspend fun refreshList(): RefreshResult =
    gate.withLock {
      if (!signedIn()) return@withLock RefreshResult.DONE
      val list =
        try {
          server.groups()
        } catch (e: IOException) {
          return@withLock RefreshResult.UNREACHABLE
        }
      keep(list.groups, list.complete)
      RefreshResult.DONE
    }

  /**
   * Keeps Groups that were just read for another purpose (the screen of the patient lists reads
   * them in full for the downloads), so that they need not be asked for again. [complete] is false
   * when they are only those of one location: nothing is removed then.
   */
  suspend fun store(groups: List<Group>, complete: Boolean) {
    gate.withLock { keep(groups, complete) }
  }

  private suspend fun keep(groups: List<Group>, complete: Boolean) {
    val now = clock()
    transactor.run {
      val cached = dao.patientLists().associateBy { it.id }
      val rows =
        groups
          .filter { it.hasIdElement() && it.hasName() }
          .map { GroupMapper.parse(it, now).row }
          .map { fresh ->
            val old = cached[fresh.id]
            // The members are read again when the patient list changed since they were.
            val unchanged =
              old != null && old.quantity == fresh.quantity && old.dateChanged == fresh.dateChanged
            fresh.copy(membersLoaded = old?.membersLoaded == true && unchanged)
          }
      dao.upsertPatientLists(rows)
      if (complete) {
        val waiting =
          dao.allChanges().mapTo(HashSet()) { it.listId } + dao.allMemberChanges().map { it.listId }
        val gone = cached.keys - rows.mapTo(HashSet()) { it.id } - waiting
        if (gone.isNotEmpty()) {
          dao.deletePatientLists(gone.toList())
          gone.forEach { dao.clearMembers(it) }
        }
      }
    }
  }

  suspend fun refreshMembers(id: String): RefreshResult =
    gate.withLock {
      if (!signedIn()) return@withLock RefreshResult.DONE
      if (dao.change(id)?.kind == PatientListChangeKind.CREATE) return@withLock RefreshResult.DONE
      val read =
        try {
          server.group(id)
        } catch (e: IOException) {
          return@withLock RefreshResult.UNREACHABLE
        }
      when (read) {
        ServerGroupRead.Gone -> {
          transactor.run { forget(id) }
          RefreshResult.GONE
        }
        is ServerGroupRead.Found -> {
          val parsed = GroupMapper.parse(read.group, clock())
          val ids = parsed.memberIds.orEmpty()
          val cached = dao.members(id).associateBy { it.patientId }
          val fetched =
            try {
              ids.filter { it !in cached }.chunked(PATIENT_BATCH).flatMap { server.patients(it) }
            } catch (e: IOException) {
              return@withLock RefreshResult.UNREACHABLE
            }
          val byId = fetched.associateBy { it.id }
          val rows =
            ids.map { patientId ->
              cached[patientId]
                ?: byId[patientId]?.let {
                  PatientListMemberRow(
                    id,
                    patientId,
                    it.name,
                    it.gender,
                    it.birthDate,
                    it.identifier,
                  )
                }
                  ?: PatientListMemberRow(id, patientId, "", null, null, null)
            }
          transactor.run {
            dao.replaceMembers(id, rows)
            dao.upsertPatientLists(
              listOf(parsed.row.copy(quantity = ids.size, membersLoaded = true)),
            )
          }
          RefreshResult.DONE
        }
      }
    }

  private suspend fun forget(id: String) {
    dao.deletePatientLists(listOf(id))
    dao.clearMembers(id)
    dao.removeChange(id)
    dao.removeMemberChangesOf(id)
    dao.removeEventsOf(id)
  }

  /**
   * Sends what waits, patient lists first and then their members, in the order the user did it. A
   * change the server refused keeps its reason and is not sent again unless [retryRefused]. Stops
   * at the first answer that says the server cannot be reached; what is left stays queued.
   */
  suspend fun send(retryRefused: Boolean = false): SendResult =
    gate.withLock {
      if (!signedIn()) return@withLock SendResult()
      if (retryRefused) clearReasons()
      assignSavedLocation()
      var total = Tally()
      val blocked = HashSet<String>()
      val features = supports()
      for (change in dao.changes()) {
        val withoutLocation =
          needsLocation && change.kind == PatientListChangeKind.CREATE && change.locationId == null
        if (change.error != null || !features.editLists || withoutLocation) {
          blocked += change.listId
          continue
        }
        val tally = sendChange(change)
        total += tally
        if (tally.blocked) blocked += change.listId
        if (tally.unreachable) return@withLock total.result()
      }
      // An add for a patient to identify waits for the identification: the server does not know
      // them.
      val held = toIdentify()
      val byPatientList =
        dao
          .memberChanges()
          .filter { it.error == null && !PatientListOverlay.run { it.isHeld(held) } }
          .groupBy { it.listId }
      patientLists@ for ((listId, list) in byPatientList) {
        if (listId in blocked || !features.changeMembers) continue
        for (action in listOf(PatientListMemberAction.ADD, PatientListMemberAction.REMOVE)) {
          for (chunk in list.filter { it.action == action }.chunked(MEMBER_BATCH)) {
            val tally = sendMembers(listId, action, chunk)
            total += tally
            if (tally.unreachable) return@withLock total.result()
            if (tally.dropped > 0) continue@patientLists
          }
        }
      }
      total.result()
    }

  private suspend fun clearReasons() {
    transactor.run {
      dao.changes().filter { it.error != null }.forEach { dao.putChange(it.copy(error = null)) }
      val members = dao.memberChanges().filter { it.error != null }
      if (members.isNotEmpty()) dao.putMemberChanges(members.map { it.copy(error = null) })
    }
  }

  private suspend fun sendChange(change: PatientListChange): Tally {
    val id = change.listId
    return when (change.kind) {
      PatientListChangeKind.CREATE ->
        when (
          val answer =
            guarded(what = "create $id") { server.create(GroupMapper.toCreate(change, listType())) }
        ) {
          is ServerGroupWrite.Done -> createdOnServer(change, answer.group)
          // The server has it already (an earlier send whose answer was lost).
          is ServerGroupWrite.Refused ->
            if (answer.code == 409) {
              transactor.run {
                dao.removeChange(id)
                dao.upsertPatientLists(listOf(rowAfterCreate(change, null)))
              }
              Tally(sent = 1)
            } else {
              refuse(change, answer)
            }
          is ServerGroupWrite.Unreachable -> Tally(unreachable = true)
        }
      PatientListChangeKind.UPDATE ->
        when (
          val answer = guarded(what = "update $id") { server.update(GroupMapper.toUpdate(change)) }
        ) {
          is ServerGroupWrite.Done -> updatedOnServer(change)
          is ServerGroupWrite.Refused -> if (answer.gone) drop(id) else refuse(change, answer)
          is ServerGroupWrite.Unreachable -> Tally(unreachable = true)
        }
      PatientListChangeKind.DELETE ->
        when (val answer = guarded(what = "delete $id") { server.delete(id) }) {
          is ServerGroupWrite.Done -> {
            transactor.run { forget(id) }
            Tally(sent = 1)
          }
          is ServerGroupWrite.Refused ->
            if (answer.gone) {
              transactor.run { forget(id) }
              Tally(sent = 1)
            } else {
              refuse(change, answer)
            }
          is ServerGroupWrite.Unreachable -> Tally(unreachable = true)
        }
      else -> Tally()
    }
  }

  private suspend fun sendMembers(
    listId: String,
    action: String,
    chunk: List<PatientListMemberChange>,
  ): Tally {
    val ids = chunk.map { it.patientId }
    val answer =
      guarded(members = true, what = "${action.lowercase()}-members $listId") {
        if (action == PatientListMemberAction.ADD) {
          server.addMembers(listId, ids)
        } else {
          server.removeMembers(listId, ids)
        }
      }
    return when (answer) {
      is ServerGroupWrite.Done -> {
        transactor.run {
          dao.removeMemberChanges(listId, ids)
          if (action == PatientListMemberAction.ADD) {
            dao.upsertMembers(
              chunk.map {
                PatientListMemberRow(
                  listId,
                  it.patientId,
                  it.name,
                  it.gender,
                  it.birthDate,
                  it.identifier,
                )
              },
            )
          } else {
            dao.removeMembers(listId, ids)
          }
          dao.patientList(listId)?.let { row ->
            val count = answer.group?.let { GroupMapper.memberIds(it).size }
            val delta = if (action == PatientListMemberAction.ADD) ids.size else -ids.size
            dao.upsertPatientLists(
              listOf(row.copy(quantity = count ?: (row.quantity + delta).coerceAtLeast(0))),
            )
          }
        }
        Tally(sent = chunk.size)
      }
      is ServerGroupWrite.Refused ->
        when {
          answer.gone -> drop(listId)
          // One patient the server does not accept must not hold back the others.
          chunk.size > 1 && (answer.code == 422 || answer.code == 400) ->
            chunk.fold(Tally()) { sum, single ->
              if (sum.unreachable || sum.dropped > 0) {
                sum
              } else {
                sum + sendMembers(listId, action, listOf(single))
              }
            }
          else -> {
            dao.putMemberChanges(chunk.map { it.copy(error = answer.reason) })
            Tally(refused = chunk.size)
          }
        }
      is ServerGroupWrite.Unreachable -> Tally(unreachable = true)
    }
  }

  /** [what] names the request in the log ("create <id>") with what the server answered. */
  private suspend fun guarded(
    members: Boolean = false,
    what: String,
    call: suspend () -> ServerGroupWrite,
  ): ServerGroupWrite =
    try {
      call().also {
        when (it) {
          is ServerGroupWrite.Done -> note(what, AuditOutcome.SUCCESS, "answer" to "accepted")
          is ServerGroupWrite.Refused -> {
            onRefused(it.code, it.reason, members)
            note(
              what,
              AuditOutcome.FAILURE,
              "answer" to "refused",
              "code" to it.code,
              "reason" to it.reason,
            )
          }
          is ServerGroupWrite.Unreachable ->
            note(what, AuditOutcome.FAILURE, "answer" to "unreachable", "reason" to it.reason)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      note(what, AuditOutcome.FAILURE, "answer" to "unreachable", "reason" to e.message)
      ServerGroupWrite.Unreachable(e.message.orEmpty())
    }

  private fun note(what: String, outcome: AuditOutcome, vararg details: Pair<String, Any?>) {
    Timber.log(
      if (outcome == AuditOutcome.SUCCESS) android.util.Log.INFO else android.util.Log.WARN,
      "Patient list %s: %s",
      what,
      details.joinToString { "${it.first}=${it.second}" },
    )
    AuditLog.record(
      AuditAction.PATIENT_LIST_SENT,
      outcome,
      "what" to what,
      *details,
    )
  }

  private suspend fun readBack(id: String): ServerGroupRead? =
    try {
      server.group(id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      note("read back $id", AuditOutcome.FAILURE, "answer" to "unreachable", "reason" to e.message)
      null
    }

  /**
   * A "yes" is not proof (a gateway's page, a server without the patient list module): the list is
   * read back, and one that is not there stays queued with a reason. What the server did not keep
   * is logged.
   */
  private suspend fun createdOnServer(change: PatientListChange, answered: Group?): Tally {
    val id = change.listId
    val serverId = answered?.takeIf { it.hasIdElement() }?.idElement?.idPart ?: id
    val read = readBack(serverId) ?: return Tally(unreachable = true)
    val found = (read as? ServerGroupRead.Found)?.group
    if (found == null) {
      note("create $id", AuditOutcome.FAILURE, "answer" to "not_there_after_the_yes")
      return refuse(change, ServerGroupWrite.Refused(0, NOT_CREATED))
    }
    val lost = notKept(change.name, change.description, change.locationId, listType(), found)
    if (lost.isNotEmpty()) {
      note("create $id", AuditOutcome.FAILURE, "answer" to "created_without", "fields" to lost)
    }
    transactor.run {
      dao.removeChange(id)
      val row = rowAfterCreate(change, found)
      dao.upsertPatientLists(listOf(row))
      // A server that numbers the patient list itself: what waits for it follows the id it gave.
      if (row.id != id) {
        val waiting = dao.memberChanges().filter { it.listId == id }
        dao.removeMemberChangesOf(id)
        dao.putMemberChanges(waiting.map { it.copy(listId = row.id) })
      }
    }
    return Tally(sent = 1)
  }

  private suspend fun updatedOnServer(change: PatientListChange): Tally {
    val id = change.listId
    val read = readBack(id) ?: return Tally(unreachable = true)
    val fresh = (read as? ServerGroupRead.Found)?.group?.let { GroupMapper.parse(it, clock()).row }
    if (
      fresh == null ||
        !fresh.name.trim().equals(change.name.orEmpty().trim()) ||
        fresh.description.cleaned() != change.description.cleaned()
    ) {
      note(
        "update $id",
        AuditOutcome.FAILURE,
        "answer" to if (fresh == null) "not_there_after_the_yes" else "different_after_the_yes",
      )
      return refuse(change, ServerGroupWrite.Refused(0, NOT_SAVED))
    }
    transactor.run {
      dao.removeChange(id)
      dao.patientList(id)?.let { old ->
        dao.upsertPatientLists(
          listOf(fresh.copy(membersLoaded = old.membersLoaded, quantity = old.quantity)),
        )
      }
    }
    return Tally(sent = 1)
  }

  private fun notKept(
    name: String?,
    description: String?,
    locationId: String?,
    cohortType: String?,
    found: Group,
  ): List<String> {
    val row = GroupMapper.parse(found, clock()).row
    return buildList {
      if (!row.name.trim().equals(name.orEmpty().trim())) add("name")
      if (description.cleaned() != null && row.description.cleaned() != description.cleaned()) {
        add("description")
      }
      if (locationId != null && row.locationId != locationId) add("location")
      if (cohortType != null && found.getExtensionByUrl(GroupMapper.LIST_TYPE) == null) {
        add("cohort type")
      }
    }
  }

  private suspend fun refuse(change: PatientListChange, answer: ServerGroupWrite.Refused): Tally {
    dao.putChange(change.copy(error = answer.reason))
    return Tally(refused = 1, blocked = true)
  }

  private suspend fun drop(listId: String): Tally {
    var count = 0
    transactor.run {
      count =
        (if (dao.change(listId) != null) 1 else 0) +
          dao.memberChanges().count { it.listId == listId }
      forget(listId)
    }
    return Tally(dropped = count.coerceAtLeast(1), blocked = true)
  }

  private fun rowAfterCreate(
    change: PatientListChange,
    group: org.hl7.fhir.r4.model.Group?,
  ): PatientListRow {
    val parsed =
      group
        ?.takeIf { it.hasIdElement() && it.hasName() }
        ?.let { GroupMapper.parse(it, clock()).row }
    return (parsed
        ?: PatientListRow(
          id = change.listId,
          name = change.name.orEmpty(),
          description = change.description,
          typeName = null,
          locationId = change.locationId,
          locationName = change.locationName,
          creatorName = null,
          dateCreated = change.createdAt,
          changedByName = null,
          dateChanged = null,
          quantity = 0,
          attributesJson = null,
          membersLoaded = true,
          refreshedAt = clock(),
        ))
      .copy(membersLoaded = true, quantity = 0)
  }

  private data class Tally(
    val sent: Int = 0,
    val refused: Int = 0,
    val dropped: Int = 0,
    val unreachable: Boolean = false,
    val blocked: Boolean = false,
  ) {
    operator fun plus(other: Tally) =
      Tally(
        sent + other.sent,
        refused + other.refused,
        dropped + other.dropped,
        unreachable || other.unreachable,
        blocked || other.blocked,
      )

    fun result() = SendResult(sent, refused, dropped, unreachable)
  }

  companion object {
    /** Patients asked for in one request (the ids are in the URL). */
    internal const val PATIENT_BATCH = 50

    internal const val MEMBER_BATCH = 100

    internal const val HISTORY_KEPT = 500

    private const val UPLOADED_IDS_KEPT_MS = 30L * 24 * 60 * 60 * 1000

    /**
     * The [PatientListMemberChange.error] of an add that waits for an upload and could not tell
     * which of several alike patients it was for: it is not guessed, the user adds the patient
     * again.
     */
    const val AMBIGUOUS_UPLOAD = "ambiguous-upload"

    internal const val NOT_CREATED =
      "The server said yes but did not create the patient list. Its patient list module may be missing or too old."

    internal const val NOT_SAVED =
      "The server said yes but did not keep the change to the patient list."
  }
}
