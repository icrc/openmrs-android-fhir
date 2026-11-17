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
import com.google.android.fhir.LocalChange
import com.google.android.fhir.get
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.sync.LivePatients
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.multiuser.DataVisibility
import org.openmrs.android.fhir.patientlists.GroupMapper
import org.openmrs.android.fhir.patientlists.MemberPending
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.patientlists.PatientListState
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.patientlists.PatientSummary
import org.openmrs.android.fhir.patientlists.RefreshResult
import org.openmrs.android.fhir.patientlists.patientsById
import org.openmrs.android.fhir.unidentified.UnidentifiedPatients
import timber.log.Timber

data class PickerCandidate(
  val patient: PatientSummary,
  val onDevice: Boolean,
  val alreadyMember: Boolean,
  val notSentYet: Boolean,
) {
  val selectable: Boolean
    get() = !alreadyMember
}

data class PatientListPickerUiState(
  val query: String = "",
  val candidates: List<PickerCandidate> = emptyList(),
  val selected: Set<String> = emptySet(),
  val loading: Boolean = true,
  val serverLoading: Boolean = false,
  val serverFailed: Boolean = false,
  val serverSearching: Boolean = false,
  /**
   * Choosing for a group session: only the patients of the device (the forms are filled in here),
   * and nothing is added to the patient list now.
   */
  val session: Boolean = false,
  val adding: Boolean = false,
  val otherLists: List<PatientListEntry> = emptyList(),
  val loadingOther: Boolean = false,
) {
  val allSelected: Boolean
    get() {
      val choices = candidates.filter { it.selectable }
      return choices.isNotEmpty() && choices.all { it.patient.id in selected }
    }
}

class PatientListPickerViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val dataVisibility: DataVisibility,
  private val repository: PatientListsRepository,
  private val unidentified: UnidentifiedPatients,
  private val applicationContext: Context,
) : ViewModel() {
  private val _uiState = MutableStateFlow(PatientListPickerUiState())
  val uiState: StateFlow<PatientListPickerUiState> = _uiState

  private val apiManager by lazy { ApiManager(applicationContext) }
  private var listId: String? = null
  private var localJob: Job? = null
  private var serverJob: Job? = null
  private var local: List<PatientSummary> = emptyList()
  private var server: List<PatientSummary> = emptyList()

  private var fromLists: List<PatientSummary> = emptyList()
  private var fromListsOnDevice: Set<String> = emptySet()
  private val known = HashMap<String, PatientSummary>()
  private val notSent = HashSet<String>()

  fun start(id: String, session: Boolean = false) {
    if (listId == id) return
    listId = id
    _uiState.update { it.copy(session = session) }
    searchLocal("")
  }

  fun onQueryChange(text: String) {
    _uiState.update { it.copy(query = text) }
    searchLocal(text)
    searchServer(text)
  }

  fun toggle(patientId: String) {
    _uiState.update {
      it.copy(
        selected =
          if (patientId in it.selected) it.selected - patientId else it.selected + patientId,
      )
    }
  }

  fun selectAll(select: Boolean) {
    val ids = _uiState.value.candidates.filter { it.selectable }.map { it.patient.id }
    _uiState.update {
      it.copy(selected = if (select) it.selected + ids else it.selected - ids.toSet())
    }
  }

  fun loadOtherLists() {
    val id = listId ?: return
    viewModelScope.launch {
      val lists =
        repository.items
          .first()
          .filter { it.id != id && it.state != PatientListState.DELETING }
          .sortedBy { it.name.lowercase() }
      _uiState.update { it.copy(otherLists = lists) }
    }
  }

  /**
   * Chooses the members of another patient list: they are shown, whoever they are, and chosen. A
   * list whose members the device has not read yet is read first (it needs the server).
   *
   * @param onDone how many were chosen, and whether the members could not be read
   */
  fun addFromList(otherId: String, onDone: (chosen: Int, unreachable: Boolean) -> Unit) {
    viewModelScope.launch {
      _uiState.update { it.copy(loadingOther = true) }
      val other = _uiState.value.otherLists.firstOrNull { it.id == otherId }
      var unreachable = false
      if (other != null && !other.membersLoaded) {
        unreachable = repository.refreshMembers(otherId) == RefreshResult.UNREACHABLE
      }
      val members =
        repository
          .members(otherId)
          .first()
          .filter { it.pending != MemberPending.REMOVE && !it.toIdentify }
          .map { PatientSummary(it.patientId, it.name, it.gender, it.birthDate, it.identifier) }
      if (members.isNotEmpty()) {
        fromLists = (fromLists + members).distinctBy { it.id }
        fromListsOnDevice =
          dataVisibility
            .visibleAmong(fhirEngine.patientsById(fromLists.map { it.id }), emptySet()) {
              it.idElement.idPart
            }
            .mapTo(HashSet()) { it.idElement.idPart }
        publishNow()
        val taken = members.mapTo(HashSet()) { it.id }
        val choices = _uiState.value.candidates.filter { it.selectable && it.patient.id in taken }
        _uiState.update { state ->
          state.copy(
            selected = state.selected + choices.map { it.patient.id },
            loadingOther = false,
          )
        }
        onDone(choices.size, unreachable)
      } else {
        _uiState.update { it.copy(loadingOther = false) }
        onDone(0, unreachable)
      }
    }
  }

  /**
   * Adds the chosen patients to the patient list (on the device; they are sent later). For a group
   * session nothing is added: the ids are handed back, and join the list when the session starts.
   */
  fun addSelected(onDone: (added: Int, failed: Int) -> Unit) {
    val id = listId ?: return
    if (_uiState.value.adding) return
    val chosen = _uiState.value.selected.mapNotNull { known[it] }
    if (_uiState.value.session) {
      bringToDevice(chosen, onDone)
      return
    }
    viewModelScope.launch {
      val unsent = chosen.mapNotNullTo(HashSet()) { it.id.takeIf { id -> id in notSent } }
      withContext(NonCancellable) { repository.addMembers(id, chosen, unsent) }
      if (chosen.isNotEmpty()) LiveSync.requestUpload(applicationContext)
      onDone(chosen.size, 0)
    }
  }

  /**
   * For a session: the patients chosen from the server are downloaded first (the forms are filled
   * in on this device); one that cannot be is left out of the choice.
   */
  private fun bringToDevice(chosen: List<PatientSummary>, onDone: (Int, Int) -> Unit) {
    val here = _uiState.value.candidates.filter { it.onDevice }.mapTo(HashSet()) { it.patient.id }
    val remote = chosen.filter { it.id !in here }
    if (remote.isEmpty()) {
      onDone(chosen.size, 0)
      return
    }
    _uiState.update { it.copy(adding = true) }
    viewModelScope.launch {
      val live = LivePatients(applicationContext, fhirEngine)
      val failed = mutableSetOf<String>()
      for (patient in remote) {
        try {
          withTimeout(DOWNLOAD_TIMEOUT_MILLIS) {
            live.download(patient.id, remember = true, keep = true)
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not bring a patient of the server to the device")
          failed += patient.id
        }
      }
      _uiState.update { it.copy(adding = false, selected = it.selected - failed) }
      onDone(chosen.size - failed.size, failed.size)
    }
  }

  private fun searchLocal(text: String) {
    localJob?.cancel()
    localJob =
      viewModelScope.launch {
        // Typing is fast and the search is a database query: a pause before it saves most of them.
        if (text.isNotEmpty()) delay(LOCAL_DEBOUNCE_MILLIS)
        local =
          try {
            findOnDevice(text)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Timber.w(e, "Could not search the patients of the device")
            emptyList()
          }
        publish(loading = false)
      }
  }

  private suspend fun findOnDevice(text: String): List<PatientSummary> {
    val found =
      fhirEngine
        .search<Patient> {
          applyPatientTextQuery(text)
          sort(Patient.NAME, Order.ASCENDING)
          count = DEVICE_LIMIT
          from = 0
        }
        .map { it.resource }
    return dataVisibility
      .visibleAmong(found, emptySet()) { it.idElement.idPart }
      .map { GroupMapper.summary(it) }
  }

  fun selectedIds(): ArrayList<String> = ArrayList(_uiState.value.selected)

  fun addPatientToIdentify(note: String, onDone: () -> Unit) {
    val id = listId ?: return
    viewModelScope.launch {
      val placeholder = unidentified.create(note)
      val patient = fhirEngine.get<Patient>(placeholder)
      withContext(NonCancellable) {
        repository.addMembers(id, listOf(GroupMapper.summary(patient)))
      }
      onDone()
    }
  }

  private fun searchServer(text: String) {
    serverJob?.cancel()
    val wanted = text.trim()
    if (wanted.length < SERVER_MIN_CHARS) {
      server = emptyList()
      _uiState.update {
        it.copy(serverSearching = false, serverLoading = false, serverFailed = false)
      }
      publish()
      return
    }
    _uiState.update { it.copy(serverSearching = true, serverLoading = true, serverFailed = false) }
    serverJob =
      viewModelScope.launch {
        delay(SERVER_DEBOUNCE_MILLIS)
        // A session is held with patients on the device: the server is searched only in live mode,
        // which can bring the chosen ones here.
        if (
          GuestSession.isActive(applicationContext) ||
            (_uiState.value.session && !LiveSync.isAvailable(applicationContext))
        ) {
          server = emptyList()
          _uiState.update {
            it.copy(serverSearching = false, serverLoading = false, serverFailed = false)
          }
          publish()
          return@launch
        }
        try {
          server = apiManager.searchPatients(wanted).map(GroupMapper::summary)
          _uiState.update { it.copy(serverLoading = false, serverFailed = false) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: IOException) {
          server = emptyList()
          _uiState.update { it.copy(serverLoading = false, serverFailed = true) }
        } catch (e: Exception) {
          Timber.w(e, "Could not search the patients of the server")
          server = emptyList()
          _uiState.update { it.copy(serverLoading = false, serverFailed = true) }
        }
        publish()
      }
  }

  private fun publish(loading: Boolean = _uiState.value.loading) {
    viewModelScope.launch { publishNow(loading) }
  }

  private suspend fun publishNow(loading: Boolean = _uiState.value.loading) {
    val id = listId ?: return
    val members = repository.members(id).first()
    val inPatientList =
      members.filter { it.pending != MemberPending.REMOVE }.mapTo(HashSet()) { it.patientId }
    val onDevice = local.mapTo(HashSet()) { it.id } + fromListsOnDevice
    val ordered = (local + fromLists + server.filter { it.id !in onDevice }).distinctBy { it.id }
    ordered.forEach { known[it.id] = it }
    val candidates =
      ordered.map { patient ->
        PickerCandidate(
          patient = patient,
          onDevice = patient.id in onDevice,
          alreadyMember = patient.id in inPatientList,
          notSentYet = patient.id in onDevice && neverUploaded(patient.id),
        )
      }
    notSent.clear()
    candidates.filter { it.notSentYet }.mapTo(notSent) { it.patient.id }
    _uiState.update { it.copy(candidates = candidates, loading = loading) }
  }

  private suspend fun neverUploaded(patientId: String): Boolean =
    try {
      fhirEngine.getLocalChanges(ResourceType.Patient, patientId).any {
        it.type == LocalChange.Type.INSERT
      }
    } catch (e: Exception) {
      false
    }

  private companion object {
    const val DEVICE_LIMIT = 40
    const val LOCAL_DEBOUNCE_MILLIS = 200L
    const val SERVER_DEBOUNCE_MILLIS = 450L
    const val SERVER_MIN_CHARS = 3
    const val DOWNLOAD_TIMEOUT_MILLIS = 30_000L
  }
}
