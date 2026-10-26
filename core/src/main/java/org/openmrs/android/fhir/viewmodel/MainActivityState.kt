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

import com.google.android.fhir.get
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.ui.screens.SyncProgressState

data class MainUiState(
  val drawerEnabled: Boolean = true,
  val isSyncTasksVisible: Boolean = false,
  val isSyncMinimized: Boolean = false,
  /** Any sync is running (manual or automatic). */
  val isSyncing: Boolean = false,
  /** Minutes until the automatic sync, null when none is scheduled (off, offline, guest). */
  val autoSyncCountdownMinutes: Int? = null,
  /** What live mode is doing, for the connection banner. */
  val liveStatus: LiveStatus = LiveStatus.Off,
  /** The automatic sync found the session invalid: the user has to sign in again. */
  val isSessionPromptPending: Boolean = false,
  /** The session is no longer valid: stays until the user signs in, unlike the prompt. */
  val sessionExpired: Boolean = false,
  val syncProgressState: SyncProgressState = SyncProgressState(0, 0),
  val syncHeaderText: String = "",
  val showSyncCloseButton: Boolean = true,
  val networkStatusText: String = "",
  val connectivityState: ServerConnectivityState = ServerConnectivityState.Offline,
  val isNetworkStatusVisible: Boolean = true,
  val lastSyncText: String = "",
  val locationMenuTitle: String = "",
  val versionMenuTitle: String = "",
  val loading: Boolean = false,
  val hasHandledPostLoginNavigation: Boolean = false,
)

/** Live mode as the connection banner shows it. */
enum class LiveStatus {
  Off,
  On,
  Sending,
  Retrying,
}

sealed class MainActivityEvent {
  data object OpenDrawer : MainActivityEvent()

  data object ShowIdentifierTypesRequired : MainActivityEvent()

  data object ShowLocationRequired : MainActivityEvent()

  data object TriggerSync : MainActivityEvent()

  data object ShowSyncTasks : MainActivityEvent()

  data object HideSyncTasks : MainActivityEvent()

  data object SyncStarted : MainActivityEvent()

  data object SyncCompleted : MainActivityEvent()

  data object SyncFailed : MainActivityEvent()

  data object SyncCancelled : MainActivityEvent()

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
