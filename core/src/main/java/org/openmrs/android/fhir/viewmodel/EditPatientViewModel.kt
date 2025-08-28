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
import ca.uhn.fhir.model.api.TemporalPrecisionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.allItems
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.get
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Address
import org.hl7.fhir.r4.model.BooleanType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.ContactPoint
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_TYPE_URL
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_URL
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_VALUE_URL
import org.openmrs.android.fhir.Constants.PERSON_ATTRIBUTE_LINK_ID_PREFIX
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssetsAsString
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
  private val openMRSHelper: OpenMRSHelper,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<EditPatientViewModel> {
    override fun create(
      handle: SavedStateHandle,
    ): EditPatientViewModel
  }

  private val patientId: String = requireNotNull(state["patient_id"])
  private val registrationQuestionnaireName: String =
    requireNotNull(state["registration_questionnaire_name"])
  val livePatientData = liveData { emit(prepareEditPatient(registrationQuestionnaireName)) }
  lateinit var originalPatient: Patient
  lateinit var questionnaireResource: Questionnaire
  val isPatientSaved = MutableLiveData<Boolean>()

  private suspend fun prepareEditPatient(questionnaireId: String): Pair<String, String> {
    val patient = fhirEngine.get<Patient>(patientId)
    originalPatient = patient
    val launchContexts = mapOf<String, Resource>("client" to patient)
    val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

    val question =
      fhirEngine.getQuestionnaireOrFromAssetsAsString(questionnaireId, applicationContext, parser)

    return if (question.isNotEmpty()) {
      questionnaireResource =
        parser.parseResource(Questionnaire::class.java, question) as Questionnaire

      val questionnaireResponse: QuestionnaireResponse =
        getQuestionnaireResponseWithPersonAttribute(
          ResourceMapper.populate(questionnaireResource, launchContexts),
          patient,
        )

      prefillEstimatedAgeIfDobUnknown(questionnaireResponse, patient)

      val questionnaireResponseJson = parser.encodeResourceToString(questionnaireResponse)
      question to questionnaireResponseJson
    } else {
      "" to ""
    }
  }

  private fun getQuestionnaireResponseWithPersonAttribute(
    questionnaireResponse: QuestionnaireResponse,
    patient: Patient,
  ): QuestionnaireResponse {
    val personAttributeValueMap = mutableMapOf<String, Type>()
    patient.extension
      .filter { it.url == OPENMRS_PERSON_ATTRIBUTE_URL }
      .forEach {
        val linkId =
          PERSON_ATTRIBUTE_LINK_ID_PREFIX +
            it.extension
              .firstOrNull { it.url == OPENMRS_PERSON_ATTRIBUTE_TYPE_URL }
              ?.value
              ?.toString()

        var extensionValue =
          it.extension.firstOrNull { it.url == OPENMRS_PERSON_ATTRIBUTE_VALUE_URL }?.value as Type

        if (extensionValue is CodeableConcept) {
          extensionValue = extensionValue.coding.firstOrNull() as Type
        }
        personAttributeValueMap[linkId] = extensionValue
      }

    // Process items recursively
    fillPersonAttributeQuestionnaireItemRecursively(
      questionnaireResponse.item,
      personAttributeValueMap,
    )

    return questionnaireResponse
  }

  private fun fillPersonAttributeQuestionnaireItemRecursively(
    items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>,
    personAttributeValueMap: Map<String, Type>,
  ) {
    items.forEach { item ->
      // Process current item
      if (item.linkId.contains(PERSON_ATTRIBUTE_LINK_ID_PREFIX)) {
        if (personAttributeValueMap.containsKey(item.linkId)) {
          item.answer.add(
            QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
              value = personAttributeValueMap[item.linkId]
            },
          )
        } else {
          item.answer.clear()
        }
      }

      // Recursively process nested items if they exist
      if (item.item.isNotEmpty()) {
        fillPersonAttributeQuestionnaireItemRecursively(item.item, personAttributeValueMap)
      }
    }
  }

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
          patient.hasGender()
      ) {
        // Name
        originalPatient.name[0].given = patient.name[0].given
        originalPatient.name[0].family = patient.name[0].family
        if (patient.name[0].hasText()) {
          originalPatient.name[0].text = patient.name[0].text
        }

        // Birth date and gender
        originalPatient.birthDateElement = calculatePatientBirthDate(questionnaireResponse)
        originalPatient.gender = patient.gender

        // Telecom
        if (patient.hasTelecom()) {
          val telecom = patient.telecomFirstRep
          if (originalPatient.hasTelecom()) {
            originalPatient.telecomFirstRep.system = telecom.system
            originalPatient.telecomFirstRep.value = telecom.value
          } else {
            originalPatient.telecom =
              mutableListOf(
                ContactPoint().apply {
                  system = telecom.system
                  value = telecom.value
                },
              )
          }
        }

        // Address
        if (patient.hasAddress()) {
          val address = patient.addressFirstRep
          if (originalPatient.hasAddress()) {
            originalPatient.addressFirstRep.city = address.city
            originalPatient.addressFirstRep.country = address.country
          } else {
            originalPatient.address =
              mutableListOf(
                Address().apply {
                  city = address.city
                  country = address.country
                },
              )
          }
        }

        val personAttributeExtensions =
          openMRSHelper.extractPersonAttributeFromQuestionnaireResponse(
            questionnaireResource,
            questionnaireResponse,
          )

        if (patient.hasExtension()) {
          personAttributeExtensions
            .toMutableList()
            .addAll(0, patient.extension.filterNot { it.url == OPENMRS_PERSON_ATTRIBUTE_URL })
        }
        originalPatient.extension = personAttributeExtensions

        fhirEngine.update(originalPatient)
        isPatientSaved.value = true
        return@launch
      }

      isPatientSaved.value = false
    }
  }

  fun calculatePatientBirthDate(response: QuestionnaireResponse): DateType? {
    val dob = response.findItemByLinkId("patient-0-birth-date")?.answerFirstRep?.value as? DateType
    val dobKnown = response.findItemByLinkId("dobKnown")?.answerFirstRep?.value as? BooleanType
    val estimatedYears =
      response.findItemByLinkId("estimatedDateOfBirthYears")?.answerFirstRep?.value as? Quantity
    val estimatedMonths =
      response.findItemByLinkId("estimatedDateOfBirthMonths")?.answerFirstRep?.value as? Quantity

    val isDobUnknown = dobKnown?.booleanValue() == false
    if (dob != null && !isDobUnknown) return dob

    if (isDobUnknown && estimatedYears != null) {
      return openMRSHelper.getDateDiffByQuantity(estimatedYears, estimatedMonths)
    }

    return null
  }

  fun QuestionnaireResponse.findItemByLinkId(
    linkId: String,
  ): QuestionnaireResponse.QuestionnaireResponseItemComponent? {
    return this.allItems.firstOrNull { it.linkId == linkId }
  }

  private fun prefillEstimatedAgeIfDobUnknown(
    response: QuestionnaireResponse,
    patient: Patient,
  ) {
    if (!patient.hasBirthDateElement()) return
    val birthDateElement = patient.birthDateElement

    val dobKnownItem = response.findItemByLinkId("dobKnown")
    val estimatedAgeYearsItem = response.findItemByLinkId("estimatedDateOfBirthYears") ?: return
    val estimatedAgeMonthsItem = response.findItemByLinkId("estimatedDateOfBirthMonths")

    val birthDate = birthDateElement.value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    val now = LocalDate.now()

    when (birthDateElement.precision) {
      TemporalPrecisionEnum.DAY -> {
        dobKnownItem?.setBooleanAnswer(true)
      }
      TemporalPrecisionEnum.YEAR -> {
        dobKnownItem?.setBooleanAnswer(false)
        estimatedAgeYearsItem.setQuantityAnswer(
          (now.year - birthDate.year).toBigDecimal(),
          "years",
          "y",
        )
      }
      TemporalPrecisionEnum.MONTH -> {
        val period = Period.between(birthDate, now)
        dobKnownItem?.setBooleanAnswer(false)
        estimatedAgeYearsItem.setQuantityAnswer(period.years.toBigDecimal(), "years", "y")
        estimatedAgeMonthsItem?.setQuantityAnswer(period.months.toBigDecimal(), "months", "m")
      }
      else -> return
    }
  }

  // Extension functions for cleaner syntax
  private fun QuestionnaireResponse.QuestionnaireResponseItemComponent.setBooleanAnswer(
    value: Boolean,
  ) {
    answer.clear()
    answer.add(
      QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
        this.value = BooleanType(value)
      },
    )
  }

  private fun QuestionnaireResponse.QuestionnaireResponseItemComponent.setQuantityAnswer(
    value: BigDecimal,
    unit: String,
    code: String,
  ) {
    answer.clear()
    answer.add(
      QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
        this.value =
          Quantity().apply {
            system = "http://unitsofmeasure.org"
            this.value = value
            this.unit = unit
            this.code = code
          }
      },
    )
  }
}
