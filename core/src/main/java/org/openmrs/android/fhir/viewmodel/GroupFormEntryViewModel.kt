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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.google.android.fhir.search.search
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse

class GroupFormEntryViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
) : ViewModel() {
  private val _patients = MutableLiveData<List<PatientListViewModel.PatientItem>>()
  val patients: LiveData<List<PatientListViewModel.PatientItem>> = _patients

  private val patientIdToEncounterIdMap = mutableMapOf<String, String>()
  val patientResponses = mutableMapOf<String, String>()
  val isLoading = MutableLiveData<Boolean>()
  var submittedSet = mutableSetOf<Int>()

  fun getPatients(patientIds: Set<String>) {
    isLoading.value = true
    viewModelScope.launch {
      _patients.value =
        fhirEngine
          .search<Patient> {}
          .filter { patientIds.contains(it.resource.idElement.idPart) }
          .mapIndexed { index, fhirPatient -> fhirPatient.resource.toPatientItem(index + 1) }
      isLoading.value = false
    }
  }

  fun getPatientName(patientId: String): String {
    val patient = _patients.value?.find { it.resourceId == patientId }
    return patient?.name ?: ""
  }

  fun setPatientIdToEncounterIdMap(patientId: String, encounterId: String) {
    patientIdToEncounterIdMap[patientId] = encounterId
  }

  fun getPatientIdToEncounterIdMap(): Map<String, String> {
    return patientIdToEncounterIdMap.toMap()
  }

  fun getEncounterIdForPatientId(patientId: String): String? {
    return patientIdToEncounterIdMap[patientId]
  }

  suspend fun isValidQuestionnaireResponse(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    applicationContext: Context,
  ): Boolean {
    return !QuestionnaireResponseValidator.validateQuestionnaireResponse(
        questionnaire,
        questionnaireResponse,
        applicationContext,
      )
      .values
      .flatten()
      .any { it is Invalid }
  }
}
