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
package org.openmrs.android.fhir.data

import android.content.Context
import ca.uhn.fhir.model.api.TemporalPrecisionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.get
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.LinkedList
import javax.inject.Inject
import kotlin.collections.forEach
import kotlinx.coroutines.flow.first
import org.hl7.fhir.r4.model.BooleanType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_TYPE_URL
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_URL
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_VALUE_URL
import org.openmrs.android.fhir.Constants.PERSON_ATTRIBUTE_LINK_ID_PREFIX
import org.openmrs.android.fhir.auth.dataStore

class OpenMRSHelper
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val context: Context,
) {

  suspend fun getAuthenticatedUserId(): String? {
    return context.dataStore.data.first().get(PreferenceKeys.USER_UUID)
  }

  suspend fun getAuthenticatedUserName(): String? {
    return context.dataStore.data.first().get(PreferenceKeys.USER_NAME)
  }

  suspend fun getAuthenticatedProviderName(): String? {
    return context.dataStore.data.first().get(PreferenceKeys.USER_PROVIDER_NAME)
  }

  suspend fun getAuthenticatedProviderUuid(): String? {
    return context.dataStore.data.first().get(PreferenceKeys.USER_PROVIDER_UUID)
  }

  suspend fun getCurrentAuthLocation(): Reference {
    val selectedLocationId = context.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
    return Reference("Location/$selectedLocationId") // Reference to the location
  }

  // Visit related functions
  suspend fun getVisits(
    patientId: String,
  ): Map<Encounter, List<Encounter>> {
    val visits: MutableMap<Encounter, List<Encounter>> = HashMap()

    val allEncounters = LinkedList<Encounter>()

    fhirEngine
      .search<Encounter> {
        filter(Encounter.SUBJECT, { value = "Patient/$patientId" })
        sort(Encounter.DATE, Order.DESCENDING)
      }
      .map { it.resource }
      .let { allEncounters.addAll(it) }

    val visitEncounters =
      allEncounters.filter { encounter ->
        encounter.type.any { type ->
          type.coding.any { coding -> coding.system == Constants.VISIT_TYPE_CODE_SYSTEM }
        }
      }

    visitEncounters.forEach { visit ->
      visits[visit] =
        allEncounters.filter { it.partOf?.reference.equals("Encounter/" + visit.idPart) }
    }

    return visits
  }

  suspend fun getActiveVisit(
    patientId: String,
    shouldCreateVisit: Boolean,
  ): Encounter? {
    val searchResult =
      fhirEngine.search<Encounter> { filter(Encounter.SUBJECT, { value = "Patient/$patientId" }) }
    val encounters = searchResult.map { it.resource }

    // Find and return the active encounter
    var currentVisit =
      encounters.find { encounter: Encounter ->
        val isVisitType =
          encounter.type?.firstOrNull()?.coding?.firstOrNull()?.system ==
            Constants.VISIT_TYPE_CODE_SYSTEM
        val period = encounter.period
        val startDate = period?.start
        val endDate = period?.end

        isVisitType && startDate != null && endDate == null && startDate.before(Date())
      }

    if (currentVisit == null && shouldCreateVisit) {
      currentVisit =
        startVisit(
          patientId,
          Constants.VISIT_TYPE_UUID,
          Date(),
        )
    }
    return currentVisit
  }

  suspend fun startVisit(
    patientId: String,
    visitTypeId: String,
    startDate: Date,
  ): Encounter {
    val encounter =
      Encounter().apply {
        subject = Reference("Patient/$patientId")
        status = Encounter.EncounterStatus.INPROGRESS
        setPeriod(Period().apply { start = startDate })
        addParticipant(createVisitParticipant())
        addLocation(
          Encounter.EncounterLocationComponent().apply { location = getCurrentAuthLocation() },
        )
        addType(
          CodeableConcept().apply {
            coding =
              listOf(
                Coding().apply {
                  system = Constants.VISIT_TYPE_CODE_SYSTEM
                  code = visitTypeId
                  display = "Facility Visit"
                },
              )
          },
        )
      }
    fhirEngine.create(encounter)
    return encounter
  }

  suspend fun endVisit(visitId: String): Encounter {
    val encounter: Encounter = fhirEngine.get(visitId)
    val currentDate = Date()
    if (encounter.hasPeriod()) {
      encounter.period.end = Date()
    } else {
      encounter.period = Period().apply { end = currentDate }
    }
    fhirEngine.update(encounter)
    return encounter
  }

  suspend fun createEncounterParticipant(): EncounterParticipantComponent {
    val participant = EncounterParticipantComponent()
    participant.individual = Reference("Practitioner/${getAuthenticatedProviderUuid()}")
    participant.individual.display = getAuthenticatedProviderName()
    participant.individual.type = "Practitioner"
    return participant
  }

  suspend fun createVisitParticipant(): EncounterParticipantComponent {
    val participant = EncounterParticipantComponent()
    participant.individual = Reference("Practitioner/${getAuthenticatedUserId()}")
    participant.individual.display = getAuthenticatedProviderName()
    participant.individual.type = "Practitioner"
    return participant
  }

  /**
   * Extracts person attributes from a QuestionnaireResponse based on the provided Questionnaire.
   * This function identifies questionnaire items related to person attributes (identified by the
   * PERSON_ATTRIBUTE_LINK_ID_PREFIX), extracts their values from the QuestionnaireResponse, and
   * converts them into FHIR Extensions.
   *
   * @param questionnaire The Questionnaire containing person attribute definitions
   * @param questionnaireResponse The QuestionnaireResponse containing submitted values
   * @return List of Extensions representing the person attributes found in the response
   */
  fun extractPersonAttributeFromQuestionnaireResponse(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): List<Extension> {
    val personAttributeQuestionnaireLinkIds = extractAllItemLinkIds(questionnaire)
    val extensionList = mutableListOf<Extension>()

    fun traverseQuestionnaireResponseItems(
      items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>?,
    ) {
      items?.forEach { item ->
        // Add the current item's ID
        if (item.linkId != null && personAttributeQuestionnaireLinkIds.contains(item.linkId)) {
          if (item.answer.isNotEmpty()) {
            var extensionValue = item.answer[0].value
            extensionList.add(getPersonAttributeExtension(extensionValue, item.linkId))
          }
        }

        // Recursively process child items
        traverseQuestionnaireResponseItems(item.item)
      }
    }

    traverseQuestionnaireResponseItems(questionnaireResponse.item)

    return extensionList
  }

  /**
   * Creates a FHIR Extension for a person attribute with the appropriate structure. This function
   * builds a nested extension structure where the main extension uses the OpenMRS person attribute
   * URL, and contains a child extension with the specific attribute type URL and value.
   *
   * @param extensionValue The value of the person attribute (as a FHIR Type)
   * @param personAttributeQuestionnaireItem The questionnaire item containing attribute metadata
   * @return A properly structured FHIR Extension for the person attribute
   */
  fun getPersonAttributeExtension(extensionValue: Type, linkId: String): Extension {
    return Extension().apply {
      url = OPENMRS_PERSON_ATTRIBUTE_URL
      extension =
        listOf(
          Extension().apply {
            url = OPENMRS_PERSON_ATTRIBUTE_TYPE_URL
            value = StringType(linkId.substringAfter("#"))
          },
          Extension().apply {
            url = OPENMRS_PERSON_ATTRIBUTE_VALUE_URL
            value = extensionValue
            value =
              when (val v = extensionValue) {
                is CodeableConcept -> v
                is Coding -> CodeableConcept().addCoding(v)
                is BooleanType -> v
                is StringType -> v
                else -> null
              }
          },
        )
    }
  }

  private fun extractAllItemLinkIds(questionnaire: Questionnaire): Set<String> {
    val linkIds = mutableSetOf<String>()

    // Helper function for recursive traversal
    fun traverseQuestionnaireItems(items: List<QuestionnaireItemComponent>?) {
      items?.forEach { item ->
        // Add the current item's ID
        item.linkId?.let { if (it.contains(PERSON_ATTRIBUTE_LINK_ID_PREFIX)) linkIds.add(it) }

        // Recursively process child items
        traverseQuestionnaireItems(item.item)
      }
    }
    // Start traversal with top-level items
    traverseQuestionnaireItems(questionnaire.item)

    return linkIds
  }

  fun getDateDiffByQuantity(estimatedAgeYear: Quantity, estimatedAgeMonth: Quantity?): DateType {
    val now = LocalDate.now()
    val year = estimatedAgeYear.value.toLong()
    val month = estimatedAgeMonth?.value?.toLong()
    val date = now.minusYears(year).minusMonths(month ?: 0)
    val precision = if (month != null) TemporalPrecisionEnum.MONTH else TemporalPrecisionEnum.YEAR

    return DateType(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())).apply {
      this.precision = precision
    }
  }
}
