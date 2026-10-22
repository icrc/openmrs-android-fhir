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
import com.google.android.fhir.datacapture.extensions.allItems
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
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_TYPE_URL
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_URL
import org.openmrs.android.fhir.Constants.OPENMRS_PERSON_ATTRIBUTE_VALUE_URL
import org.openmrs.android.fhir.Constants.PERSON_ATTRIBUTE_LINK_ID_PREFIX
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.sync.LiveSync
import timber.log.Timber

class OpenMRSHelper
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val context: Context,
) {

  suspend fun getAuthenticatedUserId(): String? {
    return context.dataStore.data.first().get(PreferenceKeys.USER_UUID)
  }

  suspend fun getAuthenticatedProviderName(): String? {
    return context.dataStore.data.first().get(PreferenceKeys.USER_PROVIDER_NAME)
  }

  suspend fun getAuthenticatedProviderUuid(): String? {
    return context.dataStore.data.first().get(PreferenceKeys.USER_PROVIDER_UUID)
  }

  suspend fun getCurrentAuthLocation(): Reference {
    val selectedLocationId = context.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
    // Without a location the reference is empty and filled in once one is chosen.
    if (selectedLocationId.isNullOrBlank()) GuestSession.markCompletionPending(context)
    return Reference("Location/$selectedLocationId") // Reference to the location
  }

  /**
   * Whether a visit is created for every encounter that is saved (the default) or the user opens
   * and ends the visits (Settings > Visits).
   */
  suspend fun automaticVisits(): Boolean =
    try {
      DemoDataStore(context).getAutomaticVisitsFlow().first()
    } catch (e: Exception) {
      true
    }

  // Visit related functions
  suspend fun getVisits(
    patientId: String,
  ): Map<Encounter, List<Encounter>> = getVisitsAndOthers(patientId).visits

  /** The patient's visits, each with its encounters, and the encounters that are in no visit. */
  suspend fun getVisitsAndOthers(patientId: String): PatientVisits {
    val visits = linkedMapOf<Encounter, List<Encounter>>()

    val allEncounters = LinkedList<Encounter>()

    fhirEngine
      .search<Encounter> {
        filter(Encounter.SUBJECT, { value = "Patient/$patientId" })
        sort(Encounter.DATE, Order.DESCENDING)
      }
      .map { it.resource }
      .let { allEncounters.addAll(it) }

    val visitEncounters =
      allEncounters
        .filter { encounter ->
          encounter.type.any { type ->
            type.coding.any { coding -> coding.system == Constants.VISIT_TYPE_CODE_SYSTEM }
          }
        }
        .sortedByDescending { encounter ->
          encounter.period?.start ?: encounter.period?.end ?: Date(0)
        }

    visitEncounters.forEach { visit ->
      visits[visit] =
        allEncounters
          .filter { it.partOf?.reference.equals("Encounter/" + visit.idPart) }
          .sortedByDescending { encounter ->
            encounter.period?.start ?: encounter.period?.end ?: Date(0)
          }
    }

    val visitIds = visitEncounters.map { "Encounter/" + it.idPart }.toSet()
    val withoutVisit =
      allEncounters
        .filter { it !in visitEncounters && it.partOf?.reference !in visitIds }
        .sortedByDescending { encounter ->
          encounter.period?.start ?: encounter.period?.end ?: Date(0)
        }

    return PatientVisits(visits, withoutVisit)
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
    LiveSync.requestUpload(context)
    return encounter
  }

  /** The visit's start and end, and the span of its encounters. */
  suspend fun visitEditState(visitId: String): VisitEditState? {
    val visit: Encounter = fhirEngine.get(visitId)
    val start = visit.period?.start ?: return null
    val encounters = encountersOf(visit)
    return VisitEditState(
      start = start,
      end = visit.period?.end,
      earliest = encounters.mapNotNull { it.period?.start ?: it.period?.end }.minOrNull(),
      latest = encounters.mapNotNull { it.period?.end ?: it.period?.start }.maxOrNull(),
    )
  }

  private suspend fun encountersOf(visit: Encounter): List<Encounter> =
    fhirEngine
      .search<Encounter> {
        filter(Encounter.SUBJECT, { value = visit.subject.reference.orEmpty() })
      }
      .map { it.resource }
      .filter { it.partOf?.reference == "Encounter/" + visit.idPart }

  /**
   * Changes when a visit started and ended (`null`: still open). The visit has to contain its
   * encounters, cannot reach into the future and cannot be open while another visit is.
   *
   * @return what stopped it, or `null` when it was saved.
   */
  suspend fun updateVisitPeriod(visitId: String, start: Date, end: Date?): VisitPeriodError? {
    val visit: Encounter = fhirEngine.get(visitId)
    val encounters = encountersOf(visit)
    VisitPeriodRules.check(
        start = start,
        end = end,
        earliest = encounters.mapNotNull { it.period?.start ?: it.period?.end }.minOrNull(),
        latest = encounters.mapNotNull { it.period?.end ?: it.period?.start }.maxOrNull(),
      )
      ?.let {
        return it
      }
    if (end == null) {
      val patientId = visit.subject?.reference?.substringAfter("Patient/").orEmpty()
      val open = getActiveVisit(patientId, false)
      if (open != null && open.idPart != visitId) return VisitPeriodError.OTHER_VISIT_OPEN
    }
    if (!visit.hasPeriod()) visit.period = Period()
    visit.period.start = start
    visit.period.end = end
    visit.status =
      if (end == null) Encounter.EncounterStatus.INPROGRESS else Encounter.EncounterStatus.FINISHED
    fhirEngine.update(visit)
    LiveSync.requestUpload(context)
    return null
  }

  suspend fun endVisit(visitId: String): Encounter {
    val encounter: Encounter = fhirEngine.get(visitId)
    val now = Date()
    // Never before it started (a visit opened at a time the clock has not reached yet).
    val end = encounter.period?.start?.takeIf { now.before(it) } ?: now
    if (encounter.hasPeriod()) {
      encounter.period.end = end
    } else {
      encounter.period = Period().apply { this.end = end }
    }
    encounter.status = Encounter.EncounterStatus.FINISHED
    fhirEngine.update(encounter)
    LiveSync.requestUpload(context)
    return encounter
  }

  /**
   * Opens an ended visit again. Only one visit is open at a time, so this does nothing (and says
   * so) while another one is.
   *
   * @return whether the visit was reopened.
   */
  suspend fun reopenVisit(visitId: String): Boolean {
    val encounter: Encounter = fhirEngine.get(visitId)
    val patientId = encounter.subject?.reference?.substringAfter("Patient/").orEmpty()
    val openVisit = getActiveVisit(patientId, false)
    if (openVisit != null && openVisit.idPart != visitId) return false
    encounter.period?.end = null
    encounter.status = Encounter.EncounterStatus.INPROGRESS
    fhirEngine.update(encounter)
    LiveSync.requestUpload(context)
    return true
  }

  /**
   * The signed-in user and provider, read from the server when they are not saved, and what was
   * created without them (a `Practitioner/null` participant, which the server refuses) is
   * corrected. Offline it does what it can: whatever is still missing is repaired later.
   *
   * @return whether the provider is known.
   */
  suspend fun ensureIdentity(): Boolean {
    var preferences = context.dataStore.data.first()
    fun known() =
      !preferences[PreferenceKeys.USER_UUID].isNullOrBlank() &&
        !preferences[PreferenceKeys.USER_PROVIDER_UUID].isNullOrBlank()
    if (!known()) {
      RestApiManager.getInstance(context).refreshIdentity()
      preferences = context.dataStore.data.first()
    }
    if (!known()) return false
    repairParticipants()
    return true
  }

  /** Encounters waiting for upload whose participant was written before the provider was known. */
  private suspend fun repairParticipants() {
    val preferences = context.dataStore.data.first()
    val providerUuid = preferences[PreferenceKeys.USER_PROVIDER_UUID]?.takeIf { it.isNotBlank() }
    val userUuid = preferences[PreferenceKeys.USER_UUID]?.takeIf { it.isNotBlank() }
    val providerName = preferences[PreferenceKeys.USER_PROVIDER_NAME]
    if (providerUuid == null || userUuid == null) return
    fun missing(reference: String?) =
      reference == "Practitioner/null" || reference == "Practitioner/"
    fhirEngine
      .search<Encounter> { filter(Encounter.PARTICIPANT, { value = "Practitioner/null" }) }
      .map { it.resource }
      .filter { encounter -> encounter.participant.any { missing(it.individual?.reference) } }
      .filter {
        fhirEngine.getLocalChanges(ResourceType.Encounter, it.idElement.idPart).isNotEmpty()
      }
      .forEach { encounter ->
        val isVisit =
          encounter.type.any { type ->
            type.coding.any { it.system == Constants.VISIT_TYPE_CODE_SYSTEM }
          }
        encounter.participant
          .filter { missing(it.individual?.reference) }
          .forEach {
            it.individual =
              Reference("Practitioner/${if (isVisit) userUuid else providerUuid}").apply {
                display = providerName
                type = "Practitioner"
              }
          }
        fhirEngine.update(encounter)
        Timber.i("Encounter %s received its provider", encounter.idElement.idPart)
      }
  }

  suspend fun createEncounterParticipant(): EncounterParticipantComponent {
    if (getAuthenticatedProviderUuid().isNullOrBlank()) {
      RestApiManager.getInstance(context).refreshIdentity()
    }
    val participant = EncounterParticipantComponent()
    participant.individual = Reference("Practitioner/${getAuthenticatedProviderUuid()}")
    participant.individual.display = getAuthenticatedProviderName()
    participant.individual.type = "Practitioner"
    return participant
  }

  suspend fun createVisitParticipant(): EncounterParticipantComponent {
    if (getAuthenticatedUserId().isNullOrBlank()) {
      RestApiManager.getInstance(context).refreshIdentity()
    }
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
            val extensionValue = item.answer[0].value
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

  /**
   * The birth date the registration answers describe: the date itself when it is known, otherwise
   * today minus the estimated age (years, plus months where they were asked). `null` when neither
   * was answered. Registering and editing a patient both use this one rule.
   */
  fun birthDateFrom(response: QuestionnaireResponse): DateType? {
    fun answer(linkId: String) =
      response.allItems.firstOrNull { it.linkId == linkId }?.answerFirstRep?.value
    val known = (answer("dobKnown") as? BooleanType)?.booleanValue()
    val date = answer("patient-0-birth-date") as? DateType
    if (known != false && date != null) return date
    val years = answer("estimatedDateOfBirthYears") as? Quantity ?: return null
    return getDateDiffByQuantity(years, answer("estimatedDateOfBirthMonths") as? Quantity)
  }

  /** Whether [birthDate] lies after today: nobody is born in the future. */
  fun isInTheFuture(birthDate: DateType): Boolean =
    birthDate.value
      ?.toInstant()
      ?.atZone(ZoneId.systemDefault())
      ?.toLocalDate()
      ?.isAfter(
        LocalDate.now(),
      ) == true

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

/** A patient's visits, each with its encounters, and the encounters that belong to no visit. */
data class PatientVisits(
  val visits: Map<Encounter, List<Encounter>>,
  val withoutVisit: List<Encounter>,
)

/** A visit that has started and not ended. */
fun Encounter.isOpenVisit(): Boolean =
  type.any { type -> type.coding.any { it.system == Constants.VISIT_TYPE_CODE_SYSTEM } } &&
    period?.start != null &&
    period?.end == null
