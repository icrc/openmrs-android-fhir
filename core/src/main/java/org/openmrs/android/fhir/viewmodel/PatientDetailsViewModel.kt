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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.revInclude
import com.google.android.fhir.search.search
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.MockConstants
import org.openmrs.android.fhir.MockConstants.DATE24_FORMATTER
import org.openmrs.android.fhir.MockConstants.WRAP_ENCOUNTER
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.helpers.OpenMRSHelper

/**
 * The ViewModel helper class for PatientItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
class PatientDetailsViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  @Assisted val state: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<PatientDetailsViewModel> {
    override fun create(
      handle: SavedStateHandle,
    ): PatientDetailsViewModel
  }

  private val patientId: String = requireNotNull(state["patient_id"])
  val livePatientData = MutableLiveData<List<PatientDetailData>>()

  /** Emits list of [PatientDetailData]. */
  fun getPatientDetailData() {
    viewModelScope.launch { livePatientData.value = getPatientDetailDataModel() }
  }

  private suspend fun getPatientDetailDataModel(): List<PatientDetailData> {
    val searchResult =
      fhirEngine.search<Patient> {
        filter(Resource.RES_ID, { value = of(patientId) })
        revInclude<Encounter>(Encounter.SUBJECT)
      }

    val patientResource = searchResult.firstOrNull()?.resource ?: return emptyList()

    val data = mutableListOf<PatientDetailData>()
    val visits = OpenMRSHelper.VisitHelper.getVisits(fhirEngine, patientId)
    data.addPatientDetailData(patientResource)
    data.add(PatientDetailHeader(getString(R.string.header_encounters)))
    visits.forEach { (visit, encounters) ->
      if (!WRAP_ENCOUNTER) {
        data.addVisitData(visit, encounters)
      }

      encounters.forEach { encounter ->
        val isSynced =
          fhirEngine.getLocalChanges(ResourceType.Encounter, encounter.logicalId).isEmpty()
        data.addEncounterData(encounter, isSynced)
      }
    }

    return data.sortedBy {
      when (it) {
        is PatientDetailOverview -> false
        is PatientDetailHeader -> false
        is PatientDetailEncounter -> it.encounter.isSynced
        else -> true
      }
    }
  }

  private fun MutableList<PatientDetailData>.addPatientDetailData(
    patient: Patient,
  ) {
    patient.toPatientItem(0).let { patientItem ->
      runBlocking {
        patientItem.isSynced =
          fhirEngine.getLocalChanges(ResourceType.Patient, patientItem.resourceId).isEmpty()
        add(PatientDetailOverview(patientItem, firstInGroup = true))
        if (patientItem.isSynced != null && !patientItem.isSynced!!) {
          add(PatientUnsynced(false, false))
        }
      }
      // Add other patient details if necessary
    }
  }

  suspend fun hasActiveVisit(): Boolean {
    return OpenMRSHelper.VisitHelper.getActiveVisit(fhirEngine, patientId, false) != null
  }

  fun createVisit(startDate: Date) {
    viewModelScope.launch {
      val visit =
        OpenMRSHelper.VisitHelper.startVisit(
          fhirEngine,
          patientId,
          MockConstants.VISIT_TYPE_UUID,
          startDate,
        )
    }
  }

  private fun MutableList<PatientDetailData>.addVisitData(
    visit: Encounter,
    encounters: List<Encounter>?,
  ) {
    val visitItem = createVisitItem(visit)
    add(PatientDetailVisit(visitItem))
  }

  private fun MutableList<PatientDetailData>.addEncounterData(
    encounter: Encounter,
    isSynced: Boolean,
  ) {
    add(PatientDetailEncounter(createEncounterItem(encounter, isSynced)))
  }

  private fun createVisitItem(visit: Encounter): PatientListViewModel.VisitItem {
    val visitType = visit.type.firstOrNull()?.coding?.firstOrNull()?.display ?: "Type"
    val startDate = visit.period?.start?.let { DATE24_FORMATTER.format(it) } ?: ""

    val endDate = visit.period?.end?.let { DATE24_FORMATTER.format(it) } ?: ""

    return PatientListViewModel.VisitItem(
      visit.logicalId,
      visitType,
      startDate,
      endDate,
    )
  }

  private fun createEncounterItem(
    encounter: Encounter,
    isSynced: Boolean,
  ): PatientListViewModel.EncounterItem {
    val visitType = encounter.type.firstOrNull()?.coding?.firstOrNull()?.display ?: "Type"
    val visitDate =
      encounter.period?.start?.let {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
      }
        ?: "Date"

    return PatientListViewModel.EncounterItem(
      encounter.logicalId,
      visitType,
      visitDate,
      encounter.type.getOrNull(0)?.id,
      encounter.type.getOrNull(0)?.text,
      isSynced,
      encounter.type.getOrNull(0)?.coding?.getOrNull(0)?.code
    )
  }

  private fun getString(resId: Int) = applicationContext.resources.getString(resId)

  companion object {
    private const val MAX_RESOURCE_COUNT = 10
  }
}

interface PatientDetailData {
  val firstInGroup: Boolean
  val lastInGroup: Boolean
}

data class PatientDetailHeader(
  val header: String,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailProperty(
  val patientProperty: PatientProperty,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailOverview(
  val patient: PatientListViewModel.PatientItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientUnsynced(
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailObservation(
  val observation: PatientListViewModel.ObservationItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailCondition(
  val condition: PatientListViewModel.ConditionItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailEncounter(
  val encounter: PatientListViewModel.EncounterItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailVisit(
  val visit: PatientListViewModel.VisitItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientProperty(val header: String, val value: String, val isSynced: Boolean)

data class RiskAssessmentItem(
  var riskStatusColor: Int,
  var riskStatus: String,
  var lastContacted: String,
  var patientCardColor: Int,
)

/**
 * The logical (unqualified) part of the ID. For example, if the ID is
 * "http://example.com/fhir/Patient/123/_history/456", then this value would be "123".
 */
private val Resource.logicalId: String
  get() {
    return this.idElement?.idPart.orEmpty()
  }
