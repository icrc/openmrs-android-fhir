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
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
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
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets

class GroupFormEntryViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
) : ViewModel() {
  private val _patients = MutableLiveData<List<PatientListViewModel.PatientItem>>()
  val patients: LiveData<List<PatientListViewModel.PatientItem>> = _patients

  private val patientIdToEncounterIdMap = mutableMapOf<String, String>()
  val patientResponses = mutableMapOf<String, String>()
  val isLoading = MutableLiveData<Boolean>()
  var submittedSet = mutableSetOf<Int>()
  val screenerQuestionnaireJson = MutableLiveData<String>()
  val encounterQuestionnaireJson = MutableLiveData<String>()
  private var encounterQuestionnaire: Questionnaire? = null
  private var screenerResponse: QuestionnaireResponse? = null
  private var screenerQuestionnaire: Questionnaire? = null
  private val screenerEncounterLinkIds = mutableSetOf<String>()
  val sessionId: String = UUID.randomUUID().toString()
  var sessionDate: Date? = null
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

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
      this@GroupFormEntryViewModel.encounterQuestionnaire = encounterQuestionnaire
      val screener =
        fhirEngine.getQuestionnaireOrFromAssets(
          "screener-questionnaire-template",
          applicationContext,
          parser,
        )
      screenerQuestionnaire = screener
      screener?.let { s ->
        val extras = mutableListOf<Questionnaire.QuestionnaireItemComponent>()

        fun collect(items: List<Questionnaire.QuestionnaireItemComponent>?) {
          items?.forEach { item ->
            if (item.extension.any { ext -> ext.url == Constants.SHOW_SCREENER_EXTENSION_URL }) {
              screenerEncounterLinkIds.add(item.linkId)
              extras.add(item.copy())
            }
            collect(item.item)
          }
        }

        collect(encounterQuestionnaire.item)
        extras.forEach { s.addItem(it) }
        screenerQuestionnaireJson.value = parser.encodeResourceToString(s)
      }
    }
  }

  fun saveScreenerObservations(patientId: String, encounterId: String) {
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

    viewModelScope.launch {
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

  fun plugAnswersToEncounter(response: QuestionnaireResponse) {
    screenerResponse = response
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
      fun plug(items: List<Questionnaire.QuestionnaireItemComponent>?) {
        items?.forEach { item ->
          if (screenerEncounterLinkIds.contains(item.linkId)) {
            item.initial.clear()
            answers[item.linkId]?.forEach { ans ->
              item.addInitial(
                Questionnaire.QuestionnaireItemInitialComponent().setValue(ans.value),
              )
            }
          }
          plug(item.item)
        }
      }
      plug(questionnaire.item)
      encounterQuestionnaireJson.value = parser.encodeResourceToString(questionnaire)
    }
  }

  fun createInternalObservations(patientId: String, encounterId: String) {
    viewModelScope.launch {
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
          ) // Cohort Identifier
          createObservation(
            patientId,
            encounterId,
            Constants.COHORT_NAME_UUID,
            StringType(cohortName),
            "Cohort Name",
          ) // Cohort Name
        }
      } finally {
        createObservation(
          patientId,
          encounterId,
          Constants.SESSION_IDENTIFIER_UUID,
          StringType(sessionId),
          "Session Identifier",
        ) // Session Identifier
      }
    }
  }

  fun createObservation(
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
        effective = DateTimeType(java.util.Date())
        value = observationValue
      }
    viewModelScope.launch {
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
  }

  fun getPatientIdToEncounterIdMap(): Map<String, String> {
    return patientIdToEncounterIdMap.toMap()
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
}
