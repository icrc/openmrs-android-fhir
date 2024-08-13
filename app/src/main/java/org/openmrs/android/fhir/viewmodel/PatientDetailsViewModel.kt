/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.android.fhir

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.revInclude
import com.google.android.fhir.search.search
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.MockConstants.DATE24_FORMATTER
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import org.openmrs.android.fhir.viewmodel.toPatientItem
import org.openmrs.android.helpers.OpenMRSHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ViewModel helper class for PatientItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
class PatientDetailsViewModel(
    application: Application,
    private val fhirEngine: FhirEngine,
    private val patientId: String
) : AndroidViewModel(application) {
    val livePatientData = MutableLiveData<List<PatientDetailData>>()

    /** Emits list of [PatientDetailData]. */
    fun getPatientDetailData() {
        viewModelScope.launch {

            livePatientData.value = getPatientDetailDataModel()
        }
    }

    private suspend fun getPatientDetailDataModel(): List<PatientDetailData> {
        val searchResult = fhirEngine.search<Patient> {
            filter(Resource.RES_ID, { value = of(patientId) })
            revInclude<Encounter>(Encounter.SUBJECT)
        }

        val patientResource = searchResult.firstOrNull()?.resource ?: return emptyList()

        val data = mutableListOf<PatientDetailData>()
        val visits = OpenMRSHelper.VisitHelper.getVisits(fhirEngine, patientId)

        data.addPatientDetailData(patientResource)

        visits.forEach { (visit, encounters) ->
            data.addVisitData(visit, encounters)
            encounters.forEach { encounter ->
                data.addEncounterData(encounter)
            }
        }

        return data
    }

    private fun MutableList<PatientDetailData>.addPatientDetailData(
        patient: Patient,
    ) {
        patient.toPatientItem(0).let { patientItem ->
            add(PatientDetailOverview(patientItem, firstInGroup = true))
            // Add other patient details if necessary
        }
    }

    suspend fun hasActiveVisit(): Boolean {
        return OpenMRSHelper.VisitHelper.getActiveVisit(fhirEngine, patientId, false) != null
    }

    fun createVisit(startDate: Date) {
        viewModelScope.launch {
            val visit =
                OpenMRSHelper.VisitHelper.startVisit(fhirEngine, patientId, MockConstants.VISIT_TYPE_UUID, startDate);
            fhirEngine.create(visit)
        }
    }

    private fun MutableList<PatientDetailData>.addVisitData(visit: Encounter, encounters: List<Encounter>?) {
        val visitItem = createVisitItem(visit)
        add(PatientDetailVisit(visitItem))
    }

    private fun MutableList<PatientDetailData>.addEncounterData(encounter: Encounter) {
        add(PatientDetailEncounter(createEncounterItem(encounter)))
    }

    private fun createVisitItem(visit: Encounter): PatientListViewModel.VisitItem {
        val visitType = visit.type.firstOrNull()?.coding?.firstOrNull()?.display ?: "Type"
        val startDate = visit.period?.start?.let {
            DATE24_FORMATTER.format(it)
        } ?: ""

        val endDate = visit.period?.end?.let {
            DATE24_FORMATTER.format(it)
        } ?: ""

        return PatientListViewModel.VisitItem(
            visit.logicalId,
            visitType,
            startDate,
            endDate
        )
    }

    private fun createEncounterItem(encounter: Encounter): PatientListViewModel.EncounterItem {
        val visitType = encounter.type.firstOrNull()?.coding?.firstOrNull()?.display ?: "Type"
        val visitDate = encounter.period?.start?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
        } ?: "Date"

        return PatientListViewModel.EncounterItem(
            encounter.logicalId,
            visitType,
            visitDate,
            encounter.type.getOrNull(0)?.id,
            encounter.type.getOrNull(0)?.text,
            "assessment.json"
        )
    }

    private fun getString(resId: Int) = getApplication<Application>().resources.getString(resId)

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

data class PatientProperty(val header: String, val value: String)

class PatientDetailsViewModelFactory(
    private val application: Application,
    private val fhirEngine: FhirEngine,
    private val patientId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PatientDetailsViewModel::class.java)) {
            "Unknown ViewModel class"
        }
        return PatientDetailsViewModel(application, fhirEngine, patientId) as T
    }
}

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
