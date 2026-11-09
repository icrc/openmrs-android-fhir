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
package org.openmrs.android.fhir.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import timber.log.Timber

/** The order of the recent patients, apart from where it is kept. */
object RecentPatientsOrder {
  /** The ids a list is written as in the preferences. */
  fun parse(text: String?): List<String> = text.orEmpty().split(',').filter { it.isNotBlank() }

  fun write(ids: List<String>): String = ids.joinToString(",")

  /**
   * [touched] go to the front (the first of them on top), in front of the ones already listed, with
   * no patient twice and at most [cap] in all.
   */
  fun moveToFront(
    current: List<String>,
    touched: List<String>,
    cap: Int = DemoDataStore.MAX_RECENT_PATIENTS,
  ): List<String> = (touched.filter { it.isNotBlank() } + current).distinct().take(cap)
}

/**
 * The patients the user interacted with last (opened the patient's page, registered, saved or
 * edited an encounter, started or ended a visit), most recent first, for the "Recent patients"
 * section of the Patients screen. Only the ids are kept, in the preferences, per signed-in user
 * (see `UserRegistry`); the screen reads the patients themselves from the device, so a patient who
 * is not on it any more (removed, replaced by the server's id) is simply not listed.
 *
 * Nothing is kept for a guest, nor while the setting is off (0).
 */
@Singleton
class RecentPatients @Inject constructor(private val context: Context) {
  private val appContext = context.applicationContext

  /** How many are listed (the setting). */
  suspend fun limit(): Int = DemoDataStore(appContext).getRecentPatientsLimitFlow().first()

  /** The ids, most recent first (as many as are kept, not only as many as are listed). */
  suspend fun ids(): List<String> =
    RecentPatientsOrder.parse(appContext.dataStore.data.first()[PreferenceKeys.RECENT_PATIENT_IDS])

  /** [patientIds] were interacted with now. */
  suspend fun record(patientIds: List<String>) {
    if (patientIds.none { it.isNotBlank() }) return
    if (GuestSession.isActive(appContext) || limit() == 0) return
    appContext.dataStore.edit { preferences ->
      val current = RecentPatientsOrder.parse(preferences[PreferenceKeys.RECENT_PATIENT_IDS])
      preferences[PreferenceKeys.RECENT_PATIENT_IDS] =
        RecentPatientsOrder.write(RecentPatientsOrder.moveToFront(current, patientIds))
    }
  }

  /** These patients are not on the device any more. */
  suspend fun forget(patientIds: Collection<String>) {
    if (patientIds.isEmpty()) return
    appContext.dataStore.edit { preferences ->
      val current = RecentPatientsOrder.parse(preferences[PreferenceKeys.RECENT_PATIENT_IDS])
      val kept = current.filterNot { it in patientIds }
      if (kept.size != current.size) {
        preferences[PreferenceKeys.RECENT_PATIENT_IDS] = RecentPatientsOrder.write(kept)
      }
    }
  }

  companion object {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Notes the interaction without making the screen that did it wait, and without ever failing
     * it: a list of recent patients is a convenience.
     */
    fun record(context: Context, vararg patientIds: String) {
      val ids = patientIds.filter { it.isNotBlank() }
      if (ids.isEmpty()) return
      val appContext = context.applicationContext
      scope.launch {
        try {
          RecentPatients(appContext).record(ids)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not note a recent patient")
        }
      }
    }
  }
}
