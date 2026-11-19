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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerCapabilities
import org.openmrs.android.fhir.data.remote.ServerFeatures
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.data.sync.PatientListSelection
import org.openmrs.android.fhir.data.sync.SyncUrls
import org.openmrs.android.fhir.di.IoDispatcher
import org.openmrs.android.fhir.forms.savedLocation
import org.openmrs.android.fhir.patientlists.PatientListEdit
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.patientlists.PatientListState
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.patientlists.atLocation
import timber.log.Timber

class SelectPatientListViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val applicationContext: Context,
  private val lists: PatientListsRepository,
  capabilities: ServerCapabilities,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  val features: StateFlow<ServerFeatures> = capabilities.features

  val selectPatientListItems = MutableLiveData<List<SelectPatientListItem>>()
  private val _uiState = MutableStateFlow(SelectPatientListUiState())
  val uiState: StateFlow<SelectPatientListUiState>
    get() = _uiState

  private val _messages = MutableSharedFlow<PatientListMessage>(extraBufferCapacity = 8)
  val messages: SharedFlow<PatientListMessage> = _messages

  private var started = false
  private var fromLogin = false
  private var refreshJob: Job? = null
  private val apiManager by lazy { ApiManager(applicationContext) }
  private val selection = PatientListSelection(applicationContext, fhirEngine)

  init {
    followSelection()
    showLists()
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
      val scopedToLocation = AppConfig.get(applicationContext).sync.filterPatientListsByLocation
      if (fromLogin && !scopedToLocation) return@launch
      // The location is saved a moment after it is picked, and may be saved again (another one
      // chosen): the lists follow it. Scoped to the location, nothing is asked before there is one.
      applicationContext.dataStore.data
        .map { it[PreferenceKeys.LOCATION_ID] }
        .distinctUntilChanged()
        .collectLatest { locationId ->
          if (scopedToLocation && locationId.isNullOrBlank()) return@collectLatest
          refresh()
        }
    }
  }

  /** Asks the server again; the lists on screen stay until the new ones are saved. */
  fun refresh() {
    if (GuestSession.isActive(applicationContext)) return
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
            lists.store(groups, complete = !scopedToLocation())
          }
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

  private fun scopedToLocation() =
    AppConfig.get(applicationContext).sync.filterPatientListsByLocation

  private fun showLists() {
    viewModelScope.launch {
      combine(
          lists.items,
          lists.pendingCount,
          applicationContext.dataStore.data
            .map { it[PreferenceKeys.LOCATION_ID] }
            .distinctUntilChanged(),
        ) { items, pending, locationId ->
          Triple(items, pending, locationId)
        }
        .collect { (items, pending, locationId) ->
          val scoped = scopedToLocation()
          val shown = items.atLocation(locationId, scoped)
          selectPatientListItems.value =
            shown.mapIndexed { index, list -> list.toSelectPatientListItem(index + 1) }
          if (scoped && items.isNotEmpty()) {
            dropSelectionsOutside(shown.map { it.id }.toSet())
          }
          _uiState.update { it.copy(isLoading = false, pendingChanges = pending) }
        }
    }
  }

  /**
   * After a location change the previously ticked lists may belong to the old location; they must
   * not keep scoping the patient download.
   */
  private suspend fun dropSelectionsOutside(availableIds: Set<String>) {
    val kept = selection.keepOnly(availableIds) ?: return
    _uiState.update { it.copy(selectedPatientListIds = kept) }
  }

  fun onQueryChanged(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun onPatientListToggle(resourceId: String, isSelected: Boolean) {
    val updatedSelection = _uiState.value.selectedPatientListIds.toMutableSet()
    if (isSelected) updatedSelection.remove(resourceId) else updatedSelection.add(resourceId)
    _uiState.update { it.copy(selectedPatientListIds = updatedSelection) }
    viewModelScope.launch {
      selection.set(
        resourceId,
        ticked = !isSelected,
        downloadNow = !fromLogin && !GuestSession.isActive(applicationContext),
      )
    }
  }

  fun attributeTypes(): List<String> = lists.attributeTypes()

  fun create(
    name: String,
    description: String,
    attributes: Map<String, String>,
    onResult: (PatientListEdit) -> Unit,
  ) {
    viewModelScope.launch {
      val location = applicationContext.savedLocation()
      val result =
        withContext(NonCancellable) {
          lists.create(name, description, location.id, location.name, attributes)
        }
      if (result is PatientListEdit.Saved) LiveSync.requestUpload(applicationContext)
      onResult(result)
    }
  }

  fun sendNow() {
    if (_uiState.value.sendingChanges) return
    viewModelScope.launch {
      _uiState.update { it.copy(sendingChanges = true) }
      val result = lists.send(retryRefused = true)
      if (!result.unreachable) lists.refreshList()
      _uiState.update { it.copy(sendingChanges = false) }
      _messages.tryEmit(result.toMessage())
    }
  }

  private fun followSelection() {
    viewModelScope.launch {
      selection.selected.collect { ids ->
        _uiState.update { it.copy(selectedPatientListIds = ids) }
      }
    }
  }

  private fun PatientListEntry.toSelectPatientListItem(position: Int) =
    SelectPatientListItem(
      id = position.toString(),
      resourceId = id,
      name = name,
      patientCount = quantity,
      locationName = locationName,
      tagRes = stateLabel(),
      tagIsError = error != null,
      selectable = state != PatientListState.NEW,
    )

  companion object {
    private const val REFRESH_TIMEOUT_MILLIS = 20_000L
  }

  data class SelectPatientListItem(
    val id: String,
    val resourceId: String,
    val name: String,
    /** How many patients the list has, when known. */
    val patientCount: Int? = null,
    val locationName: String? = null,
    val tagRes: Int? = null,
    val tagIsError: Boolean = false,
    val selectable: Boolean = true,
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
    val pendingChanges: Int = 0,
    val sendingChanges: Boolean = false,
  )
}
