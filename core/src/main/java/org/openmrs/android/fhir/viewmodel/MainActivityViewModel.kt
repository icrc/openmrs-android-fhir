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
import android.net.ConnectivityManager
import android.net.Network
import android.text.format.DateFormat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.PeriodicSyncJobStatus
import com.google.android.fhir.sync.Sync
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hl7.fhir.r4.model.Patient
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.IdentifierTypeManager
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.data.remote.model.IdentifierWrapper
import org.openmrs.android.fhir.data.remote.model.SessionLocation
import org.openmrs.android.fhir.data.sync.FhirSyncWorker
import org.openmrs.android.fhir.extensions.getServerConnectivityState
import org.openmrs.android.fhir.ui.screens.SyncProgressState
import org.openmrs.android.fhir.worker.SyncInfoDatabaseWriterWorker

/** View model for [org.openmrs.android.fhir.MainActivity]. */
class MainActivityViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  @Assisted private val savedStateHandle: SavedStateHandle,
  val fhirEngine: FhirEngine,
  val database: AppDatabase,
  val apiManager: ApiManager,
  val identifierTypeManager: IdentifierTypeManager,
) : ViewModel() {
  @AssistedFactory
  interface Factory : org.openmrs.android.fhir.di.ViewModelAssistedFactory<MainActivityViewModel> {
    override fun create(
      handle: SavedStateHandle,
    ): MainActivityViewModel
  }

  private var _pollPeriodicSyncJobStatus: SharedFlow<PeriodicSyncJobStatus>? = null
  val pollPeriodicSyncJobStatus
    get() = _pollPeriodicSyncJobStatus

  private var _stopSync: Boolean = false
  val stopSync
    get() = _stopSync

  private val _lastSyncTimestampLiveData = MutableLiveData<String>()
  val lastSyncTimestampLiveData: LiveData<String>
    get() = _lastSyncTimestampLiveData

  private val _uiState =
    MutableStateFlow(
      MainUiState(
        drawerEnabled = true,
        syncHeaderText = applicationContext.getString(R.string.syncing_patient_data),
        showSyncCloseButton = true,
        networkStatusText = applicationContext.getString(R.string.offline),
        isNetworkStatusVisible = true,
        hasHandledPostLoginNavigation = savedStateHandle[KEY_POST_LOGIN_NAVIGATION_HANDLED]
            ?: false,
      ),
    )
  val uiState: StateFlow<MainUiState> = _uiState
  private val _uiEvents = MutableSharedFlow<MainActivityEvent>(extraBufferCapacity = 1)
  val uiEvents: SharedFlow<MainActivityEvent> = _uiEvents

  private val _isSyncing = MutableLiveData<Boolean>(false)
  val isSyncing: LiveData<Boolean> = _isSyncing

  private val syncMutex = Mutex()

  private val formatter =
    DateTimeFormatter.ofPattern(
      if (DateFormat.is24HourFormat(applicationContext)) formatString24 else formatString12,
    )

  private val _networkStatus =
    MutableStateFlow<ServerConnectivityState>(ServerConnectivityState.Offline)
  val networkStatus: StateFlow<ServerConnectivityState>
    get() = _networkStatus

  private var connectivityManager: ConnectivityManager =
    applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private var networkCallBack: ConnectivityManager.NetworkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        super.onAvailable(network)
        updateConnectivityState()
      }

      override fun onLost(network: Network) {
        super.onLost(network)
        updateConnectivityState()
      }
    }

  private val workManager = WorkManager.getInstance(applicationContext)

  val syncProgress: LiveData<List<WorkInfo>> =
    workManager.getWorkInfosForUniqueWorkLiveData(SyncInfoDatabaseWriterWorker.WORK_NAME)

  init {
    observeDrawerTitles()
    observeNetworkStatusVisibility()
    observeInProgressSyncSession()
  }

  fun triggerOneTimeSync(context: Context, fetchIdentifiers: Boolean = true) {
    viewModelScope.launch {
      val shouldSync =
        syncMutex.withLock {
          if (!stopSync && (_isSyncing.value != true)) {
            _isSyncing.postValue(true)
            true // Signal that we should proceed with sync
          } else {
            false // Don't sync
          }
        }

      if (shouldSync) {
        // Actual sync work happens outside the mutex
        if (fetchIdentifiers) {
          fetchIdentifierTypesIfEmpty()
          embeddIdentifierToUnsyncedPatients(context)
        }
        SyncInfoDatabaseWriterWorker.enqueue(applicationContext)
      }
    }
  }

  fun triggerIdentifierTypeSync() {
    viewModelScope.launch {
      if (!stopSync) {
        fetchIdentifierTypesIfEmpty()
      }
    }
  }

  private suspend fun embeddIdentifierToUnsyncedPatients(context: Context) {
    // Setting location session first.
    context.dataStore.data.first()[PreferenceKeys.LOCATION_ID]?.let {
      apiManager.setLocationSession(SessionLocation(it))
    }
    var filteredIdentifierTypes = setOf<String>()
    val identifierTypeToSourceIdMap = mutableMapOf<String, String>()
    val selectedIdentifierTypes =
      context.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.toList()
    if (selectedIdentifierTypes != null) {
      filteredIdentifierTypes =
        selectedIdentifierTypes
          .filter { identifierTypeId ->
            val identifierType = let { database.dao().getIdentifierTypeById(identifierTypeId) }
            val isAutoGenerated = identifierType?.isAutoGenerated
            if (isAutoGenerated == true) {
              identifierTypeToSourceIdMap[identifierTypeId] = identifierType.sourceId
            }
            isAutoGenerated != null && isAutoGenerated == true
          }
          .toSet()
    }

    val patients =
      fhirEngine
        .search<Patient> { filter(Patient.IDENTIFIER, { value = of("unsynced") }) }
        .map { it.resource }
        .toList()
    patients.forEach {
      val identifiers = it.identifier
      identifiers.forEach { identifier ->
        if (identifier.value in filteredIdentifierTypes) {
          val value =
            identifierTypeToSourceIdMap[identifier.value]?.let { it1 ->
              fetchIdentifierFromEndpoint(
                it1,
              )
            }
          identifier.value = value
        }
      }
      identifiers.removeAt(0)
      it.identifier = identifiers
      fhirEngine.update(it)
    }
  }

  private suspend fun fetchIdentifierFromEndpoint(identifierId: String): String? {
    val response = apiManager.getIdentifier(identifierId)
    return when (response) {
      is ApiResponse.Success<IdentifierWrapper> -> response.data?.identifier
      else -> null
    }
  }

  private suspend fun fetchIdentifierTypesIfEmpty() {
    val identifierTypes = database.dao().getIdentifierTypesCount()
    if (identifierTypes == 0) {
      identifierTypeManager.fetchIdentifiers()
    }
  }

  val inProgressSyncSession: LiveData<SyncSession?> =
    database.dao().getInProgressSyncSessionAsFlow().asLiveData()

  /** Emits last sync time. */
  fun updateLastSyncTimestamp(lastSync: OffsetDateTime? = null) {
    val formatted =
      lastSync?.let { it.toLocalDateTime()?.format(formatter) ?: "" }
        ?: Sync.getLastSyncTimestamp(applicationContext)?.toLocalDateTime()?.format(formatter) ?: ""
    _lastSyncTimestampLiveData.value = formatted
    _uiState.update { it.copy(lastSyncText = formatted) }
  }

  fun registerNetworkCallback() {
    connectivityManager.registerDefaultNetworkCallback(networkCallBack)
    updateConnectivityState()
  }

  // Unregister the network callback
  fun unregisterNetworkCallback() {
    connectivityManager.unregisterNetworkCallback(networkCallBack)
  }

  private fun updateConnectivityState() {
    viewModelScope.launch {
      val connectivityState = applicationContext.getServerConnectivityState(apiManager)
      _networkStatus.value = connectivityState
      _uiState.update { current ->
        current.copy(networkStatusText = networkStatusText(connectivityState))
      }
    }
  }

  fun cancelPeriodicSyncWorker(context: Context) {
    WorkManager.getInstance(context)
      .cancelUniqueWork("${FhirSyncWorker::class.java.name}-periodicSync")
  }

  fun setIsSyncing(isSyncing: Boolean) {
    _isSyncing.postValue(isSyncing)
  }

  fun setStopSync(stopSync: Boolean) {
    this._stopSync = stopSync
  }

  fun setDrawerEnabled(enabled: Boolean) {
    _uiState.update { it.copy(drawerEnabled = enabled) }
  }

  fun showSyncTasksScreen(headerTextResId: Int, showCloseButton: Boolean) {
    _uiState.update {
      it.copy(
        syncHeaderText = applicationContext.getString(headerTextResId),
        showSyncCloseButton = showCloseButton,
        isSyncTasksVisible = true,
      )
    }
  }

  fun hideSyncTasksScreen() {
    _uiState.update { it.copy(isSyncTasksVisible = false) }
  }

  fun updateSyncProgress(current: Int, total: Int) {
    _uiState.update { it.copy(syncProgressState = SyncProgressState(current, total)) }
  }

  fun updateLocationName(locationName: String) {
    _uiState.update { it.copy(locationMenuTitle = locationName) }
  }

  fun setLoading(isLoading: Boolean) {
    _uiState.update { it.copy(loading = isLoading) }
  }

  fun requestOpenDrawer() {
    _uiEvents.tryEmit(MainActivityEvent.OpenDrawer)
  }

  fun requestSync() {
    _uiEvents.tryEmit(MainActivityEvent.TriggerSync)
  }

  fun handleConnectivityState(
    connectivityState: ServerConnectivityState,
    fetchIdentifiers: Boolean,
  ) {
    val previousState =
      savedStateHandle.get<String>(KEY_LAST_CONNECTIVITY_STATE)?.let { toConnectivityState(it) }
    savedStateHandle[KEY_LAST_CONNECTIVITY_STATE] = fromConnectivityState(connectivityState)
    if (
      connectivityState is ServerConnectivityState.ServerConnected &&
        previousState !is ServerConnectivityState.ServerConnected &&
        isSyncing.value == true &&
        !hasShownContinueSyncPrompt()
    ) {
      savedStateHandle[KEY_CONTINUE_SYNC_PROMPT_SHOWN] = true
      _uiEvents.tryEmit(MainActivityEvent.ShowContinueSyncDialog(fetchIdentifiers))
    }
  }

  fun handleSyncWorkInfos(workInfos: List<WorkInfo>) {
    val workInfo = workInfos.firstOrNull() ?: return
    val workId = workInfo.id.toString()
    val lastWorkId: String? = savedStateHandle[KEY_LAST_SYNC_WORK_ID]
    if (lastWorkId != workId) {
      savedStateHandle[KEY_LAST_SYNC_WORK_ID] = workId
      savedStateHandle[KEY_CONTINUE_SYNC_PROMPT_SHOWN] = false
      savedStateHandle[KEY_LAST_PROGRESS_STATUS] = null
    }

    val lastState: String? = savedStateHandle[KEY_LAST_WORK_STATE]
    val currentState = workInfo.state.name
    val progressStatus = workInfo.progress.getString(SyncInfoDatabaseWriterWorker.PROGRESS_STATUS)

    when {
      workInfo.state == WorkInfo.State.RUNNING -> {
        if (
          progressStatus == "STARTED" &&
            savedStateHandle.get<String>(KEY_LAST_PROGRESS_STATUS) != "STARTED"
        ) {
          _uiEvents.tryEmit(MainActivityEvent.ShowSyncTasks)
          _uiEvents.tryEmit(MainActivityEvent.SyncStarted)
        }
        savedStateHandle[KEY_LAST_PROGRESS_STATUS] = progressStatus
      }
      workInfo.state == WorkInfo.State.SUCCEEDED -> {
        if (lastState != currentState) {
          _uiEvents.tryEmit(MainActivityEvent.HideSyncTasks)
          _uiEvents.tryEmit(MainActivityEvent.SyncCompleted)
          updateLastSyncTimestamp()
          setIsSyncing(false)
        }
      }
      workInfo.state == WorkInfo.State.FAILED -> {
        if (lastState != currentState) {
          _uiEvents.tryEmit(MainActivityEvent.HideSyncTasks)
          _uiEvents.tryEmit(MainActivityEvent.SyncFailed)
          updateLastSyncTimestamp()
          setIsSyncing(false)
        }
      }
    }

    savedStateHandle[KEY_LAST_WORK_STATE] = currentState
  }

  fun requestTokenExpiredDialog(connectivityState: ServerConnectivityState) {
    if (isTokenExpiredDialogShowing()) {
      return
    }
    savedStateHandle[KEY_TOKEN_DIALOG_SHOWING] = true
    _uiEvents.tryEmit(MainActivityEvent.ShowTokenExpiredDialog(connectivityState))
  }

  fun onTokenExpiredDialogDismissed() {
    savedStateHandle[KEY_TOKEN_DIALOG_SHOWING] = false
  }

  fun requestLogoutDialog(connectivityState: ServerConnectivityState) {
    _uiEvents.tryEmit(MainActivityEvent.ShowLogoutDialog(connectivityState))
  }

  fun hasHandledPostLoginNavigation(): Boolean =
    savedStateHandle[KEY_POST_LOGIN_NAVIGATION_HANDLED] ?: false

  fun setHasHandledPostLoginNavigation(hasHandled: Boolean) {
    savedStateHandle[KEY_POST_LOGIN_NAVIGATION_HANDLED] = hasHandled
    _uiState.update { it.copy(hasHandledPostLoginNavigation = hasHandled) }
  }

  private fun isTokenExpiredDialogShowing(): Boolean =
    savedStateHandle[KEY_TOKEN_DIALOG_SHOWING] ?: false

  private fun hasShownContinueSyncPrompt(): Boolean =
    savedStateHandle[KEY_CONTINUE_SYNC_PROMPT_SHOWN] ?: false

  private fun fromConnectivityState(state: ServerConnectivityState): String {
    return when (state) {
      ServerConnectivityState.ServerConnected -> "server"
      ServerConnectivityState.InternetOnly -> "internet"
      ServerConnectivityState.Offline -> "offline"
    }
  }

  private fun toConnectivityState(value: String): ServerConnectivityState {
    return when (value) {
      "server" -> ServerConnectivityState.ServerConnected
      "internet" -> ServerConnectivityState.InternetOnly
      else -> ServerConnectivityState.Offline
    }
  }

  fun updateNetworkStatusBannerTextForTest(text: String) {
    _uiState.update { it.copy(networkStatusText = text) }
  }

  fun updateLastSyncTextForTest(text: String) {
    _uiState.update { it.copy(lastSyncText = text) }
  }

  private fun observeDrawerTitles() {
    viewModelScope.launch {
      val locationName =
        applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_NAME]
          ?: applicationContext.getString(R.string.no_location_selected)

      val versionName =
        try {
          val packageInfo =
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
          packageInfo.versionName
        } catch (_: Exception) {
          "N/A"
        }
      _uiState.update {
        it.copy(locationMenuTitle = locationName, versionMenuTitle = "App Version: $versionName")
      }
    }
  }

  private fun observeNetworkStatusVisibility() {
    viewModelScope.launch {
      DemoDataStore(applicationContext).getCheckNetworkConnectivityFlow().collect { isVisible ->
        _uiState.update { it.copy(isNetworkStatusVisible = isVisible) }
      }
    }
  }

  private fun observeInProgressSyncSession() {
    viewModelScope.launch {
      database.dao().getInProgressSyncSessionAsFlow().collect { session ->
        if (session != null) {
          _uiState.update {
            it.copy(
              syncProgressState =
                SyncProgressState(
                  current = session.uploadedPatients + session.downloadedPatients,
                  total = session.totalPatientsToDownload + session.totalPatientsToUpload,
                ),
            )
          }
        }
      }
    }
  }

  private fun networkStatusText(connectivityState: ServerConnectivityState): String {
    return when (connectivityState) {
      ServerConnectivityState.ServerConnected ->
        applicationContext.getString(R.string.network_status_connected_to_server)
      ServerConnectivityState.InternetOnly ->
        applicationContext.getString(R.string.network_status_server_unreachable)
      ServerConnectivityState.Offline -> applicationContext.getString(R.string.offline)
    }
  }

  companion object {
    private const val formatString24 = "yyyy-MM-dd HH:mm:ss"
    private const val formatString12 = "yyyy-MM-dd hh:mm:ss a"
    private const val KEY_POST_LOGIN_NAVIGATION_HANDLED = "post_login_navigation_handled"
    private const val KEY_TOKEN_DIALOG_SHOWING = "token_dialog_showing"
    private const val KEY_LAST_CONNECTIVITY_STATE = "last_connectivity_state"
    private const val KEY_CONTINUE_SYNC_PROMPT_SHOWN = "continue_sync_prompt_shown"
    private const val KEY_LAST_SYNC_WORK_ID = "last_sync_work_id"
    private const val KEY_LAST_WORK_STATE = "last_work_state"
    private const val KEY_LAST_PROGRESS_STATUS = "last_progress_status"
  }
}

