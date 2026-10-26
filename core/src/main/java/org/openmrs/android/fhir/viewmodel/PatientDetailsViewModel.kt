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
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.revInclude
import com.google.android.fhir.search.search
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.hl7.fhir.r4.model.BooleanType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.ContactPoint
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.data.PatientVisits
import org.openmrs.android.fhir.data.VisitEditState
import org.openmrs.android.fhir.data.VisitPeriodError
import org.openmrs.android.fhir.data.isOpenVisit
import org.openmrs.android.fhir.data.sync.LivePatients
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.FormSummary
import org.openmrs.android.fhir.extensions.allFormSummaries
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets
import org.openmrs.android.fhir.extensions.toLocalString
import org.openmrs.android.fhir.unidentified.UnidentifiedPatients
import timber.log.Timber

/**
 * The ViewModel helper class for PatientItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
class PatientDetailsViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val openMRSHelper: OpenMRSHelper,
  private val unidentified: UnidentifiedPatients,
  @Assisted val state: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<PatientDetailsViewModel> {
    override fun create(
      handle: SavedStateHandle,
    ): PatientDetailsViewModel
  }

  private val patientId: String = requireNotNull(state["patient_id"])

  private val live by lazy { LivePatients(applicationContext, fhirEngine) }

  init {
    AuditLog.record(AuditAction.PATIENT_OPENED, AuditOutcome.SUCCESS, "patient" to patientId)
    LivePatients.markOpen(patientId)
    // An upload went through: what is shown as waiting for the server may not be any more.
    viewModelScope.launch {
      LiveSync.uploaded.collect { if (livePatientData.value != null) getPatientDetailData() }
    }
    viewModelScope.launch { refreshFromServer() }
  }

  override fun onCleared() {
    // The patient was brought by the live search and the user does not want those kept: they are
    // removed now that the page is closed (or after the next upload, if something waits).
    LivePatients.markClosed(patientId)
    LivePatients.discardInBackground(applicationContext, fhirEngine)
    super.onCleared()
  }

  /**
   * Live mode: the record on the device may be older than the server's (someone else saw the
   * patient), so it is read again as the patient is opened. What waits for upload here is kept.
   */
  private suspend fun refreshFromServer() {
    try {
      if (!LiveSync.isEnabled(applicationContext) || LivePatients.isFresh(patientId)) return
      withTimeout(REFRESH_TIMEOUT_MILLIS) {
        withContext(Dispatchers.IO) { live.download(patientId, remember = false) }
      }
      if (livePatientData.value != null) getPatientDetailData()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Offline, or a patient the server does not know yet: the device's copy is what is shown.
      Timber.d(e, "The patient was not refreshed from the server")
    }
  }

  val livePatientData = MutableLiveData<List<PatientDetailData>>()

  private val parser = FhirContext.forR4Cached().newJsonParser()
  private val deviceLanguage = AppLanguage.currentTag(applicationContext).lowercase(Locale.ROOT)

  /** Emits list of [PatientDetailData]. */
  fun getPatientDetailData() {
    viewModelScope.launch { livePatientData.value = getPatientDetailDataModel() }
  }

  private suspend fun getPatientDetailDataModel(): List<PatientDetailData> {
    val encounterTypeCodeToQuestionnaireMap = mapEncounterTypeCodesToQuestionnaires()

    val searchResult =
      fhirEngine.search<Patient> {
        filter(Resource.RES_ID, { value = of(patientId) })
        revInclude<Encounter>(Encounter.SUBJECT)
      }

    val patientResource = searchResult.firstOrNull()?.resource ?: return emptyList()

    val data = mutableListOf<PatientDetailData>()
    val patientVisits = openMRSHelper.getVisitsAndOthers(patientId)
    data.addPatientDetailData(patientResource)
    if (!openMRSHelper.automaticVisits()) {
      // The visits are the user's: the encounters are shown under the visit they belong to.
      data.addVisitsWithTheirEncounters(patientVisits, encounterTypeCodeToQuestionnaireMap)
      return data
    }
    data.add(PatientDetailHeader(getString(R.string.header_encounters)))
    for ((_, encounters) in patientVisits.visits) {
      encounters.forEach { encounter ->
        val isSynced = isEncounterSynced(encounter)
        data.addEncounterData(encounter, isSynced, encounterTypeCodeToQuestionnaireMap)
      }
    }

    return data.sortedBy {
      when (it) {
        is PatientDetailOverview -> false
        is PatientDetailUnidentified -> false
        is PatientDetailHeader -> false
        is PatientDetailEncounter -> it.encounter.isSynced
        else -> true
      }
    }
  }

  private suspend fun isEncounterSynced(encounter: Encounter): Boolean {
    val observations =
      fhirEngine.search<Observation> {
        filter(Observation.ENCOUNTER, { value = encounter.logicalId })
      }
    observations.forEach { observation ->
      val isObservationSynced =
        fhirEngine
          .getLocalChanges(ResourceType.Observation, observation.resource.logicalId)
          .isEmpty()
      if (!isObservationSynced) {
        return false
      }
    }
    return fhirEngine.getLocalChanges(ResourceType.Encounter, encounter.logicalId).isEmpty()
  }

  private suspend fun MutableList<PatientDetailData>.addPatientDetailData(
    patient: Patient,
  ) {
    patient.toPatientItem(0).let { patientItem ->
      patientItem.isSynced =
        fhirEngine.getLocalChanges(ResourceType.Patient, patientItem.resourceId).isEmpty()
      val toIdentify = unidentified.note(patientItem.resourceId)
      add(
        PatientDetailOverview(
          patientItem,
          firstInGroup = true,
          extraDetails =
            if (toIdentify == null) extraDetailsOf(patient, patientItem.phone) else emptyList(),
          unidentified = toIdentify != null,
        ),
      )
      if (toIdentify != null) add(PatientDetailUnidentified(toIdentify))
      if (patientItem.isSynced == false) {
        add(PatientUnsynced(false, false))
      }
      // Add other patient details if necessary
    }
  }

  /**
   * What the record holds beyond the profile card: every contact (phone numbers, e-mail), the full
   * address and the person attributes of the registration form (civil status, languages, the
   * secondary contact...), labelled with the form's own wording.
   */
  private suspend fun extraDetailsOf(
    patient: Patient,
    shownPhone: String,
  ): List<PatientExtraDetail> {
    val details = mutableListOf<PatientExtraDetail>()
    patient.telecom
      .filter { it.hasValue() && it.value != shownPhone }
      .forEach { point ->
        val value = point.value
        val kind =
          when {
            point.system == ContactPoint.ContactPointSystem.EMAIL || value.contains('@') ->
              PatientExtraDetail.Kind.EMAIL
            point.system == ContactPoint.ContactPointSystem.PHONE ||
              point.system == ContactPoint.ContactPointSystem.SMS ||
              value.looksLikePhoneNumber() -> PatientExtraDetail.Kind.PHONE
            else -> PatientExtraDetail.Kind.TEXT
          }
        val label =
          getString(
            when (kind) {
              PatientExtraDetail.Kind.PHONE -> R.string.patient_phone_number
              PatientExtraDetail.Kind.EMAIL -> R.string.patient_detail_email
              PatientExtraDetail.Kind.TEXT -> R.string.patient_detail_contact
            },
          )
        details += PatientExtraDetail(label, value, kind)
      }
    patient.address.forEach { address ->
      val parts =
        listOf(
          address.line.joinToString(", ") { it.value.orEmpty() },
          address.city,
          address.district,
          address.state,
          address.postalCode,
          address.country,
        )
      val text = parts.filter { !it.isNullOrBlank() }.joinToString(", ")
      // The profile card already shows the city and the country.
      if (
        text.isNotBlank() &&
          (address.hasLine() ||
            address.hasDistrict() ||
            address.hasState() ||
            address.hasPostalCode())
      ) {
        details +=
          PatientExtraDetail(
            getString(R.string.patient_property_address),
            text,
            PatientExtraDetail.Kind.TEXT,
          )
      }
    }
    val attributes = patient.extension.filter { it.url == Constants.OPENMRS_PERSON_ATTRIBUTE_URL }
    if (attributes.isNotEmpty()) {
      val labels = registrationFormLabels()
      attributes.forEach { attribute ->
        val type =
          attribute.extension
            .firstOrNull { it.url == Constants.OPENMRS_PERSON_ATTRIBUTE_TYPE_URL }
            ?.value
            ?.primitiveValue()
            .orEmpty()
        val value =
          attribute.extension
            .firstOrNull { it.url == Constants.OPENMRS_PERSON_ATTRIBUTE_VALUE_URL }
            ?.value
            ?.displayText()
        if (type.isBlank() || value.isNullOrBlank()) return@forEach
        val label =
          labels[Constants.PERSON_ATTRIBUTE_LINK_ID_PREFIX + type] ?: type.prettyAttributeName()
        val kind =
          when {
            value.contains('@') -> PatientExtraDetail.Kind.EMAIL
            type.contains("phone", ignoreCase = true) -> PatientExtraDetail.Kind.PHONE
            else -> PatientExtraDetail.Kind.TEXT
          }
        details += PatientExtraDetail(label, value, kind)
      }
    }
    return details
  }

  /** The registration form's wording of each person attribute, by its link id. */
  private suspend fun registrationFormLabels(): Map<String, String> =
    try {
      withContext(Dispatchers.Default) {
        val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
        val form =
          fhirEngine.getQuestionnaireOrFromAssets(
            AppConfig.get(applicationContext).registrationForm,
            applicationContext,
            parser,
          )
        val labels = mutableMapOf<String, String>()
        fun walk(items: List<Questionnaire.QuestionnaireItemComponent>) {
          items.forEach {
            if (it.linkId.startsWith(Constants.PERSON_ATTRIBUTE_LINK_ID_PREFIX) && it.hasText()) {
              labels[it.linkId] = it.text
            }
            walk(it.item)
          }
        }
        form?.let { walk(it.item) }
        labels
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Could not read the registration form's labels")
      emptyMap()
    }

  private fun Type.displayText(): String? =
    when (this) {
      is CodeableConcept -> coding.firstOrNull()?.display ?: text ?: coding.firstOrNull()?.code
      is Coding -> display ?: code
      is BooleanType -> getString(if (value == true) R.string.yes else R.string.no)
      else -> primitiveValue()
    }

  private fun String.looksLikePhoneNumber(): Boolean =
    count { it.isDigit() } >= 5 && all { it.isDigit() || it in "+-() ." }

  /** `registrationapp.patient.hsuSecondaryPhoneNumber` -> "Hsu Secondary Phone Number". */
  private fun String.prettyAttributeName(): String =
    substringAfterLast('.')
      .replace(Regex("([a-z])([A-Z])"), "$1 $2")
      .replace('_', ' ')
      .replaceFirstChar { it.titlecase(Locale.getDefault()) }

  /** The note of a patient to identify after, rewritten. */
  fun updateNote(note: String) {
    viewModelScope.launch {
      unidentified.updateNote(patientId, note)
      getPatientDetailData()
    }
  }

  /** Visits are created for the user (the default) or opened and ended by them. */
  suspend fun automaticVisits(): Boolean = openMRSHelper.automaticVisits()

  suspend fun hasActiveVisit(): Boolean = openMRSHelper.getActiveVisit(patientId, false) != null

  /** Opens a visit that started at [startDate], and shows it. */
  suspend fun startVisit(startDate: Date) {
    openMRSHelper.startVisit(patientId, Constants.VISIT_TYPE_UUID, startDate)
    getPatientDetailData()
  }

  suspend fun endVisit(visitId: String) {
    openMRSHelper.endVisit(visitId)
    getPatientDetailData()
  }

  suspend fun visitEditState(visitId: String): VisitEditState? =
    try {
      openMRSHelper.visitEditState(visitId)
    } catch (e: Exception) {
      null
    }

  /** @return what stopped the change, or `null` when it was saved. */
  suspend fun saveVisitPeriod(visitId: String, start: Date, end: Date?): VisitPeriodError? {
    val problem = openMRSHelper.updateVisitPeriod(visitId, start, end)
    if (problem == null) getPatientDetailData()
    return problem
  }

  /** @return false when another visit is open, which has to be ended first. */
  suspend fun reopenVisit(visitId: String): Boolean {
    val reopened = openMRSHelper.reopenVisit(visitId)
    if (reopened) getPatientDetailData()
    return reopened
  }

  /**
   * The patient's page when the user manages the visits: each visit, newest first, with its
   * encounters under it, then the encounters that are in no visit.
   */
  private suspend fun MutableList<PatientDetailData>.addVisitsWithTheirEncounters(
    patientVisits: PatientVisits,
    encounterTypeCodeToQuestionnaireMap: Map<String, FormSummary>,
  ) {
    val hasOpenVisit = patientVisits.visits.keys.any { it.isOpenVisit() }
    add(
      PatientDetailHeader(
        header = getString(R.string.header_visits),
        count = patientVisits.visits.size,
        startVisit = !hasOpenVisit,
      ),
    )
    for ((visit, encounters) in patientVisits.visits) {
      val isOpen = visit.isOpenVisit()
      add(
        PatientDetailVisit(
          visit = createVisitItem(visit, encounterTypeCodeToQuestionnaireMap),
          isActive = isOpen,
          canReopen = !isOpen && !hasOpenVisit,
          isSynced = fhirEngine.getLocalChanges(ResourceType.Encounter, visit.logicalId).isEmpty(),
        ),
      )
      encounters.forEach { encounter ->
        add(
          PatientDetailEncounter(
            createEncounterItem(
              encounter,
              isEncounterSynced(encounter),
              encounterTypeCodeToQuestionnaireMap,
            ),
            indented = true,
          ),
        )
      }
    }
    if (patientVisits.withoutVisit.isNotEmpty()) {
      add(
        PatientDetailHeader(
          header = getString(R.string.header_encounters_without_visit),
          count = patientVisits.withoutVisit.size,
        ),
      )
      patientVisits.withoutVisit.forEach { encounter ->
        add(
          PatientDetailEncounter(
            createEncounterItem(
              encounter,
              isEncounterSynced(encounter),
              encounterTypeCodeToQuestionnaireMap,
            ),
          ),
        )
      }
    }
  }

  private fun MutableList<PatientDetailData>.addEncounterData(
    encounter: Encounter,
    isSynced: Boolean,
    encounterTypeCodeToQuestionnaireMap: Map<String, FormSummary>,
  ) {
    add(
      PatientDetailEncounter(
        createEncounterItem(encounter, isSynced, encounterTypeCodeToQuestionnaireMap),
      ),
    )
  }

  private fun createVisitItem(
    visit: Encounter,
    encounterTypeCodeToQuestionnaireMap: Map<String, FormSummary>,
  ): PatientListViewModel.VisitItem {
    val visitType = visit.getLocalizedEncounterType(encounterTypeCodeToQuestionnaireMap)
    val startDate = visit.period?.start?.let { Constants.DATE24_FORMATTER.format(it) } ?: ""
    val endDate = visit.period?.end?.let { Constants.DATE24_FORMATTER.format(it) } ?: ""

    return PatientListViewModel.VisitItem(
      visit.logicalId,
      visitType,
      startDate,
      endDate,
    )
  }

  private fun createEncounterItem(
    encounter: Encounter,
    isSynced: Boolean,
    encounterTypeCodeToQuestionnaireMap: Map<String, FormSummary>,
  ): PatientListViewModel.EncounterItem {
    val visitType = encounter.getLocalizedEncounterType(encounterTypeCodeToQuestionnaireMap)
    val visitDate = encounter.period?.start?.toLocalString() ?: "Date"

    return PatientListViewModel.EncounterItem(
      encounter.logicalId,
      visitType,
      visitDate,
      encounter.type.getOrNull(0)?.id,
      encounter.type.getOrNull(0)?.text,
      isSynced,
      encounter.type.getOrNull(0)?.coding?.getOrNull(0)?.code,
    )
  }

  /** The form for each encounter type code (the first one wins). */
  private suspend fun mapEncounterTypeCodesToQuestionnaires(): Map<String, FormSummary> {
    val summaries = fhirEngine.allFormSummaries(applicationContext)
    val encounterTypeCodeToQuestionnaireMap = mutableMapOf<String, FormSummary>()
    summaries.forEach { summary ->
      summary.encounterTypeCodes.forEach {
        encounterTypeCodeToQuestionnaireMap.putIfAbsent(it, summary)
      }
    }
    return encounterTypeCodeToQuestionnaireMap
  }

  private fun Encounter.getLocalizedEncounterType(
    encounterTypeCodeToQuestionnaireMap: Map<String, FormSummary>,
  ): String {
    val encounterType = this.type.firstOrNull()
    val encounterCode = encounterType?.coding?.firstOrNull()?.code
    val questionnaire = encounterCode?.let { encounterTypeCodeToQuestionnaireMap[it] }

    val localizedTitle = questionnaire?.titleIn(deviceLanguage)

    if (localizedTitle != null) {
      return localizedTitle
    }

    val fallback = encounterType?.coding?.firstOrNull()?.display ?: encounterType?.text ?: "Type"
    return fallback
  }

  private fun getString(resId: Int) = applicationContext.resources.getString(resId)

  private companion object {
    const val REFRESH_TIMEOUT_MILLIS = 30_000L
  }
}

