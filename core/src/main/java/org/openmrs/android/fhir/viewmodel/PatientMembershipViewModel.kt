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
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.remote.ServerCapabilities
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.forms.savedLocation
import org.openmrs.android.fhir.patientlists.GroupMapper
import org.openmrs.android.fhir.patientlists.Membership
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.patientlists.PatientMembership
import org.openmrs.android.fhir.patientlists.PatientSummary
import org.openmrs.android.fhir.patientlists.atLocation
import org.openmrs.android.fhir.unidentified.UnidentifiedStore
import timber.log.Timber

data class PatientMembershipUiState(
  val lists: List<PatientMembership> = emptyList(),
  val unsent: Boolean = false,
  val toIdentify: Boolean = false,
  val reading: Boolean = false,
  val canChange: Boolean = true,
) {
  val inLists: List<PatientMembership>
    get() = lists.filter { it.membership == Membership.IN }
}

class PatientMembershipViewModel
@Inject
constructor(
  private val repository: PatientListsRepository,
  private val fhirEngine: FhirEngine,
  private val applicationContext: Context,
  private val unidentified: UnidentifiedStore,
  private val capabilities: ServerCapabilities,
) : ViewModel() {
  private val patientId = MutableStateFlow<String?>(null)
  private val summary = MutableStateFlow<PatientSummary?>(null)
  private val unsent = MutableStateFlow(false)
  private val toIdentify = MutableStateFlow(false)
  private val reading = MutableStateFlow(false)
  private val location = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<PatientMembershipUiState> =
    patientId
      .filterNotNull()
      .flatMapLatest { id ->
        combine(
          repository.membershipsOf(id),
          combine(unsent, reading, location, toIdentify) { u, r, l, i -> listOf(u, r, l, i) },
          capabilities.features,
        ) { lists, others, features ->
          val unsent = others[0] as Boolean
          val reading = others[1] as Boolean
          val locationId = others[2] as String?
          val identify = others[3] as Boolean
          val scoped = AppConfig.get(applicationContext).sync.filterPatientListsByLocation
          val shown =
            lists.map { it.list }.atLocation(locationId, scoped).mapTo(HashSet()) { it.id }
          PatientMembershipUiState(
            lists = lists.filter { it.list.id in shown },
            unsent = unsent,
            toIdentify = identify,
            reading = reading,
            canChange = features.changeMembers,
          )
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT),
        PatientMembershipUiState(),
      )

  fun start(id: String) {
    if (patientId.value == id) return
    patientId.value = id
    viewModelScope.launch {
      location.value = applicationContext.savedLocation().id
      val patient = runCatching { fhirEngine.get<Patient>(id) }.getOrNull() ?: return@launch
      summary.value = GroupMapper.summary(patient)
      unsent.value = neverUploaded(id)
      toIdentify.value = id in unidentified.ids()
    }
  }

  fun open() {
    if (reading.value) return
    viewModelScope.launch {
      val unknown = uiState.value.lists.filter { it.membership == Membership.UNKNOWN }
      if (unknown.isEmpty()) return@launch
      reading.value = true
      // A server that is not there answers quickly; the lists stay unknown.
      unknown.take(MAX_READ).forEach { repository.refreshMembers(it.list.id) }
      reading.value = false
    }
  }

  fun setMember(listId: String, member: Boolean) {
    val patient = summary.value ?: return
    viewModelScope.launch {
      withContext(NonCancellable) {
        if (member) {
          val held = if (unsent.value) setOf(patient.id) else emptySet()
          repository.addMembers(listId, listOf(patient), held)
        } else {
          repository.removeMember(listId, patient.id)
        }
      }
      LiveSync.requestUpload(applicationContext)
    }
  }

  private suspend fun neverUploaded(id: String): Boolean =
    try {
      fhirEngine.getLocalChanges(ResourceType.Patient, id).any {
        it.type == LocalChange.Type.INSERT
      }
    } catch (e: Exception) {
      Timber.w(e, "Could not read the changes of a patient")
      false
    }

  private companion object {
    const val STOP_TIMEOUT = 5_000L
    const val MAX_READ = 15
  }
}
