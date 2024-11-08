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
package org.openmrs.android.fhir.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.count
import com.google.android.fhir.search.search
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.RiskAssessment
import timber.log.Timber
import java.time.ZoneOffset

/**
 * The ViewModel helper class for PatientItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
class PatientListViewModel(application: Application, private val fhirEngine: FhirEngine) :
  AndroidViewModel(application) {

  val liveSearchedPatients = MutableLiveData<List<PatientItem>>()
  val patientCount = MutableLiveData<Long>()

  init {
    updatePatientListAndPatientCount({ getSearchResults() }, { count() })
  }

  fun searchPatientsByName(nameQuery: String) {
    updatePatientListAndPatientCount({ getSearchResults(nameQuery) }, { count(nameQuery) })
  }

  /**
   * [updatePatientListAndPatientCount] calls the search and count lambda and updates the live data
   * values accordingly. It is initially called when this [ViewModel] is created. Later its called
   * by the client every time search query changes or data-sync is completed.
   */
  private fun updatePatientListAndPatientCount(
    search: suspend () -> List<PatientItem>,
    count: suspend () -> Long,
  ) {
    viewModelScope.launch {
      liveSearchedPatients.value = search()
      patientCount.value = count()
    }
  }

  /**
   * Returns count of all the [Patient] who match the filter criteria unlike [getSearchResults]
   * which only returns a fixed range.
   */
  private suspend fun count(nameQuery: String = ""): Long {
    return fhirEngine.count<Patient> {
      if (nameQuery.isNotEmpty()) {
        filter(
          Patient.NAME,
          {
            modifier = StringFilterModifier.CONTAINS
            value = nameQuery
          },
        )
      }
    }
  }

  private suspend fun getSearchResults(nameQuery: String = ""): List<PatientItem> {
    val patients: MutableList<PatientItem> = mutableListOf()
    fhirEngine
      .search<Patient> {
        if (nameQuery.isNotEmpty()) {
          filter(
            Patient.NAME,
            {
              modifier = StringFilterModifier.CONTAINS
              value = nameQuery
            },
          )
        }
        sort(Patient.GIVEN, Order.ASCENDING)
        count = 100
        from = 0
      }
      .mapIndexed { index, fhirPatient -> fhirPatient.resource.toPatientItem(index + 1) }
      .let {
        patients.addAll(it)
        patients.map { patientItem->
          patientItem.isSynced = fhirEngine.getLocalChanges(ResourceType.Patient, patientItem.resourceId).isEmpty()
        }
      }


    val risks = getRiskAssessments()
    patients.forEach { patient ->
      risks["Patient/${patient.resourceId}"]?.let {
        patient.risk = it.prediction?.first()?.qualitativeRisk?.coding?.first()?.code
      }
    }
    return patients
  }

  private suspend fun getRiskAssessments(): Map<String, RiskAssessment?> {
    return fhirEngine
      .search<RiskAssessment> {}
      .groupBy { it.resource.subject.reference }
      .mapValues { entry ->
        entry.value
          .filter { it.resource.hasOccurrence() }
          .maxByOrNull { it.resource.occurrenceDateTimeType.value }
          ?.resource
      }
  }

  /** The Patient's details for display purposes. */
  data class PatientItem(
    val id: String,
    val resourceId: String,
    val name: String,
    val gender: String,
    val dob: LocalDate? = null,
    val phone: String,
    val city: String,
    val country: String,
    val isActive: Boolean,
    val identifiers: List<Identifier>,
    val html: String,
    var isSynced: Boolean? = false,
    var risk: String? = "",
    var riskItem: RiskAssessmentItem? = null,
  ) {
    override fun toString(): String = name
  }

  /** The Observation's details for display purposes. */
  data class ObservationItem(
    val id: String,
    val code: String,
    val effective: String,
    val value: String,
  ) {
    override fun toString(): String = code
  }

  data class ConditionItem(
    val id: String,
    val code: String,
    val effective: String,
    val value: String,
  ) {
    override fun toString(): String = code
  }

  data class VisitItem(
    val id: String,
    val code: String,
    val startDate: String,
    val endDate: String
  ) {
    override fun toString(): String = code + ", " + startDate + " - " + endDate
    fun getPeriods(): String = startDate + " - " + endDate
  }

    data class EncounterItem(
        val encounterId: String?,
        val type: String,
        val dateTime: String,
        val formCode: String?,
        val formDisplay: String?,
        val formResource: String?,
        val isSynced: Boolean?,
    ) {
        override fun toString(): String = encounterId ?: type
    }

  class PatientListViewModelFactory(
    private val application: Application,
    private val fhirEngine: FhirEngine,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(PatientListViewModel::class.java)) {
        return PatientListViewModel(application, fhirEngine) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}

internal fun Patient.toPatientItem(position: Int): PatientListViewModel.PatientItem {
  // Show nothing if no values available for gender and date of birth.
  val patientId = if (hasIdElement()) idElement.idPart else ""
  val name = if (hasName()) name[0].nameAsSingleString else ""
  val gender = if (hasGenderElement()) genderElement.valueAsString else ""
  val dob =
    if (hasBirthDateElement()) {
      try {
        birthDateElement.value.toInstant()
          .atOffset(ZoneOffset.UTC)
          .toLocalDate()
      } catch (e: Exception) {
        Timber.e("${birthDateElement.valueAsString} can't be parsed")
        null
      }
    } else null
  val phone = if (hasTelecom()) telecom[0].value else ""
  val city = if (hasAddress()) address[0].city else ""
  val country = if (hasAddress()) address[0].country else ""
  val identifiers = identifier
  val isActive = active
  val html: String = if (hasText()) text.div.valueAsString else ""

  return PatientListViewModel.PatientItem(
    id = position.toString(),
    resourceId = patientId,
    name = name,
    gender = gender ?: "",
    dob = dob,
    phone = phone ?: "",
    city = city ?: "",
    country = country ?: "",
    isActive = isActive,
    identifiers = identifiers,
    html = html,
  )
}
