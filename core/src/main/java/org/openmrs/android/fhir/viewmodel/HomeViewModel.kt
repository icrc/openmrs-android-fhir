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
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.extensions.getServerConnectivityState

sealed interface HomeEvent {
  data object NavigateToAddPatient : HomeEvent

  data object NavigateToPatientList : HomeEvent

  data object NavigateToCustomPatientList : HomeEvent

  data object NavigateToGroupEncounter : HomeEvent

  data object NavigateToSyncInfo : HomeEvent

  data object NavigateToUnsyncedResources : HomeEvent

  data class ShowMessage(@param:StringRes val messageResId: Int) : HomeEvent
}

class HomeViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val apiManager: ApiManager,
) : ViewModel() {
  private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
  val events: SharedFlow<HomeEvent> = _events

  fun onNewPatientClicked() {
    handleLocationRequiredClick(HomeEvent.NavigateToAddPatient)
  }

  fun onPatientListClicked() {
    handleLocationRequiredClick(HomeEvent.NavigateToPatientList)
  }

  fun onCustomPatientListClicked() {
    viewModelScope.launch {
      when (applicationContext.getServerConnectivityState(apiManager)) {
        ServerConnectivityState.ServerConnected ->
          _events.emit(HomeEvent.NavigateToCustomPatientList)
        ServerConnectivityState.InternetOnly ->
          _events.emit(HomeEvent.ShowMessage(R.string.server_unreachable_try_again_message))
        ServerConnectivityState.Offline ->
          _events.emit(HomeEvent.ShowMessage(R.string.connect_internet_to_select_patient_list))
      }
    }
  }

  fun onGroupEncounterClicked() {
    _events.tryEmit(HomeEvent.NavigateToGroupEncounter)
  }

  fun onSyncInfoClicked() {
    _events.tryEmit(HomeEvent.NavigateToSyncInfo)
  }

  fun onUnsyncedResourcesClicked() {
    _events.tryEmit(HomeEvent.NavigateToUnsyncedResources)
  }

  private fun handleLocationRequiredClick(event: HomeEvent) {
    viewModelScope.launch {
      val hasLocationSelected =
        applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_NAME] != null
      if (hasLocationSelected) {
        _events.emit(event)
      } else {
        _events.emit(HomeEvent.ShowMessage(R.string.please_select_a_location_first))
      }
    }
  }
}
