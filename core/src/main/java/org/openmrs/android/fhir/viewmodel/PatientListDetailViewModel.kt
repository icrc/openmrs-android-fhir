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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.data.database.model.PatientListEvent
import org.openmrs.android.fhir.data.remote.ServerCapabilities
import org.openmrs.android.fhir.data.remote.ServerFeatures
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.data.sync.PatientListSelection
import org.openmrs.android.fhir.patientlists.PatientListEdit
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.patientlists.PatientListMember
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.patientlists.RefreshResult

data class PatientListDetailUiState(
  val patientList: PatientListEntry? = null,
  val members: List<PatientListMember> = emptyList(),
  val totalMembers: Int = 0,
  val query: String = "",
  val loaded: Boolean = false,
  val loadingMembers: Boolean = false,
  val isRefreshing: Boolean = false,
  val refreshFailed: Boolean = false,
  val gone: Boolean = false,
  val sending: Boolean = false,
  val downloaded: Boolean = false,
  val features: ServerFeatures = ServerFeatures(),
  val history: List<PatientListEvent> = emptyList(),
)

class PatientListDetailViewModel
@Inject
constructor(
  private val repository: PatientListsRepository,
  private val applicationContext: Context,
  fhirEngine: FhirEngine,
  private val capabilities: ServerCapabilities,
) : ViewModel() {
  private val selection = PatientListSelection(applicationContext, fhirEngine)
  private val listId = MutableStateFlow<String?>(null)
  private val query = MutableStateFlow("")
  private val activity = MutableStateFlow(Activity())

  private val _messages = MutableSharedFlow<PatientListMessage>(extraBufferCapacity = 8)
  val messages: SharedFlow<PatientListMessage> = _messages

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<PatientListDetailUiState> =
    listId
      .filterNotNull()
      .flatMapLatest { id ->
        combine(
          repository.item(id),
          repository.members(id),
          query,
          activity,
          combine(selection.selected, capabilities.features, repository.history(id)) {
            selected,
            features,
            history,
            ->
            Triple(selected, features, history)
          },
        ) { patientList, members, text, activity, (selected, features, history) ->
          PatientListDetailUiState(
            patientList = patientList,
            members = members.filter { it.matches(text) },
            totalMembers = members.size,
            query = text,
            loaded = true,
            loadingMembers =
              activity.refreshing &&
                patientList != null &&
                !patientList.membersLoaded &&
                members.isEmpty(),
            isRefreshing = activity.refreshing,
            refreshFailed = activity.refreshFailed,
            gone = activity.gone,
            sending = activity.sending,
            downloaded = id in selected,
            features = features,
            history = history,
          )
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT),
        PatientListDetailUiState(),
      )

  fun start(id: String) {
    if (listId.value == id) return
    listId.value = id
    refresh()
  }

  suspend fun membersForExport(): List<PatientListMember> {
    val id = listId.value ?: return emptyList()
    return repository.members(id).first()
  }

  fun onQueryChange(text: String) {
    query.value = text
  }

  fun setDownloaded(downloaded: Boolean) {
    val id = listId.value ?: return
    viewModelScope.launch { selection.set(id, ticked = downloaded, downloadNow = true) }
  }

  fun refresh() {
    val id = listId.value ?: return
    viewModelScope.launch {
      activity.update { it.copy(refreshing = true) }
      val result = repository.refreshMembers(id)
      activity.update {
        it.copy(
          refreshing = false,
          refreshFailed = result == RefreshResult.UNREACHABLE,
          gone = result == RefreshResult.GONE,
        )
      }
    }
  }

  fun edit(name: String, description: String, onResult: (PatientListEdit) -> Unit) {
    val id = listId.value ?: return
    viewModelScope.launch {
      val result = withContext(NonCancellable) { repository.edit(id, name, description) }
      if (result is PatientListEdit.Saved) changed()
      onResult(result)
    }
  }

  fun delete(onDone: () -> Unit) {
    val id = listId.value ?: return
    viewModelScope.launch {
      withContext(NonCancellable) { repository.delete(id) }
      changed()
      onDone()
    }
  }

  fun undoDelete() {
    val id = listId.value ?: return
    viewModelScope.launch { repository.undoDelete(id) }
  }

  fun removeMember(patientId: String) {
    val id = listId.value ?: return
    viewModelScope.launch {
      withContext(NonCancellable) { repository.removeMember(id, patientId) }
      changed()
    }
  }

  fun undoMember(patientId: String) {
    val id = listId.value ?: return
    viewModelScope.launch { repository.undoMemberChange(id, patientId) }
  }

  fun discardChanges() {
    val id = listId.value ?: return
    viewModelScope.launch { repository.discardChanges(id) }
  }

  fun sendNow() {
    val id = listId.value ?: return
    if (activity.value.sending) return
    viewModelScope.launch {
      activity.update { it.copy(sending = true) }
      val result = repository.send(retryRefused = true)
      if (!result.unreachable) repository.refreshMembers(id)
      activity.update { it.copy(sending = false) }
      _messages.tryEmit(result.toMessage())
    }
  }

  private fun changed() {
    LiveSync.requestUpload(applicationContext)
  }

  private data class Activity(
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val gone: Boolean = false,
    val sending: Boolean = false,
  )

  private companion object {
    const val STOP_TIMEOUT = 5_000L
  }
}

internal fun PatientListMember.matches(text: String): Boolean {
  val wanted = text.trim()
  if (wanted.isEmpty()) return true
  return wanted.split(Regex("\\s+")).all { word ->
    name.contains(word, ignoreCase = true) || identifier.orEmpty().contains(word, ignoreCase = true)
  }
}
