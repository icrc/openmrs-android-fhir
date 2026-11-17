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
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.data.remote.ServerCapabilities
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.multiuser.DataVisibility
import org.openmrs.android.fhir.patientlists.GroupMapper
import org.openmrs.android.fhir.patientlists.MemberPending
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.patientlists.PatientSummary
import org.openmrs.android.fhir.patientlists.patientsById
import org.openmrs.android.fhir.unidentified.UnidentifiedPatients
import timber.log.Timber

data class SessionPatient(
  val patient: PatientSummary,
  val onDevice: Boolean,
  val ticked: Boolean,
  val toIdentify: Boolean = false,
  val notSentYet: Boolean = false,
)

data class GroupSessionUiState(
  val listName: String? = null,
  val loading: Boolean = true,
  val members: List<SessionPatient> = emptyList(),
  val added: List<SessionPatient> = emptyList(),
  val starting: Boolean = false,
  val joinsList: Boolean = true,
) {
  val participantIds: List<String>
    get() = sessionParticipants(members, added)

  val allTicked: Boolean
    get() {
      val rows = (members + added).filter { it.onDevice }
      return rows.isNotEmpty() && rows.all { it.ticked }
    }
}

internal fun sessionParticipants(
  members: List<SessionPatient>,
  added: List<SessionPatient>,
): List<String> =
  (members.filter { it.onDevice && it.ticked } + added.filter { it.ticked })
    .map { it.patient.id }
    .distinct()

/**
 * Who joins the patient list when the session starts: the patients added to the session that are
 * taking part (one who was taken out again does not) and are not in the list already.
 */
internal fun patientsToJoinList(
  added: List<SessionPatient>,
  memberIds: Set<String>,
): List<PatientSummary> =
  added.filter { it.ticked && it.patient.id !in memberIds }.map { it.patient }.distinctBy { it.id }

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSessionPatientsViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val applicationContext: Context,
  private val repository: PatientListsRepository,
  private val unidentified: UnidentifiedPatients,
  private val visibility: DataVisibility,
  private val capabilities: ServerCapabilities,
) : ViewModel() {
  private val listId = MutableStateFlow<String?>(null)
  private val added = MutableStateFlow<List<SessionPatient>>(emptyList())
  private val untickedMembers = MutableStateFlow<Set<String>>(emptySet())
  private val starting = MutableStateFlow(false)

  val uiState: StateFlow<GroupSessionUiState> =
    listId
      .flatMapLatest { id ->
        if (id == null) {
          flowOf(GroupSessionUiState())
        } else {
          combine(
            repository.item(id),
            repository.members(id).map { members ->
              members to onDevice(members.map { it.patientId })
            },
            added,
            untickedMembers,
            starting,
          ) { list, (members, here), addedRows, unticked, busy ->
            GroupSessionUiState(
              listName = list?.name,
              loading = false,
              members =
                members
                  .filter { it.pending != MemberPending.REMOVE }
                  .map { member ->
                    SessionPatient(
                      patient =
                        PatientSummary(
                          member.patientId,
                          member.name,
                          member.gender,
                          member.birthDate,
                          member.identifier,
                        ),
                      onDevice = member.patientId in here,
                      ticked = member.patientId !in unticked,
                      toIdentify = member.toIdentify,
                    )
                  },
              added = addedRows,
              starting = busy,
              joinsList = capabilities.current().changeMembers,
            )
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), GroupSessionUiState())

  fun start(patientListId: String) {
    if (listId.value == patientListId) return
    listId.value = patientListId
    viewModelScope.launch { repository.refreshMembers(patientListId) }
  }

  fun setTicked(patientId: String, ticked: Boolean) {
    if (added.value.any { it.patient.id == patientId }) {
      added.update { rows ->
        rows.map { if (it.patient.id == patientId) it.copy(ticked = ticked) else it }
      }
    } else {
      untickedMembers.update { if (ticked) it - patientId else it + patientId }
    }
  }

  fun setAllTicked(ticked: Boolean) {
    val members = uiState.value.members.filter { it.onDevice }.map { it.patient.id }
    untickedMembers.value = if (ticked) emptySet() else members.toSet()
    added.update { rows -> rows.map { it.copy(ticked = ticked) } }
  }

  fun remove(patientId: String) {
    added.update { rows -> rows.filterNot { it.patient.id == patientId } }
  }

  fun addPatients(ids: Collection<String>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
      val known =
        added.value.mapTo(HashSet()) { it.patient.id } + uiState.value.members.map { it.patient.id }
      val fresh = ids.filter { it !in known }
      if (fresh.isEmpty()) return@launch
      val toIdentify = unidentified.ids()
      val patients = visibility.visibleAmong(fhirEngine.patientsById(fresh)) { it.idElement.idPart }
      val rows =
        patients.map { patient ->
          val summary = GroupMapper.summary(patient)
          SessionPatient(
            patient = summary,
            onDevice = true,
            ticked = true,
            toIdentify = summary.id in toIdentify,
            notSentYet = neverUploaded(summary.id),
          )
        }
      added.update { it + rows }
    }
  }

  fun addPatientToIdentify(note: String) {
    viewModelScope.launch {
      val id = unidentified.create(note)
      addPatients(listOf(id))
    }
  }

  /**
   * Adds the patients added to the session to the patient list (on the device; sent later) and
   * hands over who takes part. Nothing is added to the list while the session is not started.
   */
  fun begin(onReady: (patientIds: List<String>) -> Unit) {
    val id = listId.value ?: return
    if (starting.value) return
    val state = uiState.value
    val participants = state.participantIds
    if (participants.isEmpty()) return
    starting.value = true
    viewModelScope.launch {
      try {
        val join =
          if (capabilities.current().changeMembers) {
            patientsToJoinList(state.added, repository.memberIds(id))
          } else {
            emptyList()
          }
        if (join.isNotEmpty()) {
          val unsent = state.added.filter { it.notSentYet }.mapTo(HashSet()) { it.patient.id }
          withContext(NonCancellable) { repository.addMembers(id, join, heldForUpload = unsent) }
          LiveSync.requestUpload(applicationContext)
        }
        onReady(participants)
      } finally {
        starting.value = false
      }
    }
  }

  private suspend fun onDevice(ids: List<String>): Set<String> =
    try {
      fhirEngine.patientsById(ids).mapTo(HashSet()) { it.idElement.idPart }
    } catch (e: Exception) {
      Timber.w(e, "Could not read which patients of the list are on the device")
      emptySet()
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
    const val STOP_TIMEOUT = 5_000L
  }
}
