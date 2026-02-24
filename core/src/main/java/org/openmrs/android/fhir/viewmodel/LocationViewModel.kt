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
package org.openmrs.android.fhir.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.rest.gclient.StringClientParam
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.CurrentSyncJobStatus
import com.google.android.fhir.sync.Sync
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Location
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.sync.FirstFhirSyncWorker
import org.openmrs.android.fhir.data.sync.LocationFhirSyncWorker

class LocationViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
) : ViewModel() {
  private var masterLocationsList: MutableList<LocationItem> = mutableListOf()
  private val restApiManager = FhirApplication.restApiClient(applicationContext)
  private var favoriteLocationSet: MutableSet<String> = mutableSetOf()

  val locations = MutableLiveData<List<LocationItem>>()
  private val _pollState = MutableSharedFlow<CurrentSyncJobStatus>()
  val pollState: Flow<CurrentSyncJobStatus>
    get() = _pollState

  private val _uiState = MutableStateFlow(LocationSelectionUiState())
  val uiState: StateFlow<LocationSelectionUiState>
    get() = _uiState

  init {
    loadSelectionPreferences()
  }

  fun fetchPreSyncData() {
    viewModelScope.launch {
      val filterPatientListsByGroup =
        applicationContext.resources.getBoolean(R.bool.filter_patient_lists_by_group)

      val syncFlow =
        if (filterPatientListsByGroup) {
          Sync.oneTimeSync<LocationFhirSyncWorker>(applicationContext)
        } else {
          Sync.oneTimeSync<FirstFhirSyncWorker>(applicationContext)
        }

      syncFlow.shareIn(this, SharingStarted.Eagerly, 10).collect { _pollState.emit(it) }
    }
  }

  fun fetchLocations() {
    viewModelScope.launch {
      Sync.oneTimeSync<LocationFhirSyncWorker>(applicationContext)
        .shareIn(this, SharingStarted.Eagerly, 10)
        .collect { _pollState.emit(it) }
    }
  }

  fun getLocations() {
    viewModelScope.launch {
      val locationsList: MutableList<LocationItem> = mutableListOf()
      fhirEngine
        .search<Location> {
          sort(
            StringClientParam(Location.SP_NAME),
            Order.ASCENDING,
          )
        }
        .mapIndexed { index, fhirLocation -> fhirLocation.resource.toLocationItem(index + 1) }
        .let { locationsList.addAll(it) }
      masterLocationsList = locationsList
      locations.value = locationsList
    }
  }

  suspend fun updateSessionLocation(resourceId: String) {
    restApiManager.updateSessionLocation(resourceId)
  }

  fun onQueryChanged(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun onLoadingChanged(isLoading: Boolean) {
    _uiState.update { it.copy(isLoading = isLoading) }
  }

  fun onShowContentChanged(showContent: Boolean) {
    _uiState.update { it.copy(showContent = showContent) }
  }

  fun onFavoriteToggle(resourceId: String, isFavorite: Boolean) {
    val updatedFavorites = favoriteLocationSet.toMutableSet()
    if (isFavorite) {
      updatedFavorites.remove(resourceId)
    } else {
      updatedFavorites.add(resourceId)
    }
    favoriteLocationSet = updatedFavorites
    _uiState.update { it.copy(favoriteLocationIds = updatedFavorites) }
    viewModelScope.launch {
      applicationContext.dataStore.edit { preferences ->
        preferences[PreferenceKeys.FAVORITE_LOCATIONS] = updatedFavorites
      }
    }
  }

  fun onLocationSelected(resourceId: String, name: String) {
    _uiState.update { it.copy(selectedLocationId = resourceId) }
    viewModelScope.launch {
      applicationContext.dataStore.edit { preferences ->
        preferences[PreferenceKeys.LOCATION_ID] = resourceId
        preferences[PreferenceKeys.LOCATION_NAME] = name
      }
    }
  }

  private fun loadSelectionPreferences() {
    viewModelScope.launch {
      val preferences = applicationContext.dataStore.data.first()
      val favoriteIds =
        preferences[PreferenceKeys.FAVORITE_LOCATIONS]?.toMutableSet() ?: mutableSetOf()
      favoriteLocationSet = favoriteIds
      _uiState.update {
        it.copy(
          favoriteLocationIds = favoriteIds,
          selectedLocationId = preferences[PreferenceKeys.LOCATION_ID],
        )
      }
    }
  }

  private fun Location.toLocationItem(position: Int): LocationItem {
    return LocationItem(
      id = position.toString(),
      resourceId = idElement.idPart,
      status = status.name,
      name = name,
      description ?: "",
    )
  }

  data class LocationItem(
    val id: String,
    val resourceId: String,
    val status: String,
    val name: String,
    val description: String,
  )

  data class LocationSelectionUiState(
    val query: String = "",
    val favoriteLocationIds: Set<String> = emptySet(),
    val selectedLocationId: String? = null,
    val isLoading: Boolean = false,
    val showContent: Boolean = true,
  )
}
