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
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.get
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.readFileFromAssets
import org.openmrs.android.fhir.fragments.EditPatientFragment

/**
 * The ViewModel helper class for [EditPatientFragment], that is responsible for preparing data for
 * UI.
 */
class EditPatientViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  @Assisted val state: SavedStateHandle,
  private val fhirEngine: FhirEngine,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<EditPatientViewModel> {
    override fun create(
      handle: SavedStateHandle,
    ): EditPatientViewModel
  }

  private val patientId: String = requireNotNull(state["patient_id"])
  val livePatientData = liveData { emit(prepareEditPatient()) }
  lateinit var originalPatient: Patient

  private suspend fun prepareEditPatient(): Pair<String, String> {
    val patient = fhirEngine.get<Patient>(patientId)
    originalPatient = patient
    val launchContexts = mapOf<String, Resource>("client" to patient)
    val question =
      applicationContext.readFileFromAssets("new-patient-registration-paginated.json").trimIndent()
    val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val questionnaire = parser.parseResource(Questionnaire::class.java, question) as Questionnaire

    val questionnaireResponse: QuestionnaireResponse =
      ResourceMapper.populate(questionnaire, launchContexts)
    val questionnaireResponseJson = parser.encodeResourceToString(questionnaireResponse)
    return question to questionnaireResponseJson
  }

  private val questionnaire: String
    get() = getQuestionnaireJson()

  val isPatientSaved = MutableLiveData<Boolean>()

  private val questionnaireResource: Questionnaire
    get() =
      FhirContext.forCached(FhirVersionEnum.R4).newJsonParser().parseResource(questionnaire)
        as Questionnaire

  private var questionnaireJson: String? = null

  /**
   * Update patient registration questionnaire response into the application database.
   *
   * @param questionnaireResponse patient registration questionnaire response
   */
  fun updatePatient(questionnaireResponse: QuestionnaireResponse) {
    viewModelScope.launch {
      val entry = ResourceMapper.extract(questionnaireResource, questionnaireResponse).entryFirstRep
      if (entry.resource !is Patient) return@launch
      val patient = entry.resource as Patient
      if (
        patient.hasName() &&
          patient.name[0].hasGiven() &&
          patient.name[0].hasFamily() &&
          patient.hasBirthDate() &&
          patient.hasGender()
      ) {
        originalPatient.name[0].given = patient.name[0].given
        originalPatient.name[0].family = patient.name[0].family
        if (originalPatient.name[0].text != null) {
          originalPatient.name[0].text = patient.name[0].text
        }
        originalPatient.birthDate = patient.birthDate
        originalPatient.gender = patient.gender
        if (patient.hasTelecom()) originalPatient.telecom[0].value = patient.telecom[0].value
        if (patient.hasAddress()) {
          originalPatient.address[0].city = patient.address[0].city
          originalPatient.address[0].country = patient.address[0].country
        }
        fhirEngine.update(originalPatient)
        isPatientSaved.value = true
        return@launch
      }

      isPatientSaved.value = false
    }
  }

  private fun getQuestionnaireJson(): String {
    questionnaireJson?.let {
      return it
    }
    questionnaireJson =
      applicationContext.readFileFromAssets(
        state[EditPatientFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!,
      )
    return questionnaireJson!!
  }
}
