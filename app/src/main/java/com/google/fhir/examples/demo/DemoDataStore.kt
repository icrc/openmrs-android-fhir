package com.google.fhir.examples.demo
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.fhir.sync.DownloadWorkManager
import kotlinx.coroutines.flow.first
import org.hl7.fhir.r4.model.ResourceType

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

  suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String? {
    return context.dataStorage.data.first()[stringPreferencesKey(resourceType.name)]
  }
}