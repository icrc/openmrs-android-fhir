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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.sync.PatientListSelection
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.patientlists.PatientListState
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.patientlists.atLocation

data class GroupSessionListUiState(
  val lists: List<PatientListEntry> = emptyList(),
  val loading: Boolean = true,
)

class GroupSessionListViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val repository: PatientListsRepository,
  fhirEngine: FhirEngine,
) : ViewModel() {
  private val selection = PatientListSelection(applicationContext, fhirEngine)

  private val _uiState = MutableStateFlow(GroupSessionListUiState())
  val uiState: StateFlow<GroupSessionListUiState> = _uiState

  init {
    viewModelScope.launch {
      combine(
          repository.items,
          applicationContext.dataStore.data
            .map { it[PreferenceKeys.LOCATION_ID] }
            .distinctUntilChanged(),
          selection.selected,
        ) { items, locationId, downloaded ->
          val scoped = AppConfig.get(applicationContext).sync.filterPatientListsByLocation
          items
            .filter {
              it.state != PatientListState.DELETING &&
                (it.id in downloaded || it.state == PatientListState.NEW)
            }
            .atLocation(locationId, scoped)
        }
        .collect { shown -> _uiState.update { it.copy(lists = shown, loading = false) } }
    }
    viewModelScope.launch { repository.refreshList() }
  }
}
