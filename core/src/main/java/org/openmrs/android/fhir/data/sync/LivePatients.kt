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
package org.openmrs.android.fhir.data.sync

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import timber.log.Timber

/**
 * Live mode, the receiving half: patients are looked up on the server as the user types, and the
 * one they open is brought to the device (with their encounters and observations) and used like any
 * other patient from then on, offline included.
 *
 * Everything here is read only on the server. What is downloaded is stored the way a sync stores it
 * (it is not a local change, so nothing is sent back), and what is waiting for upload on the device
 * is never overwritten.
 *
 * Whether the patient then stays on the device is a setting (Settings > Live mode > Keep patients
 * from the server on this device, on by default). When it is off the patient is *temporary*: it is
 * removed (record, encounters, observations) once the user has left them, as soon as nothing about
 * them is waiting for upload.
 */
class LivePatients(
  private val context: Context,
  private val fhirEngine: FhirEngine,
) {
  private val apiManager by lazy { ApiManager(context) }

  /** The patients of the server matching [text] by name or identifier. */
  suspend fun search(text: String): List<Patient> = apiManager.searchPatients(text)

  /**
   * Brings the patient's record from the server to the device. With [remember] (the patient comes
   * from the live search) it is also listed from now on, for good or only until the user leaves
   * them, depending on the setting.
   */
  suspend fun download(patientId: String, remember: Boolean = true) {
    val resources = apiManager.fetchPatientRecord(patientId)
    check(resources.any { it is Patient }) { "The server did not return the patient" }
    // A patient that was already here (a list the user deselected since) is not this feature's to
    // remove.
    val wasHere = remember && patientId !in temporaryIds() && isOnDevice(patientId)
    fhirEngine.syncDownload(AcceptLocalConflictResolver) { flowOf(resources) }
    downloadedAt[patientId] = SystemClock.elapsedRealtime()
    if (remember) {
      awaitingOpen[patientId] = SystemClock.elapsedRealtime()
      if (wasHere || keepPatients()) {
        add(PreferenceKeys.LIVE_PATIENT_IDS, patientId)
      } else {
        add(PreferenceKeys.LIVE_TEMPORARY_IDS, patientId)
      }
    }
  }

  /**
   * The patients brought here by a live search, the temporary ones included. The patient list only
   * shows the patients of the selected patient lists, and these are not in them.
   */
  suspend fun rememberedIds(): Set<String> =
    try {
      val preferences = context.dataStore.data.first()
      preferences[PreferenceKeys.LIVE_PATIENT_IDS].orEmpty() +
        preferences[PreferenceKeys.LIVE_TEMPORARY_IDS].orEmpty()
    } catch (e: Exception) {
      emptySet()
    }

  private suspend fun temporaryIds(): Set<String> =
    try {
      context.dataStore.data.first()[PreferenceKeys.LIVE_TEMPORARY_IDS].orEmpty()
    } catch (e: Exception) {
      emptySet()
    }

  private suspend fun keepPatients(): Boolean =
    try {
      DemoDataStore(context).getKeepLivePatientsFlow().first()
    } catch (e: Exception) {
      true
    }

  private suspend fun isOnDevice(patientId: String): Boolean =
    try {
      fhirEngine.get<Patient>(patientId)
      true
    } catch (e: ResourceNotFoundException) {
      false
    }

  /**
   * Removes the temporary patients the user is no longer using, as far as nothing about them waits
   * for upload (what does wait is tried again after the next upload). If the setting was turned
   * back on meanwhile they are kept instead.
   */
  suspend fun discardTemporary() =
    discardLock.withLock {
      val temporary = temporaryIds()
      if (temporary.isEmpty()) return@withLock
      if (keepPatients()) {
        context.dataStore.edit { preferences ->
          preferences[PreferenceKeys.LIVE_PATIENT_IDS] =
            preferences[PreferenceKeys.LIVE_PATIENT_IDS].orEmpty() + temporary
          preferences.remove(PreferenceKeys.LIVE_TEMPORARY_IDS)
        }
        return@withLock
      }
      val removed =
        temporary.filter { id -> id != openPatientId && !isAboutToOpen(id) && discard(id) }
      if (removed.isEmpty()) return@withLock
      context.dataStore.edit { preferences ->
        preferences[PreferenceKeys.LIVE_TEMPORARY_IDS] =
          preferences[PreferenceKeys.LIVE_TEMPORARY_IDS].orEmpty() - removed.toSet()
      }
      // The lists showing them reload.
      LiveSync.notifyUploaded()
    }

  /** @return whether the patient's data was removed (false: something about them is pending). */
  private suspend fun discard(patientId: String): Boolean {
    val reference = "Patient/$patientId"
    val encounters =
      fhirEngine
        .search<Encounter> { filter(Encounter.SUBJECT, { value = reference }) }
        .map { it.resource.idElement.idPart }
    val observations =
      fhirEngine
        .search<Observation> { filter(Observation.SUBJECT, { value = reference }) }
        .map { it.resource.idElement.idPart }
    val conditions =
      fhirEngine
        .search<Condition> { filter(Condition.SUBJECT, { value = reference }) }
        .map { it.resource.idElement.idPart }
    val everything =
      listOf(
        ResourceType.Patient to listOf(patientId),
        ResourceType.Encounter to encounters,
        ResourceType.Observation to observations,
        ResourceType.Condition to conditions,
      )
    val pending =
      everything.any { (type, ids) ->
        ids.any { fhirEngine.getLocalChanges(type, it).isNotEmpty() }
      }
    if (pending) return false
    // The things that point at others first.
    listOf(
        ResourceType.Observation,
        ResourceType.Condition,
        ResourceType.Encounter,
        ResourceType.Patient,
      )
      .forEach { type ->
        val ids = everything.first { it.first == type }.second.toSet()
        if (ids.isNotEmpty()) fhirEngine.purge(type, ids)
      }
    return true
  }

  private suspend fun add(
    key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>,
    id: String,
  ) {
    context.dataStore.edit { preferences -> preferences[key] = preferences[key].orEmpty() + id }
  }

  companion object {
    private const val FRESH_MILLIS = 30_000L
    private const val OPENING_MILLIS = 120_000L
    private val downloadedAt = ConcurrentHashMap<String, Long>()

    /** Patients just brought from the server whose page has not opened yet. */
    private val awaitingOpen = ConcurrentHashMap<String, Long>()
    private val discardLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The patient whose page is open: never removed under the user's hands. */
    @Volatile private var openPatientId: String? = null

    fun markOpen(patientId: String) {
      openPatientId = patientId
      awaitingOpen.remove(patientId)
    }

    private fun isAboutToOpen(patientId: String): Boolean {
      val since = awaitingOpen[patientId] ?: return false
      return SystemClock.elapsedRealtime() - since < OPENING_MILLIS
    }

    fun markClosed(patientId: String) {
      if (openPatientId == patientId) openPatientId = null
    }

    /** The record was brought from the server a moment ago: there is nothing newer to ask for. */
    fun isFresh(patientId: String): Boolean {
      val at = downloadedAt[patientId] ?: return false
      return SystemClock.elapsedRealtime() - at < FRESH_MILLIS
    }

    /** [discardTemporary] without waiting for it, and surviving the screen that asked. */
    fun discardInBackground(context: Context, fhirEngine: FhirEngine) {
      scope.launch {
        try {
          LivePatients(context.applicationContext, fhirEngine).discardTemporary()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not remove the temporary patients")
        }
      }
    }
  }
}
