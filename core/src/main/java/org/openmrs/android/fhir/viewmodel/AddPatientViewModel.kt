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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.google.android.fhir.db.ResourceNotFoundException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.extensions.readFileFromAssets

/** ViewModel for patient registration screen {@link AddPatientFragment}. */
class AddPatientViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val database: AppDatabase,
) : ViewModel() {

  var locationId: String? = null
  val isPatientSaved = MutableLiveData<Boolean>()
  var questionnaire: Questionnaire = Questionnaire()
  val embeddedQuestionnaire = MutableLiveData<String>()
  private var saveInProgress = false

  /**
   * Saves patient registration questionnaire response into the application database.
   *
   * @param questionnaireResponse patient registration questionnaire response
   */
  fun savePatient(questionnaireResponse: QuestionnaireResponse) {
    if (saveInProgress) return // To avoid multiple save of patient.

    viewModelScope.launch {
      saveInProgress = true
      if (
        QuestionnaireResponseValidator.validateQuestionnaireResponse(
            questionnaire,
            questionnaireResponse,
            applicationContext,
          )
          .values
          .flatten()
          .any { it is Invalid }
      ) {
        saveInProgress = false
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
      if (location != null) {
        val identifiers =
          extractLocationIdentifiersFromQuestionnaireResponse(questionnaireResponse, location)
        identifiers.addAll(createLocationIdentifiers(location))
        identifiers.add(0, createUnsyncedIdentifier(location))
        patient.identifier = identifiers
      }
      fhirEngine.create(patient)
      isPatientSaved.value = true
      saveInProgress = false
    }
  }

  private suspend fun createLocationIdentifiers(location: Location): List<Identifier> {
    val identifierList: MutableList<Identifier> = mutableListOf()
    val selectedIdentifierTypes =
      applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.toList()
    if (selectedIdentifierTypes != null) {
      val filteredIdentifierTypes =
        selectedIdentifierTypes.filter { identifierTypeId ->
          val isAutoGenerated = let {
            database.dao().getIdentifierTypeById(identifierTypeId)?.isAutoGenerated
          }
          isAutoGenerated != null && isAutoGenerated == true
        }
      for (identifierTypeId in filteredIdentifierTypes) {
        val identifierType = let { database.dao().getIdentifierTypeById(identifierTypeId) }
        val identifier =
          Identifier().apply {
            id = generateUuid()
            use = Identifier.IdentifierUse.OFFICIAL
            value = identifierType?.uuid
            type = CodeableConcept().apply { text = identifierType?.display }
          }
        identifier.addExtension(
          PATIENT_LOCATION_IDENTIFIER_URL,
          Reference().apply {
            reference = location.id
            type = "Location"
            display = location.name
          },
        )
        identifierList.add(identifier)
      }
    }
    return identifierList
  }

  private fun createUnsyncedIdentifier(location: Location): Identifier {
    val identifier =
      Identifier().apply {
        id = generateUuid()
        use = Identifier.IdentifierUse.OFFICIAL
        value = "unsynced"
        type = CodeableConcept().apply { text = "unsynced" }
      }
    identifier.addExtension(
      PATIENT_LOCATION_IDENTIFIER_URL,
      Reference().apply {
        reference = location.id
        type = "Location"
        display = location.name
      },
    )
    return identifier
  }

  private fun extractLocationIdentifiersFromQuestionnaireResponse(
    questionnaireResponse: QuestionnaireResponse,
    location: Location,
  ): MutableList<Identifier> {
    val identifierItems = questionnaireResponse.item.filter { it.linkId.contains("identifier-") }

    val identifierList: MutableList<Identifier> = mutableListOf()
    identifierItems.forEach { identifierItem ->
      val identifier =
        Identifier().apply {
          id = generateUuid()
          use = Identifier.IdentifierUse.OFFICIAL
          value = identifierItem.answerFirstRep.valueStringType.value
          type = CodeableConcept().apply { text = identifierItem.text }
        }
      identifier.addExtension(
        PATIENT_LOCATION_IDENTIFIER_URL,
        Reference().apply {
          reference = location.id
          type = "Location"
          display = location.name
        },
      )
      identifierList.add(identifier)
    }
    return identifierList
  }

  fun getEmbeddedQuestionnaire(questionnaireName: String) {
    viewModelScope.launch {
      val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
      try {
        questionnaire =
          fhirEngine.get(ResourceType.Questionnaire, questionnaireName) as Questionnaire
      } catch (e: ResourceNotFoundException) {
        val questionnaireString = applicationContext.readFileFromAssets("$questionnaireName.json")
        questionnaire = parser.parseResource(Questionnaire::class.java, questionnaireString)
      }
      embeddedQuestionnaire.value =
        parser.encodeResourceToString(embeddIdentifierInQuestionnaire(questionnaire))
    }
  }

  private suspend fun embeddIdentifierInQuestionnaire(questionnaire: Questionnaire): Questionnaire {
    val questionnaireItems = questionnaire.item
    val selectedIdentifiers =
      applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]
    val filteredIdentifiers: MutableList<String>
    if (selectedIdentifiers != null) {
      filteredIdentifiers =
        selectedIdentifiers
          .filter { identifierId ->
            val isAutoGenerated = let {
              database.dao().getIdentifierTypeById(identifierId)?.isAutoGenerated
            }
            isAutoGenerated == false
          }
          .toMutableList()
      filteredIdentifiers.forEach { identifierTypeId ->
        val selectedIdentifierType = database.dao().getIdentifierTypeById(identifierTypeId)
        if (selectedIdentifierType != null) {
          questionnaireItems.add(
            0,
            QuestionnaireItemComponent().apply {
              linkId = "identifier-${selectedIdentifierType.uuid}"
              type = Questionnaire.QuestionnaireItemType.STRING
              definition = PATIENT_IDENTIFIER_DEFINITION_URL
              text = selectedIdentifierType.display
              required = selectedIdentifierType.required
            },
          )
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
    private val PATIENT_LOCATION_IDENTIFIER_URL =
      "http://fhir.openmrs.org/ext/patient/identifier#location"
    private val PATIENT_IDENTIFIER_DEFINITION_URL =
      "http://hl7.org/fhir/StructureDefinition/Patient#Patient.identifier"
  }
}
