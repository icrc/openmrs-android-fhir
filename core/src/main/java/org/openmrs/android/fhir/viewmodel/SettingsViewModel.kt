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
import org.openmrs.android.fhir.data.SettingsRepository
import org.openmrs.android.fhir.data.sync.InitialSyncRunner

data class SettingsUiState(
  val isNetworkStatusVisible: Boolean = true,
  val isNotificationsEnabled: Boolean = true,
  val tokenCheckDelayMinutes: Int = DemoDataStore.INITIAL_TOKEN_CHECK_DELAY.toInt(),
  val periodicSyncDelayMinutes: Int = DemoDataStore.INITIAL_PERIODIC_SYNC_DELAY.toInt(),
  val isInitialSyncInProgress: Boolean = false,
)

sealed interface SettingsEvent {
  data object SettingsSaved : SettingsEvent

  data object SettingsDiscarded : SettingsEvent

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
  }

  fun onNotificationsToggle(enabled: Boolean) {
    _uiState.update { it.copy(isNotificationsEnabled = enabled) }
  }

  fun onTokenDelaySelected(minutes: Int) {
    _uiState.update { it.copy(tokenCheckDelayMinutes = minutes) }
  }

  fun onPeriodicSyncDelaySelected(minutes: Int) {
    _uiState.update { it.copy(periodicSyncDelayMinutes = minutes) }
  }

  fun onSaveClicked() {
    viewModelScope.launch {
      val currentState = uiState.value
      settingsRepository.setNetworkStatusVisible(currentState.isNetworkStatusVisible)
      settingsRepository.setNotificationsEnabled(currentState.isNotificationsEnabled)
      settingsRepository.setTokenExpiryDelayMinutes(currentState.tokenCheckDelayMinutes)
      settingsRepository.setPeriodicSyncDelayMinutes(currentState.periodicSyncDelayMinutes)
      _events.emit(SettingsEvent.SettingsSaved)
    }
  }

  fun onCancelClicked() {
    _events.tryEmit(SettingsEvent.SettingsDiscarded)
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
    _uiState.update {
      it.copy(
        isNetworkStatusVisible = networkStatusVisible,
        isNotificationsEnabled = notificationsEnabled,
        tokenCheckDelayMinutes = tokenDelayMinutes,
        periodicSyncDelayMinutes = periodicDelayMinutes,
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
      else -> Unit
    }
  }

  private fun resetInitialSyncState() {
    initialSyncJob = null
    _uiState.update { it.copy(isInitialSyncInProgress = false) }
  }
}
