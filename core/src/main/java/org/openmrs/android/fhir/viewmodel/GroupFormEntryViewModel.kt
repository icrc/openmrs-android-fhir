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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.allItems
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.google.android.fhir.delete
import com.google.android.fhir.get
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.search
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.GroupSessionDraftRepository
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.convertDateAnswersToUtcDateTime
import org.openmrs.android.fhir.extensions.convertDateTimeAnswersToDate
import org.openmrs.android.fhir.extensions.ensurePageGroupsHaveTrailingSpacer
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets
import org.openmrs.android.fhir.extensions.nowLocalDateTime
import org.openmrs.android.fhir.extensions.nowUtcDateTime

class GroupFormEntryViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val groupSessionDraftRepository: GroupSessionDraftRepository,
  @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<GroupFormEntryViewModel> {
    override fun create(handle: SavedStateHandle): GroupFormEntryViewModel
  }

  private val _patients = MutableLiveData<List<PatientListViewModel.PatientItem>>()
  val patients: LiveData<List<PatientListViewModel.PatientItem>> = _patients

  private val patientIdToEncounterIdMap = mutableMapOf<String, String>()
  val patientResponses = mutableMapOf<String, String>()
  val isLoading = MutableLiveData<Boolean>()
  var submittedPatientIds = mutableSetOf<String>()
  val screenerQuestionnaireJson = MutableLiveData<String>()
  val encounterQuestionnaireJson = MutableLiveData<String>()
  private var encounterQuestionnaire: Questionnaire? = null
  private var screenerResponse: QuestionnaireResponse? = null
  private var screenerQuestionnaire: Questionnaire? = null
  private val screenerLinkIds = mutableSetOf<String>()
  private val screenerEncounterLinkIds = mutableSetOf<String>()
  var sessionId: String = UUID.randomUUID().toString()
  var isScreenerCompleted = false
  var sessionDate: Date? = null
  var screenerResponseJson: String? = null
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  var hasActiveSession: Boolean
    get() = savedStateHandle[ACTIVE_SESSION_KEY] ?: false
    private set(value) {
      savedStateHandle[ACTIVE_SESSION_KEY] = value
    }

  fun getPatients(patientIds: Set<String>) {
    isLoading.value = true
    viewModelScope.launch {
      _patients.value =
        fhirEngine
          .search<Patient> {}
          .filter { patientIds.contains(it.resource.idElement.idPart) }
          .mapIndexed { index, fhirPatient -> fhirPatient.resource.toPatientItem(index + 1) }
      isLoading.value = false
    }
  }

  fun getPatientName(patientId: String): String {
    val patient = _patients.value?.find { it.resourceId == patientId }
    return patient?.name ?: ""
  }

  fun setPatientIdToEncounterIdMap(patientId: String, encounterId: String) {
    patientIdToEncounterIdMap[patientId] = encounterId
  }

  fun prepareScreenerQuestionnaire(encounterQuestionnaire: Questionnaire) {
    viewModelScope.launch {
      encounterQuestionnaire.ensurePageGroupsHaveTrailingSpacer()
      this@GroupFormEntryViewModel.encounterQuestionnaire = encounterQuestionnaire
      val screener =
        fhirEngine.getQuestionnaireOrFromAssets(
          "screener-questionnaire-template",
          applicationContext,
          parser,
        )
      screenerQuestionnaire = screener
      plugCurrentDateTimeToSessionDate(screener)
      screenerLinkIds.clear()
      screenerEncounterLinkIds.clear()
      screener?.let { s ->
        val extras = mutableListOf<Questionnaire.QuestionnaireItemComponent>()

        fun collectTemplateLinkIds(items: List<Questionnaire.QuestionnaireItemComponent>?) {
          items?.forEach { item ->
            screenerLinkIds.add(item.linkId)
            collectTemplateLinkIds(item.item)
          }
        }

        fun collectEncounterExtras(items: List<Questionnaire.QuestionnaireItemComponent>?) {
          items?.forEach { item ->
            if (item.extension.any { ext -> ext.url == Constants.SHOW_SCREENER_EXTENSION_URL }) {
              if (screenerLinkIds.add(item.linkId)) {
                extras.add(item.copy())
              }
              screenerEncounterLinkIds.add(item.linkId)
            }
            collectEncounterExtras(item.item)
          }
        }

        collectTemplateLinkIds(s.item)
        collectEncounterExtras(encounterQuestionnaire.item)
        extras.forEach { s.addItem(it) }
        screenerQuestionnaireJson.value = parser.encodeResourceToString(s)
      }
    }
  }

  fun plugCurrentDateTimeToSessionDate(questionnaire: Questionnaire?) {
    questionnaire?.item?.forEach {
      if (it.hasText() and (it.text == "Session Date")) {
        it.initial.clear()
        it.addInitial(
          Questionnaire.QuestionnaireItemInitialComponent().setValue(nowLocalDateTime()),
        )
      }
    }
  }

  fun markSessionActive() {
    hasActiveSession = true
  }

  suspend fun saveScreenerObservations(patientId: String, encounterId: String) {
    val response = screenerResponse ?: return
    val questionnaire = screenerQuestionnaire ?: return
    val answerMap =
      mutableMapOf<String, List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent>>()

    fun mapAnswers(items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>?) {
      items?.forEach {
        if (it.answer.isNotEmpty()) {
          answerMap[it.linkId] = it.answer
        }
        mapAnswers(it.item)
      }
    }

    mapAnswers(response.item)

    val itemMap = mutableMapOf<String, Questionnaire.QuestionnaireItemComponent>()

    fun mapItems(items: List<Questionnaire.QuestionnaireItemComponent>?) {
      items?.forEach {
        itemMap[it.linkId] = it
        mapItems(it.item)
      }
    }

    mapItems(questionnaire.item)

    answerMap.forEach { (linkId, answers) ->
      if (!screenerEncounterLinkIds.contains(linkId)) {
        val qItem = itemMap[linkId]
        val code: String =
          if (qItem?.code?.isNotEmpty() == true) {
            qItem.codeFirstRep.code ?: ""
          } else {
            linkId
          }
        answers.forEach { ans ->
          createObservation(patientId, encounterId, code.toString(), ans.value, qItem?.text)
        }
      }
    }
  }

  fun setSessionDate(response: QuestionnaireResponse) {
    val linkId =
      screenerQuestionnaire
        ?.item
        ?.find { it.codeFirstRep.code == Constants.SESSION_DATE_UUID }
        ?.linkId

    sessionDate =
      response.allItems
        .firstOrNull { it.linkId == linkId }
        ?.answer
        ?.firstOrNull()
        ?.valueDateTimeType
        ?.value
  }

  fun cacheScreenerDraft(response: QuestionnaireResponse) {
    screenerResponse = response
    screenerResponseJson = parser.encodeResourceToString(response)
  }

  fun plugAnswersToEncounter(response: QuestionnaireResponse) {
    screenerResponse = response
    screenerResponseJson = parser.encodeResourceToString(response)
    isScreenerCompleted = true
    val answers =
      mutableMapOf<String, List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent>>()

    fun collect(items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>?) {
      items?.forEach {
        if (it.answer.isNotEmpty()) {
          answers[it.linkId] = it.answer
        }
        collect(it.item)
      }
    }

    collect(response.item)

    encounterQuestionnaire?.let { questionnaire ->
      fun convertAnswerValue(
        item: Questionnaire.QuestionnaireItemComponent,
        answer: QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent,
      ): Type {
        val answerCopy = answer.copy()
        if (
          item.type == Questionnaire.QuestionnaireItemType.DATE && answerCopy.value is DateTimeType
        ) {
          val conversionResponse =
            QuestionnaireResponse().apply {
              addItem(
                QuestionnaireResponse.QuestionnaireResponseItemComponent().apply {
                  linkId = item.linkId
                  addAnswer(answerCopy)
                },
              )
            }
          convertDateTimeAnswersToDate(conversionResponse)
          return conversionResponse.itemFirstRep.answerFirstRep.value
        }
        if (
          item.type == Questionnaire.QuestionnaireItemType.DATETIME && answerCopy.value is DateType
        ) {
          val conversionResponse =
            QuestionnaireResponse().apply {
              addItem(
                QuestionnaireResponse.QuestionnaireResponseItemComponent().apply {
                  linkId = item.linkId
                  addAnswer(answerCopy)
                },
              )
            }
          convertDateAnswersToUtcDateTime(conversionResponse)
          return conversionResponse.itemFirstRep.answerFirstRep.value
        }
        return answerCopy.value
      }

      fun plug(items: List<Questionnaire.QuestionnaireItemComponent>?) {
        items?.forEach { item ->
          answers[item.linkId]?.let { linkAnswers ->
            item.initial.clear()
            linkAnswers.forEach { ans ->
              item.addInitial(
                Questionnaire.QuestionnaireItemInitialComponent()
                  .setValue(
                    convertAnswerValue(item, ans),
                  ),
              )
            }
          }
          plug(item.item)
        }
      }
      plug(questionnaire.item)
      questionnaire.ensurePageGroupsHaveTrailingSpacer()
      encounterQuestionnaireJson.value = parser.encodeResourceToString(questionnaire)
    }
  }

  suspend fun createInternalObservations(
    patientId: String,
    encounterId: String,
    sessionId: String,
  ) {
    val selectedPatientListId =
      applicationContext.dataStore.data
        .first()[PreferenceKeys.SELECTED_PATIENT_LISTS]
        ?.firstOrNull()
    try {
      if (selectedPatientListId != null) {
        val cohortName = fhirEngine.get<Group>(selectedPatientListId).name
        createObservation(
          patientId,
          encounterId,
          Constants.COHORT_IDENTIFIER_UUID,
          StringType(selectedPatientListId),
          "Cohort Identifier",
        )
        createObservation(
          patientId,
          encounterId,
          Constants.COHORT_NAME_UUID,
          StringType(cohortName),
          "Cohort Name",
        )
      }
    } finally {
      createObservation(
        patientId,
        encounterId,
        Constants.SESSION_IDENTIFIER_UUID,
        StringType(sessionId),
        "Session Identifier",
      )
    }
  }

  suspend fun createObservation(
    patientId: String,
    encounterId: String,
    observationCode: String,
    observationValue: Type,
    display: String? = "",
  ) {
    val encounterRef = Reference("Encounter/$encounterId")
    val patientRef = Reference("Patient/$patientId")
    val obs =
      Observation().apply {
        id = generateUuid()
        status = Observation.ObservationStatus.FINAL
        code =
          CodeableConcept()
            .addCoding(
              Coding().apply { code = observationCode }.setDisplay(display),
            )
            .setText(display)
        subject = patientRef
        encounter = encounterRef
        effective = nowUtcDateTime()
        value = observationValue
      }
    // Delete observation if exists then create new observation.
    fhirEngine.withTransaction {
      fhirEngine
        .search<Observation> {
          filter(Observation.SUBJECT, { value = "Patient/$patientId" })
          filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" })
          filter(Observation.CODE, { value = of(CodeType(observationCode)) })
          operation = Operation.AND
        }
        .firstOrNull()
        ?.let { fhirEngine.delete<Observation>(it.resource.id) }
      fhirEngine.create(obs)
    }
  }

  fun getEncounterIdForPatientId(patientId: String): String? {
    return patientIdToEncounterIdMap[patientId]
  }

  suspend fun isValidQuestionnaireResponse(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    applicationContext: Context,
  ): Boolean {
    return !QuestionnaireResponseValidator.validateQuestionnaireResponse(
        questionnaire,
        questionnaireResponse,
        applicationContext,
      )
      .values
      .flatten()
      .any { it is Invalid }
  }

  suspend fun saveDraft(questionnaireId: String, patientIds: List<String>) {
    val draft =
      GroupSessionDraft(
        questionnaireId = questionnaireId,
        sessionId = sessionId,
        patientIds = patientIds,
        patientResponses = patientResponses.toMap(),
        screenerResponse = screenerResponseJson,
        encounterQuestionnaireJson = encounterQuestionnaireJson.value,
        screenerQuestionnaireJson = screenerQuestionnaireJson.value,
        sessionDate = sessionDate?.time,
        screenerCompleted = isScreenerCompleted,
      )
    withContext(Dispatchers.IO) { groupSessionDraftRepository.upsertDraft(draft) }
  }

  suspend fun loadDraft(questionnaireId: String): GroupSessionDraft? {
    return withContext(Dispatchers.IO) { groupSessionDraftRepository.getDraft(questionnaireId) }
  }

  suspend fun deleteDraft(questionnaireId: String) {
    withContext(Dispatchers.IO) { groupSessionDraftRepository.deleteDraft(questionnaireId) }
  }

  fun restoreDraft(draft: GroupSessionDraft) {
    sessionId = draft.sessionId
    isScreenerCompleted = draft.screenerCompleted
    sessionDate = draft.sessionDate?.let { Date(it) }
    patientResponses.clear()
    patientResponses.putAll(draft.patientResponses)
    screenerResponseJson = draft.screenerResponse
    screenerResponse =
      draft.screenerResponse?.let {
        parser.parseResource(QuestionnaireResponse::class.java, it) as QuestionnaireResponse
      }
    if (!draft.encounterQuestionnaireJson.isNullOrBlank()) {
      encounterQuestionnaire =
        parser.parseResource(Questionnaire::class.java, draft.encounterQuestionnaireJson)
          as Questionnaire
      encounterQuestionnaire?.ensurePageGroupsHaveTrailingSpacer()
      encounterQuestionnaireJson.value =
        encounterQuestionnaire?.let { parser.encodeResourceToString(it) }
          ?: draft.encounterQuestionnaireJson
    }
    if (!draft.screenerQuestionnaireJson.isNullOrBlank()) {
      screenerQuestionnaireJson.value = draft.screenerQuestionnaireJson
    }
    if (draft.screenerCompleted) {
      screenerResponse?.let { plugAnswersToEncounter(it) }
    }
    markSessionActive()
  }

  fun clearSessionState() {
    patientResponses.clear()
    patientIdToEncounterIdMap.clear()
    submittedPatientIds.clear()
    screenerResponse = null
    screenerResponseJson = null
    encounterQuestionnaire = null
    screenerQuestionnaire = null
    sessionDate = null
    isScreenerCompleted = false
    sessionId = UUID.randomUUID().toString()
    clearActiveSession()
  }

  fun clearActiveSession() {
    savedStateHandle[ACTIVE_SESSION_KEY] = false
  }

  fun hasDraftData(): Boolean {
    return patientResponses.isNotEmpty() || screenerResponse != null || isScreenerCompleted
  }

  companion object {
    private const val ACTIVE_SESSION_KEY = "active_session"
  }
}
