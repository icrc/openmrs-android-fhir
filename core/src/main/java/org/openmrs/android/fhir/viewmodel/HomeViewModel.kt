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
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.count
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Patient
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.sync.PatientListSelection
import org.openmrs.android.fhir.data.transfer.DataTransfer
import org.openmrs.android.fhir.multiuser.DataVisibility
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.unidentified.UnidentifiedStore
import timber.log.Timber

sealed interface HomeEvent {
  data object NavigateToAddPatient : HomeEvent

  data object NavigateToPatientList : HomeEvent

  data object NavigateToCustomPatientList : HomeEvent

  data object NavigateToGroupEncounter : HomeEvent

  data object NavigateToSyncInfo : HomeEvent

  data object NavigateToImportExport : HomeEvent

  data class ShowMessage(@param:StringRes val messageResId: Int) : HomeEvent
}

/**
 * What the device holds, at a glance: patients (`null` when not worth counting), changes waiting
 * and patients to identify (recorded without being registered: no sync sends them).
 */
data class HomeSummary(val patients: Long?, val waiting: Int, val toIdentify: Int = 0)

data class HomePatientList(val id: String, val name: String, val patients: Int)

class HomeViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val apiManager: ApiManager,
  private val fhirEngine: FhirEngine,
  private val dataTransfer: DataTransfer,
  private val dataVisibility: DataVisibility,
  private val patientLists: PatientListsRepository,
  private val unidentified: UnidentifiedStore,
) : ViewModel() {
  private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
  val events: SharedFlow<HomeEvent> = _events

  private val _summary = MutableStateFlow<HomeSummary?>(null)
  val summary: StateFlow<HomeSummary?> = _summary

  val selectedLists: StateFlow<List<HomePatientList>> =
    combine(patientLists.items, PatientListSelection(applicationContext, fhirEngine).selected) {
        items,
        selected,
        ->
        if (GuestSession.isActive(applicationContext)) {
          emptyList()
        } else {
          items.filter { it.id in selected }.map { HomePatientList(it.id, it.name, it.quantity) }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  /**
   * Counts again: after a sync, or when the screen is back in front. Patients are not counted on a
   * device shared by several users (the list leaves some out per user), only the changes waiting.
   */
  fun refreshSummary() {
    viewModelScope.launch {
      try {
        val patients = if (dataVisibility.isSharedDevice()) null else fhirEngine.count<Patient> {}
        _summary.value =
          HomeSummary(
            patients = patients,
            waiting = dataTransfer.pendingCount() + patientLists.pendingNow(),
            toIdentify = unidentified.visibleTo(dataVisibility).size,
          )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.w(e, "Could not count what is on the device")
      }
    }
  }

  fun onNewPatientClicked() {
    handleLocationRequiredClick(HomeEvent.NavigateToAddPatient)
  }

  fun onPatientListClicked() {
    handleLocationRequiredClick(HomeEvent.NavigateToPatientList)
  }

  fun onCustomPatientListClicked() {
    // No reachability check first: the screen shows the lists saved on the device at once, asks the
    // server itself and says so when it cannot be reached.
    _events.tryEmit(HomeEvent.NavigateToCustomPatientList)
  }

  fun onGroupEncounterClicked() {
    _events.tryEmit(HomeEvent.NavigateToGroupEncounter)
  }

  fun onSyncInfoClicked() {
    _events.tryEmit(HomeEvent.NavigateToSyncInfo)
  }

  fun onImportExportClicked() {
    _events.tryEmit(HomeEvent.NavigateToImportExport)
  }

  private fun handleLocationRequiredClick(event: HomeEvent) {
    // No location needed to register or look up patients (a guest has none to pick; anyone else
    // may not have chosen one yet): what is saved gets it later.
    _events.tryEmit(event)
  }

  private companion object {
    const val STOP_TIMEOUT = 5_000L
  }
}
