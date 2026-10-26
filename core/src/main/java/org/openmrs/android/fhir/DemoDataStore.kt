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
package org.openmrs.android.fhir

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.fhir.sync.DownloadWorkManager
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.hl7.fhir.r4.model.ResourceType
import org.json.JSONArray
import org.json.JSONObject
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.database.model.CustomFormSection
import org.openmrs.android.fhir.data.sync.SyncMode

private val Context.dataStorage: DataStore<Preferences> by
  preferencesDataStore(name = "demo_app_storage")

/**
 * Stores the lastUpdated timestamp per resource to be used by [DownloadWorkManager]'s
 * implementation for optimal sync. See
 * [_lastUpdated](https://build.fhir.org/search.html#_lastUpdated).
 */
class DemoDataStore(private val context: Context) {

  suspend fun saveLastUpdatedTimestamp(resourceType: ResourceType, timestamp: String) {
    context.dataStorage.edit { pref -> pref[stringPreferencesKey(resourceType.name)] = timestamp }
  }

  /** The next download of these types starts from the beginning again. */
  suspend fun clearLastUpdatedTimestamps(vararg types: ResourceType) {
    context.dataStorage.edit { pref ->
      types.forEach { pref.remove(stringPreferencesKey(it.name)) }
    }
  }

  suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String? {
    return context.dataStorage.data.first()[stringPreferencesKey(resourceType.name)]
  }

  /**
   * When the last full sync (upload and download) ended. The engine keeps a "last sync" time too,
   * but it is also set by an upload-only run (live mode), which has downloaded nothing.
   */
  suspend fun saveLastFullSync(time: OffsetDateTime) {
    context.dataStorage.edit { pref ->
      pref[stringPreferencesKey(LAST_FULL_SYNC)] = time.toString()
    }
  }

  suspend fun getLastFullSync(): OffsetDateTime? =
    try {
      OffsetDateTime.parse(context.dataStorage.data.first()[stringPreferencesKey(LAST_FULL_SYNC)])
    } catch (e: Exception) {
      null
    }

  suspend fun saveTokenExpiryDelay(time: String) {
    context.dataStorage.edit { pref -> pref[stringPreferencesKey(TOKEN_EXPIRY_DELAY)] = time }
  }

  suspend fun getTokenExpiryDelay(): Long {
    return (context.dataStorage.data
      .first()[stringPreferencesKey(TOKEN_EXPIRY_DELAY)]
      ?.toLong()
      ?.times(60)
      ?: INITIAL_TOKEN_CHECK_DELAY.times(60)) * 1000
  }

  suspend fun clearAll() {
    context.dataStorage.edit { it.clear() }
  }

  suspend fun savePeriodicSyncDelay(time: String) {
    context.dataStorage.edit { pref -> pref[stringPreferencesKey(PERIODIC_SYNC_DELAY)] = time }
  }

  suspend fun getPeriodicSyncDelay(): Long {
    return (context.dataStorage.data
      .first()[stringPreferencesKey(PERIODIC_SYNC_DELAY)]
      ?.toLong()
      ?.times(60)
      ?: INITIAL_PERIODIC_SYNC_DELAY.times(60)) * 1000
  }

  suspend fun setAutoSyncEnabled(isEnabled: Boolean) {
    context.dataStorage.edit { pref -> pref[booleanPreferencesKey(AUTO_SYNC_ENABLED)] = isEnabled }
  }

  /** Whether the app syncs by itself every [getPeriodicSyncDelayMinutesFlow] minutes. */
  fun getAutoSyncEnabledFlow() =
    context.dataStorage.data.map { pref -> pref[booleanPreferencesKey(AUTO_SYNC_ENABLED)] ?: true }

  suspend fun setAutoSyncMode(mode: SyncMode) {
    context.dataStorage.edit { pref -> pref[stringPreferencesKey(AUTO_SYNC_MODE)] = mode.value }
  }

  /** What the automatic sync does: upload only, or upload and download (the default). */
  fun getAutoSyncModeFlow() =
    context.dataStorage.data.map { pref ->
      SyncMode.fromValue(pref[stringPreferencesKey(AUTO_SYNC_MODE)]).let {
        if (it == SyncMode.DOWNLOAD_ONLY) SyncMode.BOTH else it
      }
    }

  suspend fun setLiveModeEnabled(isEnabled: Boolean) {
    context.dataStorage.edit { pref -> pref[booleanPreferencesKey(LIVE_MODE)] = isEnabled }
  }

  /**
   * Whether what is saved is sent to the server at once and the server can be searched (live mode).
   * Off by default: the app is offline first and only syncs when asked or on its timer.
   */
  fun getLiveModeEnabledFlow() =
    context.dataStorage.data.map { pref -> pref[booleanPreferencesKey(LIVE_MODE)] ?: false }

  suspend fun setKeepLivePatients(keep: Boolean) {
    context.dataStorage.edit { pref -> pref[booleanPreferencesKey(KEEP_LIVE_PATIENTS)] = keep }
  }

