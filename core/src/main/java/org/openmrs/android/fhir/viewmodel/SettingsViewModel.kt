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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.sync.CurrentSyncJobStatus
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.data.SettingsRepository
import org.openmrs.android.fhir.data.sync.InitialSyncRunner
import org.openmrs.android.fhir.data.sync.SyncMode

data class SettingsUiState(
  val isNetworkStatusVisible: Boolean = true,
  val isNotificationsEnabled: Boolean = true,
  val tokenCheckDelayMinutes: Int = DemoDataStore.INITIAL_TOKEN_CHECK_DELAY.toInt(),
  val periodicSyncDelayMinutes: Int = DemoDataStore.INITIAL_PERIODIC_SYNC_DELAY.toInt(),
  val isAutoSyncEnabled: Boolean = true,
  /** What the automatic sync does: upload only, or upload and download. */
  val autoSyncMode: SyncMode = SyncMode.BOTH,
  val isLiveModeEnabled: Boolean = false,
  val keepLivePatients: Boolean = true,
  val automaticVisits: Boolean = true,
  /** The user signed in now; the sharing setting is theirs. None for a guest. */
  val signedInUser: String? = null,
  val shareMyData: Boolean = false,
  val rememberLocation: Boolean = true,
  val isInitialSyncInProgress: Boolean = false,
)

sealed interface SettingsEvent {
  data object SettingsReset : SettingsEvent

  data object InitialSyncStarted : SettingsEvent

  data object InitialSyncCompleted : SettingsEvent

  data object InitialSyncFailed : SettingsEvent
}

