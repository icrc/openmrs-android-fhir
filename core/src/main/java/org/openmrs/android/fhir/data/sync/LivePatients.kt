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
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.hl7.fhir.r4.model.Patient
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager

/**
 * Live mode, the receiving half: patients are looked up on the server as the user types, and the
 * one they open is brought to the device (with their encounters and observations) and used like any
 * other patient from then on, offline included.
 *
 * Everything here is read only on the server. What is downloaded is stored the way a sync stores it
 * (it is not a local change, so nothing is sent back), and what is waiting for upload on the device
 * is never overwritten.
 */
class LivePatients(
  private val context: Context,
  private val fhirEngine: FhirEngine,
) {
  private val apiManager by lazy { ApiManager(context) }

  /** The patients of the server matching [text] by name or identifier. */
  suspend fun search(text: String): List<Patient> = apiManager.searchPatients(text)

  /** Brings the patient's record from the server to the device and remembers to list it. */
  suspend fun download(patientId: String, remember: Boolean = true) {
    val resources = apiManager.fetchPatientRecord(patientId)
    check(resources.any { it is Patient }) { "The server did not return the patient" }
    fhirEngine.syncDownload(AcceptLocalConflictResolver) { flowOf(resources) }
    downloadedAt[patientId] = SystemClock.elapsedRealtime()
    if (remember) remember(patientId)
  }

  /**
   * The patients brought here by a live search. The patient list only shows the patients of the
   * selected patient lists, and these are not in them.
   */
  suspend fun rememberedIds(): Set<String> =
    try {
      context.dataStore.data.first()[PreferenceKeys.LIVE_PATIENT_IDS].orEmpty()
    } catch (e: Exception) {
      emptySet()
    }

  companion object {
    private const val FRESH_MILLIS = 30_000L
    private val downloadedAt = ConcurrentHashMap<String, Long>()

    /** The record was brought from the server a moment ago: there is nothing newer to ask for. */
    fun isFresh(patientId: String): Boolean {
      val at = downloadedAt[patientId] ?: return false
      return SystemClock.elapsedRealtime() - at < FRESH_MILLIS
    }
  }

  private suspend fun remember(patientId: String) {
    context.dataStore.edit { preferences ->
      preferences[PreferenceKeys.LIVE_PATIENT_IDS] =
        preferences[PreferenceKeys.LIVE_PATIENT_IDS].orEmpty() + patientId
    }
  }
}
