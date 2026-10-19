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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import timber.log.Timber

/**
 * Brings the patients of the patient lists the user has just added, right away, so they can be
 * searched and used without waiting for the next sync.
 *
 * The sync cannot do it: it asks the server only for what changed since its last run, and the
 * patients of a list that was just added were on the server long before. So the added lists are
 * remembered ([PreferenceKeys.PENDING_LIST_DOWNLOADS]) and downloaded whole, here. If the server
 * cannot be reached, the lists stay remembered and the next sync that downloads does it (see
 * `SyncInfoDatabaseWriterWorker`).
 */
class PatientListDownloader(context: Context, private val fhirEngine: FhirEngine) {
  private val context = context.applicationContext

  /** Remembers lists the user has added. */
  suspend fun add(listIds: Collection<String>) {
    if (listIds.isEmpty()) return
    context.dataStore.edit { it[PreferenceKeys.PENDING_LIST_DOWNLOADS] = pending(it) + listIds }
  }

  /** The user took the lists back out, or they are done: there is nothing left to download. */
  suspend fun remove(listIds: Collection<String>) {
    context.dataStore.edit {
      it[PreferenceKeys.PENDING_LIST_DOWNLOADS] = pending(it) - listIds.toSet()
    }
  }

  private fun pending(preferences: Preferences): Set<String> =
    preferences[PreferenceKeys.PENDING_LIST_DOWNLOADS].orEmpty()

  /**
   * Downloads the patients of the remembered lists that are still selected. Whatever fails stays
   * remembered.
   *
   * @return how many patients came.
   */
  suspend fun downloadPending(): Int =
    lock.withLock {
      val preferences = context.dataStore.data.first()
      val selected = preferences[PreferenceKeys.SELECTED_PATIENT_LISTS].orEmpty()
      val remembered = pending(preferences)
      val lists = remembered.filter { it in selected }.toSet()
      if (lists.isEmpty()) {
        remove(remembered)
        return@withLock 0
      }
      val patients =
        ApiManager(context).downloadPatientsOfLists(lists) { resources ->
          // Stored the way a sync stores them: nothing here is a local change.
          fhirEngine.syncDownload(AcceptLocalConflictResolver) { flowOf(resources) }
          // The patient list on the screen shows them as they come.
          LiveSync.notifyUploaded()
        }
      remove(lists)
      AuditLog.record(
        AuditAction.PATIENT_LISTS_CHANGED,
        AuditOutcome.SUCCESS,
        "downloaded_lists" to lists.size,
        "patients" to patients,
      )
      patients
    }

  /** How the download that follows a change of the lists is going, for the screen to tell. */
  sealed interface State {
    data object Downloading : State

    data class Done(val patients: Int) : State

    /** No connection or the server refused: the sync will bring them. */
    data object Failed : State
  }

  companion object {
    private const val DEBOUNCE_MILLIS = 1_500L
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _states = MutableSharedFlow<State>(extraBufferCapacity = 8)
    val states: SharedFlow<State> = _states

    /**
     * Downloads the remembered lists a moment after the last change (the user may be ticking
     * several), without depending on the screen that asked: leaving it does not stop it.
     */
    @Synchronized
    fun schedule(context: Context, fhirEngine: FhirEngine) {
      job?.cancel()
      job =
        scope.launch {
          delay(DEBOUNCE_MILLIS)
          _states.tryEmit(State.Downloading)
          try {
            _states.tryEmit(
              State.Done(PatientListDownloader(context, fhirEngine).downloadPending()),
            )
          } catch (e: CancellationException) {
            throw e
          } catch (e: IOException) {
            Timber.w(e, "Could not download the patients of the added lists")
            _states.tryEmit(State.Failed)
          } catch (e: Exception) {
            Timber.w(e, "Could not download the patients of the added lists")
            AuditLog.failure(AuditAction.PATIENT_LISTS_CHANGED, "error" to e.javaClass.simpleName)
            _states.tryEmit(State.Failed)
          }
        }
    }
  }
}