data class MainUiState(
  val drawerEnabled: Boolean = true,
  val isSyncTasksVisible: Boolean = false,
  val syncProgressState: SyncProgressState = SyncProgressState(0, 0),
  val syncHeaderText: String = "",
  val showSyncCloseButton: Boolean = true,
  val networkStatusText: String = "",
  val isNetworkStatusVisible: Boolean = true,
  val lastSyncText: String = "",
  val locationMenuTitle: String = "",
  val versionMenuTitle: String = "",
  val loading: Boolean = false,
  val hasHandledPostLoginNavigation: Boolean = false,
)

sealed class MainActivityEvent {
  data object OpenDrawer : MainActivityEvent()

  data object TriggerSync : MainActivityEvent()

  data object ShowSyncTasks : MainActivityEvent()

  data object HideSyncTasks : MainActivityEvent()

  data object SyncStarted : MainActivityEvent()

  data object SyncCompleted : MainActivityEvent()

  data object SyncFailed : MainActivityEvent()

  data class ShowContinueSyncDialog(
    val fetchIdentifiers: Boolean,
  ) : MainActivityEvent()

  data class ShowTokenExpiredDialog(
    val connectivityState: ServerConnectivityState,
  ) : MainActivityEvent()

  data class ShowLogoutDialog(
    val connectivityState: ServerConnectivityState,
  ) : MainActivityEvent()
}
