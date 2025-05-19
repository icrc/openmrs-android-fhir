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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.QuestionnaireResponse

class GroupFormEntryViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
) : ViewModel() {
  private val _patients = MutableLiveData<List<PatientListViewModel.PatientItem>>()
  val patients: LiveData<List<PatientListViewModel.PatientItem>> = _patients
  val patientQuestionnaireResponseMap = mutableMapOf<String, QuestionnaireResponse>()

  private val _savedCount = MutableLiveData<Int>(0)
  val savedCount: LiveData<Int> = _savedCount

  private val patientIdToIndexMap = mutableMapOf<String, Int>()

  fun getPatients() {
    viewModelScope.launch {
      _patients.value =
        fhirEngine
          .search<Patient> {}
          .mapIndexed { index, fhirPatient ->
            val patientItem = fhirPatient.resource.toPatientItem(index + 1)
            patientIdToIndexMap[patientItem.resourceId] = index
            patientItem
          }
    }
  }

  fun getPatientIndexById(patientId: String): Int? {
    return patientIdToIndexMap[patientId]
  }

  fun getPatientQuestionnaireResponse(patientId: String?): QuestionnaireResponse? {
    if (patientId == null) {
      return null
    }

    return patientQuestionnaireResponseMap[patientId]
  }

  fun setPatientQuestionnaireResponse(patientId: String, response: QuestionnaireResponse) {
    patientQuestionnaireResponseMap[patientId] = response
  }

  fun getAllPatientIdsFromPatientQuestionnaireResponse(): List<String> {
    return patientQuestionnaireResponseMap.keys.toList()
  }

  fun incrementSavedCount() {
    _savedCount.value = _savedCount.value?.plus(1)
  }
}
