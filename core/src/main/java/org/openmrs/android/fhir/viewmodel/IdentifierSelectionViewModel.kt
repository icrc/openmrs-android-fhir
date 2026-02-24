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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.IdentifierTypeManager
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.IdentifierType

class IdentifierSelectionViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val database: AppDatabase,
  private val identifierTypeManager: IdentifierTypeManager,
) : ViewModel() {
  private val _uiState = MutableStateFlow(IdentifierSelectionUiState())
  val uiState: StateFlow<IdentifierSelectionUiState>
    get() = _uiState

  init {
    loadSelectedIdentifiers()
  }

  fun loadIdentifiers() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      var identifiers = database.dao().getAllIdentifierTypes().toMutableList()
      if (identifiers.isEmpty()) {
        identifierTypeManager.fetchIdentifiers()
        identifiers = database.dao().getAllIdentifierTypes().toMutableList()
      }
      _uiState.update { it.copy(identifierTypes = identifiers, isLoading = false) }
    }
  }

  fun onQueryChanged(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun onIdentifierToggle(identifierType: IdentifierType, isSelected: Boolean) {
    if (identifierType.required) {
      return
    }
    val updated = _uiState.value.selectedIdentifierIds.toMutableSet()
    if (isSelected) {
      updated.remove(identifierType.uuid)
    } else {
      updated.add(identifierType.uuid)
    }
    _uiState.update { it.copy(selectedIdentifierIds = updated) }
    viewModelScope.launch {
      applicationContext.dataStore.edit { preferences ->
        preferences[PreferenceKeys.SELECTED_IDENTIFIER_TYPES] = updated
      }
    }
  }

  private fun loadSelectedIdentifiers() {
    viewModelScope.launch {
      val selected =
        applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]
          ?: emptySet()
      _uiState.update { it.copy(selectedIdentifierIds = selected) }
    }
  }

  data class IdentifierSelectionUiState(
    val query: String = "",
    val identifierTypes: List<IdentifierType> = emptyList(),
    val selectedIdentifierIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
  )
}
