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
package org.openmrs.android.fhir.unidentified

import android.content.Context
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.UnidentifiedPatient
import org.openmrs.android.fhir.data.sync.LivePatients
import org.openmrs.android.fhir.data.sync.LiveSync

/**
 * "Identify patient after": record visits, encounters and observations for a patient who is on the
 * server but not on this device, without registering them (which would end as a duplicate).
 * - [create] makes a placeholder patient, with a note to know who it is. The records are entered
 *   against it like against any patient (the profile, the forms, the visits all work).
 * - Everything that is about a placeholder is kept **out of the upload queue** (see
 *   `TrackingFhirEngine`): the sync, automatic or not, has nothing to send and never tries.
 * - [identify] takes the real patient from the server (a search, like live mode), moves every
 *   record to them (as new records, so they upload normally) and drops the placeholder.
 */
@Singleton
class UnidentifiedPatients
@Inject
constructor(
  private val context: Context,
  private val fhirEngine: FhirEngine,
  private val store: UnidentifiedStore,
) {
  private val lock = Mutex()

  /** One entry of the list: the note and what was recorded. */
  data class Entry(
    val patientId: String,
    val note: String,
    val createdAt: Long,
    val visits: Int,
    val encounters: Int,
  )

  suspend fun count(): Int = store.ids().size

  suspend fun ids(): Set<String> = store.ids()

  suspend fun note(patientId: String): String? = store.get(patientId)?.note

  /** A placeholder patient with [note]; returns its id, to open the patient's page. */
  suspend fun create(note: String): String =
    lock.withLock {
      val entry =
        UnidentifiedPatient(UUID.randomUUID().toString(), note.trim(), System.currentTimeMillis())
      // Known before it is saved, so nothing that follows can reach the upload queue.
      store.put(entry)
      save(placeholder(entry))
      entry.patientId
    }

  suspend fun updateNote(patientId: String, note: String) =
    lock.withLock {
      val entry = store.get(patientId) ?: return@withLock
      val changed = entry.copy(note = note.trim())
      store.put(changed)
      save(placeholder(changed))
    }

  suspend fun entries(): List<Entry> =
    store.all().map { entry ->
      val encounters = recordsOf(entry.patientId).filterIsInstance<Encounter>()
      val visits = encounters.count { it.isVisit() }
      Entry(entry.patientId, entry.note, entry.createdAt, visits, encounters.size - visits)
    }

  /** The patients of the server matching [text] (needs a connection). */
  suspend fun searchServer(text: String): List<Patient> =
    LivePatients(context, fhirEngine).search(text)

  /**
   * Moves what was recorded for [entryId] to the patient [serverPatientId] of the server.
   *
   * @return how many records were moved.
   */
  suspend fun identify(entryId: String, serverPatientId: String): Int =
    lock.withLock {
      store.get(entryId) ?: return@withLock 0
      // First the real patient: if this fails nothing has been touched.
      LivePatients(context, fhirEngine).download(serverPatientId, remember = true)
      moveRecords(entryId, serverPatientId)
    }

  /**
   * The patient could not be found on the server: what was recorded for [entryId] goes to
   * [newPatientId], a patient registered for it (already saved on the device), and is sent with it.
   *
   * @return how many records were moved.
   */
  suspend fun linkToNewPatient(entryId: String, newPatientId: String): Int =
    lock.withLock {
      store.get(entryId) ?: return@withLock 0
      moveRecords(entryId, newPatientId)
    }

  private suspend fun moveRecords(entryId: String, patientId: String): Int {
    val records = recordsOf(entryId)
    val target = Reference("Patient/$patientId")
    records.forEach { it.setSubjectTo(target) }
    val ordered = records.sortedBy { it.order() }
    fhirEngine.withTransaction {
      // They were never uploaded and have no pending change: purged, then created again as new
      // records for the real patient (the same ids, which the server keeps).
      listOf(ResourceType.Observation, ResourceType.Condition, ResourceType.Encounter).forEach {
        type ->
        val ids = ordered.filter { it.resourceType == type }.map { it.idElement.idPart }.toSet()
        if (ids.isNotEmpty()) purge(type, ids)
      }
      if (ordered.isNotEmpty()) create(*ordered.toTypedArray())
    }
    fhirEngine.purge(ResourceType.Patient, setOf(entryId), true)
    store.remove(entryId)
    LiveSync.requestUpload(context)
    LiveSync.notifyUploaded()
    return ordered.size
  }

  /** The database was emptied: forget what was cached. */
  fun forget() = store.invalidate()

  /** Their records were removed from the device: nobody is waiting to be identified any more. */
  suspend fun removeAll() {
    lock.withLock { store.ids().forEach { store.remove(it) } }
  }

  // ---- records of a placeholder -----------------------------------------------------------------

  private suspend fun recordsOf(patientId: String): List<Resource> {
    val reference = "Patient/$patientId"
    val encounters =
      fhirEngine
        .search<Encounter> { filter(Encounter.SUBJECT, { value = reference }) }
        .map { it.resource }
    val observations =
      fhirEngine
        .search<Observation> { filter(Observation.SUBJECT, { value = reference }) }
        .map { it.resource }
    val conditions =
      fhirEngine
        .search<Condition> { filter(Condition.SUBJECT, { value = reference }) }
        .map { it.resource }
    return encounters + observations + conditions
  }

  private fun Resource.setSubjectTo(reference: Reference) {
    when (this) {
      is Encounter -> subject = reference
      is Observation -> subject = reference
      is Condition -> subject = reference
      else -> Unit
    }
  }

  /** Visits first, then the encounters inside them, then what was observed. */
  private fun Resource.order(): Int =
    when (this) {
      is Encounter -> if (isVisit()) 0 else 1
      is Condition -> 2
      else -> 3
    }

  private fun Encounter.isVisit(): Boolean =
    type.any { type -> type.coding.any { it.system == Constants.VISIT_TYPE_CODE_SYSTEM } }

  // ---- the placeholder patient
  // --------------------------------------------------------------------

  private fun placeholder(entry: UnidentifiedPatient) =
    Patient().apply {
      id = entry.patientId
      addName(
        HumanName().apply {
          addGiven(entry.note.ifBlank { context.getString(R.string.identify_later_no_note) })
          family = context.getString(R.string.identify_later_family)
        },
      )
      meta.addTag(Coding(TAG_SYSTEM, TAG_CODE, "Identify patient after"))
    }

  /** Saved the way a download is: not a local change, so it is never uploaded. */
  private suspend fun save(patient: Patient) {
    fhirEngine.syncDownload(AcceptLocalConflictResolver) { flowOf(listOf(patient)) }
  }

  companion object {
    const val TAG_SYSTEM = "http://openmrs.org/fhir/tag"
    const val TAG_CODE = "identify-patient-after"
  }
}
