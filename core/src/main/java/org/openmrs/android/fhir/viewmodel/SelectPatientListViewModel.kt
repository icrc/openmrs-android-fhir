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
import com.google.android.fhir.FhirEngine
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
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Reference
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.sync.GroupFhirSyncWorker

class SelectPatientListViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val applicationContext: Context,
) : ViewModel() {
  private var masterSelectPatientListItemList: MutableList<SelectPatientListItem> = mutableListOf()

  val selectPatientListItems = MutableLiveData<List<SelectPatientListItem>>()
  private val _pollState = MutableSharedFlow<CurrentSyncJobStatus>()
  val pollState: Flow<CurrentSyncJobStatus>
    get() = _pollState

  private val _uiState = MutableStateFlow(SelectPatientListUiState())
  val uiState: StateFlow<SelectPatientListUiState>
    get() = _uiState

  init {
    loadSelectedPatientLists()
  }

  fun getSelectPatientListItems() {
    viewModelScope.launch {
      // TODO Pending for cohort-module
      //
      // val filterPatientListsByGroup =
      // applicationContext.resources.getBoolean(R.bool.filter_patient_lists_by_group)
      val filterPatientListsByGroup = false
      val locationId = applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
      val selectPatientListItemList: MutableList<SelectPatientListItem> = mutableListOf()
      val groups = fhirEngine.search<Group> {}.map { it.resource }
      val filteredGroups =
        if (filterPatientListsByGroup) {
          groups.filter { it.belongsToLocation(locationId) }
        } else {
          groups
        }

      filteredGroups
        .mapIndexed { index, group -> group.toSelectPatientListItem(index + 1) }
        .let { selectPatientListItemList.addAll(it) }
      masterSelectPatientListItemList = selectPatientListItemList
      selectPatientListItems.value = selectPatientListItemList
    }
  }

  fun fetchPatientListItems() {
    viewModelScope.launch {
      Sync.oneTimeSync<GroupFhirSyncWorker>(applicationContext)
        .shareIn(this, SharingStarted.Eagerly, 10)
        .collect { _pollState.emit(it) }
    }
  }

  fun getSelectPatientListItemsListFiltered(query: String = ""): List<SelectPatientListItem> {
    return masterSelectPatientListItemList.filter { it.name.contains(query, true) }
  }

  fun onQueryChanged(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun onLoadingChanged(isLoading: Boolean) {
    _uiState.update { it.copy(isLoading = isLoading) }
  }

  fun onPatientListToggle(resourceId: String, isSelected: Boolean) {
    val updatedSelection = _uiState.value.selectedPatientListIds.toMutableSet()
    if (isSelected) {
      updatedSelection.remove(resourceId)
    } else {
      updatedSelection.add(resourceId)
    }
    _uiState.update { it.copy(selectedPatientListIds = updatedSelection) }
    viewModelScope.launch {
      applicationContext.dataStore.edit { preferences ->
        preferences[PreferenceKeys.SELECTED_PATIENT_LISTS] = updatedSelection
      }
    }
  }

  private fun loadSelectedPatientLists() {
    viewModelScope.launch {
      val selectedIds =
        applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_PATIENT_LISTS]
          ?: emptySet()
      _uiState.update { it.copy(selectedPatientListIds = selectedIds) }
    }
  }

  private fun Group.toSelectPatientListItem(position: Int): SelectPatientListItem {
    return SelectPatientListItem(
      id = position.toString(),
      resourceId = idElement.idPart,
      name = name,
    )
  }

  private fun Group.belongsToLocation(locationId: String?): Boolean {
    if (locationId.isNullOrBlank()) {
      return true
    }

    return extension
      .mapNotNull { it.extractLocationReferenceId() }
      .any { it.equals(locationId, ignoreCase = true) }
  }

  private fun org.hl7.fhir.r4.model.Extension.extractLocationReferenceId(): String? {
    if (url != GROUP_LOCATION_EXTENSION_URL) {
      return null
    }

    val reference = value as? Reference ?: return null
    val referenceValue = reference.reference ?: return null

    return referenceValue.substringAfter("Location/", missingDelimiterValue = "").takeIf {
      it.isNotBlank()
    }
  }

  companion object {
    private const val GROUP_LOCATION_EXTENSION_URL = "http://fhir.openmrs.org/ext/group/location"
  }

  data class SelectPatientListItem(
    val id: String,
    val resourceId: String,
    val name: String,
  )

  data class SelectPatientListUiState(
    val query: String = "",
    val selectedPatientListIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
  )
}
