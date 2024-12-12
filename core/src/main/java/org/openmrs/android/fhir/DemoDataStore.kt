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

  suspend fun clearAll() {
    context.dataStorage.edit { it.clear() }
  }
}
