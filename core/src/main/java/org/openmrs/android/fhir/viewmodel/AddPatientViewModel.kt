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
import com.google.android.fhir.datacapture.extensions.allItems
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.Constants.PATIENT_IDENTIFIER_DEFINITION_URL
import org.openmrs.android.fhir.Constants.PATIENT_LOCATION_IDENTIFIER_URL
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets

/** ViewModel for patient registration screen {@link AddPatientFragment}. */
class AddPatientViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val database: AppDatabase,
  private val openMRSHelper: OpenMRSHelper,
) : ViewModel() {

  var locationId: String? = null
  val isPatientSaved = MutableLiveData<Boolean>()
  var questionnaire: Questionnaire? = Questionnaire()
  val embeddedQuestionnaire = MutableLiveData<String>()
  private var saveInProgress = false

  val isLoading = MutableLiveData<Boolean>()

  /**
   * Saves patient registration questionnaire response into the application database.
   *
   * @param questionnaireResponse patient registration questionnaire response
   */
  fun savePatient(questionnaireResponse: QuestionnaireResponse, fetchIdentifiers: Boolean = true) {
    if (saveInProgress) return // To avoid multiple save of patient.
    viewModelScope.launch {
      saveInProgress = true
      if (
        QuestionnaireResponseValidator.validateQuestionnaireResponse(
            questionnaire!!,
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
            questionnaire!!,
            questionnaireResponse,
          )
          .entryFirstRep
      if (entry.resource !is Patient) {
        return@launch
      }

      val patient = entry.resource as Patient
      patient.id = generateUuid()

      if (patient.birthDate == null) {
        val estimatedMonths =
          questionnaireResponse.allItems
            .firstOrNull { it.linkId == "estimatedDateOfBirthMonths" }
            ?.answerFirstRep
            ?.value as? Quantity
        val estimatedYears =
          questionnaireResponse.allItems
            .firstOrNull { it.linkId == "estimatedDateOfBirthYears" }
            ?.answerFirstRep
            ?.value as? Quantity
        patient.birthDateElement =
          estimatedYears?.let {
            openMRSHelper.getDateDiffByQuantity(
              estimatedAgeYear = estimatedYears,
              estimatedAgeMonth = estimatedMonths,
            )
          }
      }

      val location = locationId?.let { fhirEngine.get(ResourceType.Location, it) } as Location?
      if (location != null) {
        val identifiers =
          extractLocationIdentifiersFromQuestionnaireResponse(questionnaireResponse, location)
        identifiers.addAll(createLocationIdentifiers(location, fetchIdentifiers))
        if (fetchIdentifiers) {
          identifiers.add(0, createUnsyncedIdentifier(location))
        }
        patient.identifier = identifiers
      }

      val personAttributeExtensions =
        openMRSHelper.extractPersonAttributeFromQuestionnaireResponse(
          questionnaire!!,
          questionnaireResponse,
        )

      if (patient.hasExtension()) {
        personAttributeExtensions.toMutableList().addAll(0, patient.extension)
      }
      patient.extension = personAttributeExtensions

      fhirEngine.create(patient)
      isPatientSaved.value = true
      saveInProgress = false
    }
  }

  private suspend fun createLocationIdentifiers(
    location: Location,
    fetchIdentifiers: Boolean = true,
  ): List<Identifier> {
    val identifierList: MutableList<Identifier> = mutableListOf()
    val selectedIdentifierTypes =
      applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.toList()
    if (selectedIdentifierTypes != null) {
      for (identifierTypeId in selectedIdentifierTypes) {
        val identifierType = database.dao().getIdentifierTypeById(identifierTypeId) ?: continue
        val isAutoGenerated = identifierType.isAutoGenerated ?: false
        val value = if (fetchIdentifiers) identifierType.uuid else null

        if (!isAutoGenerated && value.isNullOrBlank()) continue

        val identifier =
          Identifier().apply {
            id = generateUuid()
            use = Identifier.IdentifierUse.OFFICIAL
            this.value = value
            type = CodeableConcept().apply { text = identifierType.display }
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
      val value = identifierItem.answerFirstRep.valueStringType.value
      if (value.isNullOrBlank()) return@forEach

      val identifier =
        Identifier().apply {
          id = generateUuid()
          use = Identifier.IdentifierUse.OFFICIAL
          this.value = value
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
      questionnaire =
        fhirEngine.getQuestionnaireOrFromAssets(questionnaireName, applicationContext, parser)
      if (questionnaire != null) {
        embeddedQuestionnaire.value =
          parser.encodeResourceToString(embeddIdentifierInQuestionnaire(questionnaire!!))
      } else {
        embeddedQuestionnaire.value = ""
      }
    }
  }

  private suspend fun embeddIdentifierInQuestionnaire(questionnaire: Questionnaire): Questionnaire {
    val questionnaireItems = questionnaire.item
    val selectedIdentifiers =
      applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]
    val filteredIdentifiers: List<String>
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

  fun QuestionnaireResponse.findItemByLinkId(
    linkId: String,
  ): QuestionnaireResponse.QuestionnaireResponseItemComponent? {
    fun recurse(
      items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>,
    ): QuestionnaireResponse.QuestionnaireResponseItemComponent? {
      for (item in items) {
        if (item.linkId == linkId) return item
        val nested = recurse(item.item)
        if (nested != null) return nested
      }
      return null
    }
    return recurse(this.item)
  }

  private fun generateUuid(): String {
    return UUID.randomUUID().toString()
  }
}