interface PatientDetailData {
  val firstInGroup: Boolean
  val lastInGroup: Boolean
}

data class PatientDetailHeader(
  val header: String,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
  /** The number next to the title; null: the number of encounters. */
  val count: Int? = null,
  /** The header offers to open a new visit (the user manages the visits and none is open). */
  val startVisit: Boolean = false,
) : PatientDetailData

data class PatientDetailProperty(
  val patientProperty: PatientProperty,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailOverview(
  val patient: PatientListViewModel.PatientItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
  /** Everything else the record holds, shown on request ("Show all details"). */
  val extraDetails: List<PatientExtraDetail> = emptyList(),
  /** A placeholder for records to identify after: no registration data to show or edit. */
  val unidentified: Boolean = false,
) : PatientDetailData

/** The note of a patient to identify after, and the way to identify them. */
data class PatientDetailUnidentified(
  val note: String,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

/** One more fact about the patient: what it is, its value and what tapping it should do. */
data class PatientExtraDetail(val label: String, val value: String, val kind: Kind) {
  enum class Kind {
    PHONE,
    EMAIL,
    TEXT,
  }
}

data class PatientUnsynced(
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailObservation(
  val observation: PatientListViewModel.ObservationItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailCondition(
  val condition: PatientListViewModel.ConditionItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
) : PatientDetailData

data class PatientDetailEncounter(
  val encounter: PatientListViewModel.EncounterItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
  /** Shown under its visit. */
  val indented: Boolean = false,
) : PatientDetailData

data class PatientDetailVisit(
  val visit: PatientListViewModel.VisitItem,
  override val firstInGroup: Boolean = false,
  override val lastInGroup: Boolean = false,
  /** Open: it can be ended. */
  val isActive: Boolean = false,
  /** Ended, and no other visit is open: it can be opened again. */
  val canReopen: Boolean = false,
  val isSynced: Boolean = true,
) : PatientDetailData

data class PatientProperty(val header: String, val value: String, val isSynced: Boolean)

/**
 * The logical (unqualified) part of the ID. For example, if the ID is
 * "http://example.com/fhir/Patient/123/_history/456", then this value would be "123".
 */
private val Resource.logicalId: String
  get() {
    return this.idElement?.idPart.orEmpty()
  }
