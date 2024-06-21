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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import org.openmrs.android.fhir.extensions.readFileFromAssets
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import java.util.UUID
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.fragments.AddPatientFragment

/** ViewModel for patient registration screen {@link AddPatientFragment}. */
class AddPatientViewModel(application: Application, private val state: SavedStateHandle) :
  AndroidViewModel(application) {

  private var _questionnaireJson: String? = null
  var locationId: String? = null
  val questionnaireJson: String
    get() = fetchQuestionnaireJson()

  val isPatientSaved = MutableLiveData<Boolean>()

  private val questionnaire: Questionnaire
    get() =
      FhirContext.forCached(FhirVersionEnum.R4).newJsonParser().parseResource(questionnaireJson)
        as Questionnaire

  private var fhirEngine: FhirEngine = FhirApplication.fhirEngine(application.applicationContext)

  /**
   * Saves patient registration questionnaire response into the application database.
   *
   * @param questionnaireResponse patient registration questionnaire response
   */
  fun savePatient(questionnaireResponse: QuestionnaireResponse) {
    viewModelScope.launch {
      if (
        QuestionnaireResponseValidator.validateQuestionnaireResponse(
          questionnaire,
          questionnaireResponse,
          getApplication(),
        )
          .values
          .flatten()
          .any { it is Invalid }
      ) {
        isPatientSaved.value = false
        return@launch
      }

      val entry =
        ResourceMapper.extract(
          questionnaire,
          questionnaireResponse,
        )
          .entryFirstRep
      if (entry.resource !is Patient) {
        return@launch
      }
      val patient = entry.resource as Patient
      patient.id = generateUuid()
      val location = locationId?.let { fhirEngine.get(ResourceType.Location, it) } as Location?
      if(location != null) {
        patient.addIdentifier(createLocationIdentifier(location))
      }

      fhirEngine.create(patient)
      isPatientSaved.value = true
    }
  }

  private fun createLocationIdentifier(location: Location): Identifier {
    val identifierValue = fetchIdentifierFromServer()
    val identifier = Identifier().apply {
      id = generateUuid()
      use = Identifier.IdentifierUse.OFFICIAL
      value = identifierValue
      type = CodeableConcept().apply { text = DEFAULT_IDENTIFIER_TYPE }
    }
    //TODO: Add Type to identifier which includes OpenMRS ID
    identifier.addExtension(PATIENT_LOCATION_IDENTIFIER_URL, Reference().apply {
      reference = location.id
      type = "Location"
      display = location.name
    })

    return identifier
  }

  //TODO: fetch Identifier from server
  private fun fetchIdentifierFromServer(): String {
    return (1000..9999).random().toString()
  }

  private fun fetchQuestionnaireJson(): String {
    _questionnaireJson?.let {
      return it
    }
    _questionnaireJson =
      getApplication<Application>()
        .readFileFromAssets(state[AddPatientFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
    return _questionnaireJson!!
  }

  private fun generateUuid(): String {
    return UUID.randomUUID().toString()
  }

  companion object {
    private val PATIENT_LOCATION_IDENTIFIER_URL = "http://fhir.openmrs.org/ext/patient/identifier#location"
    private val DEFAULT_IDENTIFIER_TYPE = "HSU ID"
  }
}