  /**
   * Whether a patient brought from the server by the live search stays on the device (on, the
   * default) or is removed once the user leaves them.
   */
  fun getKeepLivePatientsFlow() =
    context.dataStorage.data.map { pref -> pref[booleanPreferencesKey(KEEP_LIVE_PATIENTS)] ?: true }

  suspend fun setAutomaticVisits(automatic: Boolean) {
    context.dataStorage.edit { pref -> pref[booleanPreferencesKey(AUTOMATIC_VISITS)] = automatic }
  }

  /**
   * Whether a visit is created for every encounter that is saved (on, the default) or the user
   * opens and ends the visits and the encounters they save belong to the open one.
   */
  fun getAutomaticVisitsFlow() =
    context.dataStorage.data.map { pref -> pref[booleanPreferencesKey(AUTOMATIC_VISITS)] ?: true }

  fun getPeriodicSyncDelayMinutesFlow() =
    context.dataStorage.data.map { pref ->
      pref[stringPreferencesKey(PERIODIC_SYNC_DELAY)]?.toIntOrNull()?.takeIf { it > 0 }
        ?: INITIAL_PERIODIC_SYNC_DELAY.toInt()
    }

  /** The ids of the favorite forms, in the order they were picked. */
  fun getFavoriteFormIdsFlow() =
    context.dataStorage.data.map { pref ->
      pref[stringPreferencesKey(FAVORITE_FORMS)]
        ?.split(FAVORITE_FORMS_SEPARATOR)
        ?.filter { it.isNotBlank() }
        .orEmpty()
    }

  suspend fun setFavoriteFormIds(ids: List<String>) {
    context.dataStorage.edit { pref ->
      pref[stringPreferencesKey(FAVORITE_FORMS)] = ids.joinToString(FAVORITE_FORMS_SEPARATOR)
    }
  }

  /** The sections the user made for the Create Encounter screen; null: the app's own. */
  fun getCustomFormSectionsFlow() =
    context.dataStorage.data.map { pref ->
      pref[stringPreferencesKey(CUSTOM_FORM_SECTIONS)]?.let { parseCustomFormSections(it) }
    }

  /** Saves the user's own sections; null (or none) brings back the app's. */
  suspend fun setCustomFormSections(sections: List<CustomFormSection>?) {
    context.dataStorage.edit { pref ->
      val key = stringPreferencesKey(CUSTOM_FORM_SECTIONS)
      if (sections.isNullOrEmpty()) {
        pref.remove(key)
      } else {
        pref[key] =
          JSONArray()
            .apply {
              sections.forEach {
                put(JSONObject().put("name", it.name).put("forms", JSONArray(it.formIds)))
              }
            }
            .toString()
      }
    }
  }

  private fun parseCustomFormSections(json: String): List<CustomFormSection>? =
    try {
      val array = JSONArray(json)
      (0 until array.length())
        .map { array.getJSONObject(it) }
        .map { section ->
          val forms = section.getJSONArray("forms")
          CustomFormSection(
            name = section.getString("name"),
            formIds = (0 until forms.length()).map { forms.getString(it) },
          )
        }
        .takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
      null
    }

  suspend fun setCheckNetworkConnectivity(isEnabled: Boolean) {
    context.dataStore.edit { pref ->
      pref[booleanPreferencesKey(CHECK_NETWORK_CONNECTIVITY)] = isEnabled
    }
  }

  fun getCheckNetworkConnectivityFlow() =
    context.dataStore.data.map { preferences ->
      preferences[booleanPreferencesKey(CHECK_NETWORK_CONNECTIVITY)] ?: true
    }

  suspend fun setNotificationsEnabled(isEnabled: Boolean) {
    context.dataStore.edit { pref -> pref[booleanPreferencesKey(ENABLE_NOTIFICATIONS)] = isEnabled }
  }

  fun getNotificationsEnabledFlow() =
    context.dataStore.data.map { preferences ->
      preferences[booleanPreferencesKey(ENABLE_NOTIFICATIONS)] ?: true
    }

  companion object {
    const val TOKEN_EXPIRY_DELAY = "token-expiry-delay"
    const val PERIODIC_SYNC_DELAY = "periodic-sync-delay"
    const val AUTO_SYNC_ENABLED = "auto-sync-enabled"
    const val AUTO_SYNC_MODE = "auto-sync-mode"
    const val LIVE_MODE = "live-mode"
    const val AUTOMATIC_VISITS = "automatic-visits"
    const val KEEP_LIVE_PATIENTS = "keep-live-patients"
    const val LAST_FULL_SYNC = "last-full-sync"
    const val FAVORITE_FORMS = "favorite-forms"
    const val CUSTOM_FORM_SECTIONS = "custom-form-sections"
    private const val FAVORITE_FORMS_SEPARATOR = ","

    /** How many forms can be favorites. */
    const val MAX_FAVORITE_FORMS = 5
    const val CHECK_NETWORK_CONNECTIVITY = "check-network-connectivity"
    const val ENABLE_NOTIFICATIONS = "enable-notifications"
    const val INITIAL_TOKEN_CHECK_DELAY: Long = 1
    const val INITIAL_PERIODIC_SYNC_DELAY: Long = 15
  }
}
