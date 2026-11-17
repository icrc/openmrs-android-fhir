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
import androidx.datastore.preferences.core.edit
import com.google.android.fhir.FhirEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

/**
 * Which patient lists the device downloads (the choice of the **Patient lists** screen and of the
 * page of one list). A list ticked later brings its patients at once ([PatientListDownloader]); at
 * login the first sync does it.
 */
class PatientListSelection(context: Context, private val fhirEngine: FhirEngine) {
  private val context = context.applicationContext

  val selected: Flow<Set<String>> =
    this.context.dataStore.data
      .map { it[PreferenceKeys.SELECTED_PATIENT_LISTS] ?: emptySet() }
      .distinctUntilChanged()

  suspend fun current(): Set<String> = selected.first()

  suspend fun set(listId: String, ticked: Boolean, downloadNow: Boolean) {
    val updated = current().toMutableSet().apply { if (ticked) add(listId) else remove(listId) }
    AuditLog.record(
      AuditAction.PATIENT_LISTS_CHANGED,
      AuditOutcome.SUCCESS,
      "list" to listId,
      "selected" to ticked,
      "total" to updated.size,
    )
    context.dataStore.edit { it[PreferenceKeys.SELECTED_PATIENT_LISTS] = updated }
    val downloader = PatientListDownloader(context, fhirEngine)
    if (!ticked) {
      downloader.remove(listOf(listId))
    } else if (downloadNow) {
      downloader.add(listOf(listId))
      PatientListDownloader.schedule(context, fhirEngine)
    }
  }

  suspend fun keepOnly(available: Set<String>): Set<String>? {
    val ticked = current()
    if (ticked.isEmpty()) return null
    val kept = ticked.filter { it in available }.toSet()
    if (kept.size == ticked.size) return null
    context.dataStore.edit { it[PreferenceKeys.SELECTED_PATIENT_LISTS] = kept }
    return kept
  }
}