class SettingsViewModel
@Inject
constructor(
  private val settingsRepository: SettingsRepository,
  private val initialSyncRunner: InitialSyncRunner,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState

  private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
  val events: SharedFlow<SettingsEvent> = _events

  private var initialSyncJob: Job? = null
  private var hasEmittedInitialSyncStarted = false

  init {
    viewModelScope.launch { loadSettings() }
  }

  fun onNetworkStatusToggle(enabled: Boolean) {
    _uiState.update { it.copy(isNetworkStatusVisible = enabled) }
    persist("network_status_visible", enabled) {
      settingsRepository.setNetworkStatusVisible(enabled)
    }
  }

  fun onNotificationsToggle(enabled: Boolean) {
    _uiState.update { it.copy(isNotificationsEnabled = enabled) }
    persist("notifications", enabled) { settingsRepository.setNotificationsEnabled(enabled) }
  }

  fun onTokenDelaySelected(minutes: Int) {
    _uiState.update { it.copy(tokenCheckDelayMinutes = minutes) }
    persist("token_check_delay", minutes) { settingsRepository.setTokenExpiryDelayMinutes(minutes) }
  }

  fun onAutoSyncToggle(enabled: Boolean) {
    _uiState.update { it.copy(isAutoSyncEnabled = enabled) }
    persist("auto_sync", enabled) { settingsRepository.setAutoSyncEnabled(enabled) }
  }

  fun onAutoSyncModeSelected(mode: SyncMode) {
    _uiState.update { it.copy(autoSyncMode = mode) }
    persist("auto_sync_mode", mode.value) { settingsRepository.setAutoSyncMode(mode) }
  }

  fun onLiveModeToggle(enabled: Boolean) {
    _uiState.update { it.copy(isLiveModeEnabled = enabled) }
    persist("live_mode", enabled) { settingsRepository.setLiveModeEnabled(enabled) }
  }

  fun onKeepLivePatientsToggle(keep: Boolean) {
    _uiState.update { it.copy(keepLivePatients = keep) }
    persist("keep_live_patients", keep) { settingsRepository.setKeepLivePatients(keep) }
  }

  fun onAutomaticVisitsToggle(automatic: Boolean) {
    _uiState.update { it.copy(automaticVisits = automatic) }
    persist("automatic_visits", automatic) { settingsRepository.setAutomaticVisits(automatic) }
  }

  fun onShareMyDataToggle(share: Boolean) {
    _uiState.update { it.copy(shareMyData = share) }
    // Both belong to the signed-in user: nobody signed in, nothing to keep.
    if (uiState.value.signedInUser != null) {
      persist("share_my_data", share) { settingsRepository.setShareMyData(share) }
    }
  }

  fun onRememberLocationToggle(remember: Boolean) {
    _uiState.update { it.copy(rememberLocation = remember) }
    if (uiState.value.signedInUser != null) {
      persist("remember_location", remember) { settingsRepository.setRememberLocation(remember) }
    }
  }

  fun onPeriodicSyncDelaySelected(minutes: Int) {
    _uiState.update { it.copy(periodicSyncDelayMinutes = minutes) }
    persist("periodic_sync_delay", minutes) {
      settingsRepository.setPeriodicSyncDelayMinutes(minutes)
    }
  }

  /** A setting changed: it is kept at once (there is no Save), and noted for the support team. */
  private fun persist(name: String, value: Any, save: suspend () -> Unit) {
    viewModelScope.launch {
      save()
      AuditLog.record(
        AuditAction.SETTINGS_CHANGED,
        AuditOutcome.SUCCESS,
        "changed" to listOf("$name=$value"),
      )
    }
  }

  /** Every value of this page that lives here goes back to what the app starts with. */
  fun onResetToDefaults() {
    val defaults = SettingsUiState()
    _uiState.update {
      it.copy(
        isNetworkStatusVisible = defaults.isNetworkStatusVisible,
        isNotificationsEnabled = defaults.isNotificationsEnabled,
        tokenCheckDelayMinutes = defaults.tokenCheckDelayMinutes,
        periodicSyncDelayMinutes = defaults.periodicSyncDelayMinutes,
        isAutoSyncEnabled = defaults.isAutoSyncEnabled,
        autoSyncMode = defaults.autoSyncMode,
        isLiveModeEnabled = defaults.isLiveModeEnabled,
        keepLivePatients = defaults.keepLivePatients,
        automaticVisits = defaults.automaticVisits,
        shareMyData = defaults.shareMyData,
        rememberLocation = defaults.rememberLocation,
      )
    }
    viewModelScope.launch {
      settingsRepository.setNetworkStatusVisible(defaults.isNetworkStatusVisible)
      settingsRepository.setNotificationsEnabled(defaults.isNotificationsEnabled)
      settingsRepository.setTokenExpiryDelayMinutes(defaults.tokenCheckDelayMinutes)
      settingsRepository.setPeriodicSyncDelayMinutes(defaults.periodicSyncDelayMinutes)
      settingsRepository.setAutoSyncEnabled(defaults.isAutoSyncEnabled)
      settingsRepository.setAutoSyncMode(defaults.autoSyncMode)
      settingsRepository.setLiveModeEnabled(defaults.isLiveModeEnabled)
      settingsRepository.setKeepLivePatients(defaults.keepLivePatients)
      settingsRepository.setAutomaticVisits(defaults.automaticVisits)
      if (uiState.value.signedInUser != null) {
        settingsRepository.setShareMyData(defaults.shareMyData)
        settingsRepository.setRememberLocation(defaults.rememberLocation)
      }
      _events.emit(SettingsEvent.SettingsReset)
    }
  }

  fun onInitialSyncClicked() {
    if (initialSyncJob?.isActive == true) {
      return
    }

    hasEmittedInitialSyncStarted = false
    _uiState.update { it.copy(isInitialSyncInProgress = true) }

    initialSyncJob =
      viewModelScope.launch {
        initialSyncRunner.runInitialSync().collect { status -> handleInitialSyncStatus(status) }
      }
  }

  private suspend fun loadSettings() {
    val networkStatusVisible = settingsRepository.getNetworkStatusVisible()
    val notificationsEnabled = settingsRepository.getNotificationsEnabled()
    val tokenDelayMinutes = settingsRepository.getTokenExpiryDelayMinutes()
    val periodicDelayMinutes = settingsRepository.getPeriodicSyncDelayMinutes()
    val autoSyncEnabled = settingsRepository.getAutoSyncEnabled()
    val autoSyncMode = settingsRepository.getAutoSyncMode()
    val liveModeEnabled = settingsRepository.getLiveModeEnabled()
    val keepLivePatients = settingsRepository.getKeepLivePatients()
    val automaticVisits = settingsRepository.getAutomaticVisits()
    val signedInUser = settingsRepository.getSignedInUser()
    val shareMyData = settingsRepository.getShareMyData()
    val rememberLocation = settingsRepository.getRememberLocation()
    _uiState.update {
      it.copy(
        isNetworkStatusVisible = networkStatusVisible,
        isNotificationsEnabled = notificationsEnabled,
        tokenCheckDelayMinutes = tokenDelayMinutes,
        periodicSyncDelayMinutes = periodicDelayMinutes,
        isAutoSyncEnabled = autoSyncEnabled,
        autoSyncMode = autoSyncMode,
        isLiveModeEnabled = liveModeEnabled,
        keepLivePatients = keepLivePatients,
        automaticVisits = automaticVisits,
        signedInUser = signedInUser,
        shareMyData = shareMyData,
        rememberLocation = rememberLocation,
      )
    }
  }

  private suspend fun handleInitialSyncStatus(status: CurrentSyncJobStatus) {
    when (status) {
      is CurrentSyncJobStatus.Succeeded -> {
        _events.emit(SettingsEvent.InitialSyncCompleted)
        resetInitialSyncState()
      }
      is CurrentSyncJobStatus.Failed,
      is CurrentSyncJobStatus.Cancelled,
      is CurrentSyncJobStatus.Blocked, -> {
        _events.emit(SettingsEvent.InitialSyncFailed)
        resetInitialSyncState()
      }
      is CurrentSyncJobStatus.Running,
      CurrentSyncJobStatus.Enqueued, -> {
        if (!hasEmittedInitialSyncStarted) {
          _events.emit(SettingsEvent.InitialSyncStarted)
          hasEmittedInitialSyncStarted = true
        }
      }
    }
  }

  private fun resetInitialSyncState() {
    initialSyncJob = null
    _uiState.update { it.copy(isInitialSyncInProgress = false) }
  }
}
