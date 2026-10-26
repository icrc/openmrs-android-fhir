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
package org.openmrs.android.fhir.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.WorkManager
import com.google.android.fhir.FhirEngine
import kotlinx.coroutines.flow.first
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.extensions.checkAndDeleteLogFile
import org.openmrs.android.fhir.multiuser.UserRegistry

/**
 * What leaves the device when a session ends: the session alone (the patients stay), or everything
 * (logout with "delete data", "Log out and delete all data").
 */
class SessionCleaner(
  private val context: Context,
  private val authStateManager: AuthStateManager,
  private val fhirEngine: FhirEngine,
  private val demoDataStore: DemoDataStore,
  private val database: AppDatabase,
) {
  /** Logging out with "delete data" off: the session goes, the patients and drafts stay. */
  suspend fun endSessionKeepingData() {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelAllWork()
    workManager.pruneWork()
    authStateManager.resetBasicAuthCredentials()
    BiometricUtils.deleteBiometricKey(context)
  }

  /**
   * The session's preferences go; when the data stays, so do the choices that belong to it (the
   * location, the patient lists, the identifier types), or the kept data would not match them.
   */
  suspend fun clearSessionPreferences(clearData: Boolean) {
    val kept = if (clearData) null else context.dataStore.data.first()
    authStateManager.clearAuthDataStore()
    context.dataStore.edit { preferences -> preferences.clear() }
    if (kept != null) {
      context.dataStore.edit { preferences ->
        kept[PreferenceKeys.LOCATION_ID]?.let { preferences[PreferenceKeys.LOCATION_ID] = it }
        kept[PreferenceKeys.LOCATION_NAME]?.let { preferences[PreferenceKeys.LOCATION_NAME] = it }
        kept[PreferenceKeys.SELECTED_PATIENT_LISTS]?.let {
          preferences[PreferenceKeys.SELECTED_PATIENT_LISTS] = it
        }
        kept[PreferenceKeys.FAVORITE_LOCATIONS]?.let {
          preferences[PreferenceKeys.FAVORITE_LOCATIONS] = it
        }
        kept[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.let {
          preferences[PreferenceKeys.SELECTED_IDENTIFIER_TYPES] = it
        }
      }
    }
  }

  /** Clears app data except AuthStateManager's datastore. */
  suspend fun clearAppData() {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelAllWork()
    workManager.pruneWork()
    fhirEngine.clearDatabase()
    demoDataStore.clearAll()
    database.clearAllTables()
    FhirApplication.appComponent(context).unidentifiedPatients().forget()
    // Nothing of any user is left on the device.
    UserRegistry.reset(context)
    authStateManager.resetBasicAuthCredentials()
    BiometricUtils.deleteBiometricKey(context)
    checkAndDeleteLogFile(context)
    clearApplicationFiles()
  }

  private fun clearApplicationFiles() {
    val dirs = listOf(context.filesDir, context.cacheDir, context.getExternalFilesDir(null))
    dirs.forEach { dir ->
      dir?.listFiles()?.forEach { file ->
        if (
          dir == context.filesDir && (file.name == "datastore" || file.name == AuditLog.DIRECTORY)
        ) {
          return@forEach
        }
        file.deleteRecursively()
      }
    }
  }
}
