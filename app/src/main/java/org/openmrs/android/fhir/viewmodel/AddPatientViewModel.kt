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
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.fragments.AddPatientFragment

/** ViewModel for patient registration screen {@link AddPatientFragment}. */
class AddPatientViewModel(application: Application, private val state: SavedStateHandle) :
  AndroidViewModel(application) {

  var locationId: String? = null


  val isPatientSaved = MutableLiveData<Boolean>()
  var questionnaire: Questionnaire = Questionnaire()
  val embeddedQuestionnaire = MutableLiveData<String>()

  private var fhirEngine: FhirEngine = FhirApplication.fhirEngine(application.applicationContext)
  private var database: AppDatabase = FhirApplication.roomDatabase(application.applicationContext)
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
        val identifiers = extractLocationIdentifiersFromQuestionnaireResponse(questionnaireResponse,location)
        identifiers.addAll(createLocationIdentifiers(location))
        patient.identifier = identifiers
      }
      fhirEngine.create(patient)
      isPatientSaved.value = true
    }
  }

  private fun createLocationIdentifiers(location: Location): List<Identifier> {
    val identifierList: MutableList<Identifier> = mutableListOf()
    val identifierMap = runBlocking { PatientIdentifierManager.getNextIdentifiers() }
    identifierMap.forEach { (identifierTypeDisplay, identifierValue) ->
      val identifier = Identifier().apply {
        id = generateUuid()
        use = Identifier.IdentifierUse.OFFICIAL
        value = identifierValue
        type = CodeableConcept().apply { text = identifierTypeDisplay }
      }
      identifier.addExtension(PATIENT_LOCATION_IDENTIFIER_URL, Reference().apply {
        reference = location.id
        type = "Location"
        display = location.name
      })
      identifierList.add(identifier)
    }
    return identifierList
  }

  private fun extractLocationIdentifiersFromQuestionnaireResponse(questionnaireResponse: QuestionnaireResponse, location: Location): MutableList<Identifier> {
    val identifierItems = questionnaireResponse.item.filter {
      it.linkId.contains("identifier-")
    }

    val identifierList: MutableList<Identifier> = mutableListOf()
    identifierItems.forEach { identifierItem ->
      val identifier = Identifier().apply {
        id = generateUuid()
        use = Identifier.IdentifierUse.OFFICIAL
        value = identifierItem.answerFirstRep.valueStringType.value
        type = CodeableConcept().apply { text = identifierItem.text }
      }
      identifier.addExtension(PATIENT_LOCATION_IDENTIFIER_URL, Reference().apply {
        reference = location.id
        type = "Location"
        display = location.name
      })
      identifierList.add(identifier)
    }
    return identifierList
  }

  fun getEmbeddedQuestionnaire() {
    viewModelScope.launch {

      val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
      val questionnaireString = getApplication<Application>()
        .readFileFromAssets(state[AddPatientFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
      questionnaire = parser.parseResource(Questionnaire::class.java, questionnaireString)
      embeddedQuestionnaire.value =
        parser.encodeResourceToString(embeddIdentifierInQuestionnaire(questionnaire))
    }
  }

  private suspend fun embeddIdentifierInQuestionnaire(questionnaire: Questionnaire): Questionnaire{
    val questionnaireItems = questionnaire.item

    val appContext = getApplication<Application>().applicationContext
    val selectedIdentifiers = appContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]
    val filteredIdentifiers: MutableList<String>
    if (selectedIdentifiers != null) {
      filteredIdentifiers = selectedIdentifiers.filter {identifierId ->
        val isUnique = let {database.dao().getIdentifierTypeById(identifierId)?.isUnique}
        isUnique == "null"
      }.toMutableList()
      filteredIdentifiers.forEach {identifierTypeId ->
        val selectedIdentifierType = database.dao().getIdentifierTypeById(identifierTypeId)
        if(selectedIdentifierType != null){
          questionnaireItems.add(0,QuestionnaireItemComponent().apply {
            linkId = "identifier-${selectedIdentifierType.uuid}"
            type = Questionnaire.QuestionnaireItemType.STRING
            definition = PATIENT_IDENTIFIER_DEFINITION_URL
            text = selectedIdentifierType.display
            required = true
          })
        }
      }
    }
    questionnaire.setItem(questionnaireItems)
    return questionnaire
  }

  private fun generateUuid(): String {
    return UUID.randomUUID().toString()
  }

  companion object {
    private val PATIENT_LOCATION_IDENTIFIER_URL = "http://fhir.openmrs.org/ext/patient/identifier#location"
    private val PATIENT_IDENTIFIER_DEFINITION_URL = "http://hl7.org/fhir/StructureDefinition/Patient#Patient.identifier"
  }
}


