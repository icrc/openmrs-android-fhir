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
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.sync.PatientListDownloader
import org.openmrs.android.fhir.data.sync.SyncUrls
import org.openmrs.android.fhir.di.DefaultDispatcher
import org.openmrs.android.fhir.di.IoDispatcher
import timber.log.Timber

class SelectPatientListViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val applicationContext: Context,
  @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  val selectPatientListItems = MutableLiveData<List<SelectPatientListItem>>()
  private val _uiState = MutableStateFlow(SelectPatientListUiState())
  val uiState: StateFlow<SelectPatientListUiState>
    get() = _uiState

  private var started = false
  private var fromLogin = false
  private var refreshJob: Job? = null
  private val apiManager by lazy { ApiManager(applicationContext) }

  init {
    loadSelectedPatientLists()
  }

  /**
   * Shows what is already on the device at once and asks the server for the lists of the location
   * in the background (one small request, no reachability check first, no sync job): the screen
   * never waits for the network to show something. With [fromLogin] and lists that are not scoped
   * to the location, the first sync brings them and only the device is read.
   */
  fun start(fromLogin: Boolean) {
    if (started) return
    started = true
    this.fromLogin = fromLogin
    viewModelScope.launch {
      getSelectPatientListItems()
      val scopedToLocation = AppConfig.get(applicationContext).sync.filterPatientListsByLocation
      if (fromLogin && !scopedToLocation) return@launch
      // The location is saved a moment after it is picked, and may be saved again (another one
      // chosen): the lists follow it. Scoped to the location, nothing is asked before there is one.
      applicationContext.dataStore.data
        .map { it[PreferenceKeys.LOCATION_ID] }
        .distinctUntilChanged()
        .collectLatest { locationId ->
          if (scopedToLocation && locationId.isNullOrBlank()) return@collectLatest
          getSelectPatientListItems()
          refresh()
        }
    }
  }

  /** Asks the server again; the lists on screen stay until the new ones are saved. */
  fun refresh() {
    refreshJob?.cancel()
    refreshJob =
      viewModelScope.launch {
        _uiState.update { it.copy(isRefreshing = true, loadFailed = false) }
        try {
          val locationId = applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
          val groups =
            withTimeout(REFRESH_TIMEOUT_MILLIS) {
              withContext(ioDispatcher) {
                apiManager.fetchPatientLists(SyncUrls.groups(applicationContext, locationId))
              }
            }
          withContext(ioDispatcher) {
            // Stored the way a sync stores them: nothing here is a local change.
            fhirEngine.syncDownload(AcceptLocalConflictResolver) { flowOf(groups) }
          }
          getSelectPatientListItems()
        } catch (e: TimeoutCancellationException) {
          _uiState.update { it.copy(loadFailed = true) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not fetch the patient lists")
          _uiState.update { it.copy(loadFailed = true) }
        } finally {
          _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
        }
      }
  }

  suspend fun getSelectPatientListItems() {
    val filterPatientListsByGroup =
      AppConfig.get(applicationContext).sync.filterPatientListsByLocation
    val locationId = applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
    // Reading and filtering every Group can take a moment on big servers: keep it off main.
    val (groups, selectPatientListItemList) =
      withContext(defaultDispatcher) {
        val groups = fhirEngine.search<Group> {}.map { it.resource }
        val filteredGroups =
          if (filterPatientListsByGroup) {
            groups.filter { it.belongsToLocation(locationId) }
          } else {
            groups
          }
        groups to
          filteredGroups
            .sortedBy { it.name.orEmpty().lowercase() }
            .mapIndexed { index, group -> group.toSelectPatientListItem(index + 1) }
            .toMutableList()
      }
    if (filterPatientListsByGroup && groups.isNotEmpty()) {
      dropSelectionsOutside(selectPatientListItemList.map { it.resourceId }.toSet())
    }
    selectPatientListItems.value = selectPatientListItemList
    _uiState.update { it.copy(isLoading = false) }
  }

  /**
   * After a location change the previously ticked lists may belong to the old location; they must
   * not keep scoping the patient download.
   */
  private suspend fun dropSelectionsOutside(availableIds: Set<String>) {
    val selected = applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_PATIENT_LISTS]
    if (selected.isNullOrEmpty()) return
    val kept = selected.filter { it in availableIds }.toSet()
    if (kept.size == selected.size) return
    applicationContext.dataStore.edit { it[PreferenceKeys.SELECTED_PATIENT_LISTS] = kept }
    _uiState.update { it.copy(selectedPatientListIds = kept) }
  }

  fun onQueryChanged(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun onPatientListToggle(resourceId: String, isSelected: Boolean) {
    val updatedSelection = _uiState.value.selectedPatientListIds.toMutableSet()
    if (isSelected) {
      updatedSelection.remove(resourceId)
    } else {
      updatedSelection.add(resourceId)
    }
    AuditLog.record(
      AuditAction.PATIENT_LISTS_CHANGED,
      AuditOutcome.SUCCESS,
      "list" to resourceId,
      "selected" to !isSelected,
      "total" to updatedSelection.size,
    )
    _uiState.update { it.copy(selectedPatientListIds = updatedSelection) }
    val downloader = PatientListDownloader(applicationContext, fhirEngine)
    viewModelScope.launch {
      applicationContext.dataStore.edit { preferences ->
        preferences[PreferenceKeys.SELECTED_PATIENT_LISTS] = updatedSelection
      }
      if (isSelected) {
        downloader.remove(listOf(resourceId))
      } else if (!fromLogin) {
        // A list added later: its patients come now, if the server can be reached (at login the
        // first sync follows and brings them).
        downloader.add(listOf(resourceId))
        PatientListDownloader.schedule(applicationContext, fhirEngine)
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
      patientCount = if (hasQuantity()) quantity else member.size,
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

  /**
   * The location a patient list is tied to. The server publishes it as a plain string holding the
   * location uuid; a `Location/<uuid>` reference is accepted as well.
   */
  private fun org.hl7.fhir.r4.model.Extension.extractLocationReferenceId(): String? {
    if (url != GROUP_LOCATION_EXTENSION_URL) {
      return null
    }

    val raw =
      when (val extensionValue = value) {
        is StringType -> extensionValue.value
        is Reference -> extensionValue.reference
        else -> null
      }
    return raw?.substringAfterLast("/")?.trim()?.takeIf { it.isNotBlank() }
  }

  companion object {
    private const val REFRESH_TIMEOUT_MILLIS = 20_000L
    private const val GROUP_LOCATION_EXTENSION_URL = "http://fhir.openmrs.org/ext/group/location"
  }

  data class SelectPatientListItem(
    val id: String,
    val resourceId: String,
    val name: String,
    /** How many patients the list has, when known. */
    val patientCount: Int? = null,
  )

  data class SelectPatientListUiState(
    val query: String = "",
    val selectedPatientListIds: Set<String> = emptySet(),
    /** Nothing is known yet (the device has not been read): the screen shows placeholders. */
    val isLoading: Boolean = true,
    /** The server is being asked; what is on screen stays. */
    val isRefreshing: Boolean = false,
    /** The last attempt to reach the server did not work. */
    val loadFailed: Boolean = false,
  )
}
