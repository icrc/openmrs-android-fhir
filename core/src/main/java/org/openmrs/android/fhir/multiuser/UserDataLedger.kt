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
package org.openmrs.android.fhir.multiuser

import android.content.Context
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.RiskAssessment
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.PatientOwner
import org.openmrs.android.fhir.data.database.model.PendingChange
import org.openmrs.android.fhir.data.database.model.PendingUser
import timber.log.Timber

/** Which patients the signed-in user may see on a device shared by several users. */
interface DataVisibility {
  /** More than one person has used the device, so some patients may have to be left out. */
  fun isSharedDevice(): Boolean

  /**
   * [items] without the ones the signed-in user must not see (see [UserDataLedger.hiddenAmong]).
   */
  suspend fun <T> visibleAmong(
    items: List<T>,
    access: Set<String> = emptySet(),
    idOf: (T) -> String,
  ): List<T>

  /** A device with one user: everybody sees everything. */
  object Everything : DataVisibility {
    override fun isSharedDevice(): Boolean = false

    override suspend fun <T> visibleAmong(
      items: List<T>,
      access: Set<String>,
      idOf: (T) -> String,
    ): List<T> = items
  }
}

/**
 * Who a record on this device belongs to, for a device shared by several people.
 *
 * It answers two questions and does so with notes of its own (two small tables), never by touching
 * the FHIR data:
 * - **Whose patients are these?** Every patient registered, or downloaded, while a user is signed
 *   in is noted as theirs (a patient can be several users'). Another user sees a patient only if it
 *   is theirs, if its owner chose to share (setting "Let other users see my registered data"), or
 *   if they have access to it themselves (it is in a patient list they selected, or they opened it
 *   from the server). See [hiddenAmong].
 * - **Who still has something to upload?** Every record created, changed or deleted is noted with
 *   the signed-in user, and the note is dropped once the FHIR engine has nothing left to upload for
 *   it. See [pendingByUser].
 *
 * Cost: a device with one user pays one preferences read per call (the answer is "nobody is
 * hidden") and one small insert per save; the ownership notes only start once a second user signs
 * in. Nothing here ever fails a save: a note that cannot be written is logged and dropped.
 */
