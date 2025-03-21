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
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Group
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.extensions.isInternetAvailable
import timber.log.Timber

class SelectPatientListViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val apiManager: ApiManager,
) : ViewModel() {
  private var masterSelectPatientListItemList: MutableList<SelectPatientListItem> = mutableListOf()

  val selectPatientListItems = MutableLiveData<List<SelectPatientListItem>>()

  fun getSelectPatientListItems() {
    viewModelScope.launch {
      val selectPatientListItemList: MutableList<SelectPatientListItem> = mutableListOf()
      if (isInternetAvailable(applicationContext)) {
        try {
          apiManager.getPatientLists(applicationContext).let { response ->
            when (response) {
              is ApiResponse.Success<Bundle> -> {
                var selectPatientListItemEntry = response.data?.entry ?: emptyList()
                val selectPatientListItems =
                  selectPatientListItemEntry
                    .mapIndexed { index, entryComponent ->
                      (entryComponent.resource as Group).toSelectPatientListItem(index + 1)
                    }
                    .sortedBy { it.name }
                selectPatientListItemList.addAll(selectPatientListItems)
                purgeUnassignedPatientLists(selectPatientListItemEntry.map { it.resource as Group })
              }
              else -> {
                masterSelectPatientListItemList = selectPatientListItemList
                selectPatientListItems.value = selectPatientListItemList
                return@launch
              }
            }
          }
        } catch (e: Exception) {
          Timber.e(e.localizedMessage)
        }
      }
      masterSelectPatientListItemList = selectPatientListItemList
      selectPatientListItems.value = selectPatientListItemList
    }
  }

  private fun purgeUnassignedPatientLists(remotePatientLists: List<Group>) {
    viewModelScope.launch {
      val selectedPatientLists =
        applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_PATIENT_LISTS]

      selectedPatientLists?.forEach { patientListId ->
        if (patientListId !in remotePatientLists.map { it.idPart }) {
          applicationContext.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SELECTED_PATIENT_LISTS] =
              selectedPatientLists.minus(patientListId)
          }
        }
      }
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
