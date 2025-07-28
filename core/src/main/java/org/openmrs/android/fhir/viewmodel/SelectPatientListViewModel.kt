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
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.CurrentSyncJobStatus
import com.google.android.fhir.sync.Sync
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Group
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

  fun getSelectPatientListItems() {
    viewModelScope.launch {
      val selectPatientListItemList: MutableList<SelectPatientListItem> = mutableListOf()
      fhirEngine
        .search<Group> {}
        .mapIndexed { index, fhirGroup -> fhirGroup.resource.toSelectPatientListItem(index + 1) }
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

  private fun Group.toSelectPatientListItem(position: Int): SelectPatientListItem {
    return SelectPatientListItem(
      id = position.toString(),
      resourceId = idElement.idPart,
      name = name,
    )
  }

  data class SelectPatientListItem(
    val id: String,
    val resourceId: String,
    val name: String,
  )
}