@Singleton
class UserDataLedger
@Inject
constructor(
  private val context: Context,
  private val database: Lazy<AppDatabase>,
) : DataVisibility {
  private val dao
    get() = database.get().userDataDao()

  /** Records waiting to be completed with a location: they must not be uploaded yet. */
  fun holdsUploads(): Boolean = GuestSession.isCompletionPending(context)

  // ---- Writing notes -----------------------------------------------------------------------

  /** [resources] were created on the device; [ids] are the ids they got, in the same order. */
  suspend fun created(resources: List<Resource>, ids: List<String>) {
    note {
      val touched =
        resources.mapIndexedNotNull { index, resource ->
          touched(resource, ids.getOrNull(index) ?: resource.idElement?.idPart)
        }
      write(touched)
      claim(touched.filter { it.type == ResourceType.Patient }.map { it.id })
    }
  }

  /** [resources] were changed on the device. */
  suspend fun changed(resources: List<Resource>) {
    note { write(resources.mapNotNull { touched(it, it.idElement?.idPart) }) }
  }

  /** A record was deleted on the device. */
  suspend fun deleted(type: ResourceType, id: String) {
    note { write(listOf(Touched(type, id, patientId = null))) }
  }

  /** [resources] came from the server while the signed-in user was syncing or opening them. */
  suspend fun downloaded(resources: List<Resource>) {
    if (!UserRegistry.isMultiUser(context)) return
    note { claim(resources.filterIsInstance<Patient>().mapNotNull { it.idElement?.idPart }) }
  }

  private class Touched(val type: ResourceType, val id: String, val patientId: String?)

  private fun touched(resource: Resource, id: String?): Touched? {
    if (id.isNullOrBlank()) return null
    val patientId =
      when (resource) {
        is Patient -> id
        is Encounter -> resource.subject.patientIdOrNull()
        is Observation -> resource.subject.patientIdOrNull()
        is RiskAssessment -> resource.subject.patientIdOrNull()
        else -> null
      }
    return Touched(resource.resourceType, id, patientId)
  }

  private fun Reference?.patientIdOrNull(): String? =
    this?.takeIf { hasReference() }?.referenceElement?.idPart

  private suspend fun write(touched: List<Touched>) {
    val user = UserRegistry.active(context) ?: return
    if (touched.isEmpty()) return
    val now = System.currentTimeMillis()
    dao.addPending(
      touched.map { PendingChange(it.type.name, it.id, user, it.patientId, now) },
    )
  }

  private suspend fun claim(patientIds: List<String>) {
    if (patientIds.isEmpty() || !UserRegistry.isMultiUser(context)) return
    val user = UserRegistry.active(context) ?: return
    dao.addOwners(patientIds.map { PatientOwner(it, user) })
  }

  /** Notes are a convenience: whatever goes wrong here must not fail the save they describe. */
  private suspend fun note(block: suspend () -> Unit) {
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Could not note who changed a record")
    }
  }

  /**
   * The second user has come: the patients already on the device belong to the first one. Run once
   * (it is repeated if interrupted), before the second user can download anything of their own.
   */
  suspend fun attributeExistingPatients(engine: FhirEngine) {
    val first = UserRegistry.firstUser(context) ?: return
    var offset = 0
    while (true) {
      val page =
        engine.search<Patient> {
          count = ATTRIBUTION_PAGE
          from = offset
        }
      dao.addOwners(page.map { PatientOwner(it.resource.idElement.idPart, first) })
      if (page.size < ATTRIBUTION_PAGE) break
      offset += page.size
    }
    UserRegistry.markBackfilled(context)
  }

  // ---- Who can see what --------------------------------------------------------------------

  /**
   * The ids, among [patientIds], that the signed-in user must not see. [access] are the patients
   * the user is entitled to anyway (members of their selected patient lists, opened live).
   *
   * With a single user nobody is hidden and nothing is read but one preference.
   */
  suspend fun hiddenAmong(
    patientIds: Collection<String>,
    access: Set<String> = emptySet(),
  ): Set<String> {
    if (patientIds.isEmpty() || !UserRegistry.isMultiUser(context)) return emptySet()
    val user = UserRegistry.active(context)
    val sharing = UserRegistry.sharingUsers(context)
    // Until the patients present before the second user are attributed, they are the first user's.
    val unownedAreVisible =
      UserRegistry.isBackfilled(context) || user == UserRegistry.firstUser(context)
    val owners = HashMap<String, MutableSet<String>>()
    patientIds.distinct().chunked(CHUNK).forEach { chunk ->
      dao.ownersOf(chunk).forEach { owners.getOrPut(it.patientId) { mutableSetOf() } += it.owner }
    }
    return patientIds.filterTo(HashSet()) { id ->
      when {
        id in access -> false
        else -> {
          val ownedBy = owners[id]
          if (ownedBy == null) {
            !unownedAreVisible
          } else {
            (user == null || user !in ownedBy) && ownedBy.none { it in sharing }
          }
        }
      }
    }
  }

  override fun isSharedDevice(): Boolean = UserRegistry.isMultiUser(context)

  override suspend fun <T> visibleAmong(
    items: List<T>,
    access: Set<String>,
    idOf: (T) -> String,
  ): List<T> {
    if (items.isEmpty() || !UserRegistry.isMultiUser(context)) return items
    val hidden = hiddenAmong(items.map(idOf), access)
    return if (hidden.isEmpty()) items else items.filterNot { idOf(it) in hidden }
  }

  // ---- Who has something to upload ---------------------------------------------------------

  /**
   * The users who still have records waiting to upload, most first. Cheap when there are none (one
   * count); otherwise the engine is asked about each noted record, and the ones it has nothing to
   * upload for are dropped from the notes.
   */
  suspend fun pendingByUser(engine: FhirEngine): List<PendingUser> {
    if (dao.pendingCount() == 0) return emptyList()
    val noted = dao.allPending()
    val finished = ArrayList<PendingChange>()
    val waiting = ArrayList<PendingChange>()
    noted.forEachIndexed { index, change ->
      // A very long queue is not checked record by record: the rest is taken as waiting.
      if (index >= MAX_CHECKED) {
        waiting += change
        return@forEachIndexed
      }
      val type = runCatching { ResourceType.fromCode(change.resourceType) }.getOrNull()
      val stillWaiting =
        type != null && engine.getLocalChanges(type, change.resourceId).isNotEmpty()
      if (stillWaiting) waiting += change else finished += change
    }
    if (finished.isNotEmpty()) {
      finished.chunked(CHUNK).forEach { dao.removePending(it) }
    }
    return waiting
      .groupBy { it.owner }
      .map { (user, changes) ->
        PendingUser(
          user = user,
          patients = changes.mapNotNull { it.patientId }.distinct().size,
          records = changes.size,
        )
      }
      .sortedWith(compareByDescending<PendingUser> { it.records }.thenBy { it.user })
  }

  /** These patients were removed from the device: the notes about them go too. */
  suspend fun forget(patientIds: Collection<String>) {
    note { patientIds.chunked(CHUNK).forEach { dao.removeOwnersOf(it) } }
    note { patientIds.chunked(CHUNK).forEach { dao.removePendingOf(it) } }
  }

  /** The device was wiped: so are the notes. */
  suspend fun clear() {
    dao.clearPending()
    dao.clearOwners()
  }

  private companion object {
    const val CHUNK = 500
    const val ATTRIBUTION_PAGE = 500
    const val MAX_CHECKED = 3000
  }
}
