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
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.UUID
import kotlin.collections.forEach
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.StringType
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.getJsonFileNames
import org.openmrs.android.fhir.extensions.readFileFromAssets
import timber.log.Timber

class EditEncounterViewModel
@AssistedInject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  @Assisted val state: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  interface Factory : ViewModelAssistedFactory<EditEncounterViewModel> {
    override fun create(
      handle: SavedStateHandle,
    ): EditEncounterViewModel
  }

  private val _encounterDataPair = MutableLiveData<Pair<String, String>>()
  val encounterDataPair: LiveData<Pair<String, String>> = _encounterDataPair

  val isResourcesSaved = MutableLiveData<String>()
  val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  fun prepareEditEncounter(encounterId: String, encounterType: String) {
    viewModelScope.launch {
      val observations = getObservationsEncounterId(encounterId)
      try {
        var questionnaire =
          fhirEngine
            .search<Questionnaire> {}
            .firstOrNull { questionnaire ->
              questionnaire.resource.code.any { it.code == encounterType }
            }
            ?.resource
        if (questionnaire == null) {
          // Look into assets folder
          val assetQuestionnaireFileNames = applicationContext.getJsonFileNames()
          assetQuestionnaireFileNames.forEach {
            val questionnaireString = applicationContext.readFileFromAssets(it)
            if (questionnaireString.isNotEmpty()) {
              val assetsQuestionnaire =
                parser.parseResource(Questionnaire::class.java, questionnaireString)
              if (assetsQuestionnaire.hasCode()) {
                assetsQuestionnaire.code.forEach {
                  if (
                    it.hasSystem() and
                      (it?.system.toString() ==
                        "http://fhir.openmrs.org/code-system/encounter-type") and
                      it.hasCode() and
                      (it.code == encounterType)
                  ) {
                    questionnaire = assetsQuestionnaire
                    return@forEach
                  }
                }
              }
            }
          }
        }

        val questionnaireJson = parser.encodeResourceToString(questionnaire)

        val observationBundle =
          Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            observations.forEach { addEntry().resource = it.apply { id = "Observation/$id" } }
          }

        val launchContexts = mapOf("observations" to observationBundle)
        val questionnaireResponse =
          ResourceMapper.populate(
            questionnaire!!,
            launchContexts,
          ) // if questionnaire is null it'll throw exception while encoding to string.
        val questionnaireResponseJson = parser.encodeResourceToString(questionnaireResponse)
        _encounterDataPair.value = questionnaireJson to questionnaireResponseJson
      } catch (e: Exception) {
        Timber.e(e.localizedMessage)
        _encounterDataPair.value = Pair("", "")
      }
    }
  }

  fun updateEncounter(
    questionnaireResponse: QuestionnaireResponse,
    encounterId: String,
    encounterType: String,
  ) {
    viewModelScope.launch {
      var questionnaire =
        fhirEngine
          .search<Questionnaire> {}
          .firstOrNull { questionnaire ->
            questionnaire.resource.code.any { it.code == encounterType }
          }
          ?.resource
      if (questionnaire == null) {
        // Look into assets folder
        val assetQuestionnaireFileNames = applicationContext.getJsonFileNames()
        assetQuestionnaireFileNames.forEach {
          val questionnaireString = applicationContext.readFileFromAssets(it)
          if (questionnaireString.isNotEmpty()) {
            val assetsQuestionnaire =
              parser.parseResource(Questionnaire::class.java, questionnaireString)
            if (assetsQuestionnaire.hasCode()) {
              assetsQuestionnaire.code.forEach {
                if (
                  it.hasSystem() and
                    (it?.system.toString() ==
                      "http://fhir.openmrs.org/code-system/encounter-type") and
                    it.hasCode() and
                    (it.code == encounterType)
                ) {
                  questionnaire = assetsQuestionnaire
                  return@forEach
                }
              }
            }
          }
        }
      }

      if (questionnaire == null) {
        isResourcesSaved.value = "ERROR"
        return@launch
      }

      val bundle = ResourceMapper.extract(questionnaire, questionnaireResponse)

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
        isResourcesSaved.value = "MISSING"
        return@launch
      }

      saveResources(encounterId, bundle)
    }
  }

  private suspend fun saveResources(encounterId: String, bundle: Bundle) {
    try {
      val encounterReference = Reference("Encounter/$encounterId")
      val encounterSubject = fhirEngine.get<Encounter>(encounterId).subject
      val observations = getObservationsEncounterId(encounterId)
      val conditions = getConditionsEncounterId(encounterId)

      bundle.entry.forEach {
        when (val resource = it.resource) {
          is Observation -> {
            if (resource.hasCode()) {
              handleObservation(resource, encounterReference, encounterSubject, observations)
            }
          }
          is Condition -> {
            if (resource.hasCode()) {
              handleCondition(resource, encounterReference, encounterSubject, conditions)
            }
          }
        }
      }
      isResourcesSaved.value = "SAVED"
    } catch (e: Exception) {
      Timber.e(e.localizedMessage)
      isResourcesSaved.value = "MISSING"
    }
  }

  private suspend fun handleObservation(
    resource: Observation,
    encounterReference: Reference,
    subjectReference: Reference,
    observations: List<Observation>,
  ) {
    if (!resource.hasCode() || !resource.hasValue()) return

    val matching =
      observations.filter { obs ->
        obs.code.coding.any { c -> c.code == resource.code.codingFirstRep.code }
      }

    when (val value = resource.value) {
      is StringType,
      is Quantity, -> {
        val existing = matching.firstOrNull()
        if (existing != null) {
          if (existing.value.equalsDeep(value)) return // same value → do nothing

          // amend
          existing.status = Observation.ObservationStatus.AMENDED
          existing.value = value
          existing.effective = DateTimeType(Date())
          updateResourceToDatabase(existing)
        } else {
          // new resource
          resource.id = generateUuid()
          resource.subject = subjectReference
          resource.encounter = encounterReference
          resource.status = Observation.ObservationStatus.FINAL
          resource.effective = DateTimeType(Date())
          createResourceToDatabase(resource)
        }
      }
      is CodeableConcept -> {
        val codings = value.coding

        if (codings.size <= 1) {
          val existing = matching.firstOrNull()
          if (existing != null && existing.value.equalsDeep(value)) return

          // amend or create
          val target = existing ?: Observation()
          target.id = existing?.id ?: generateUuid()
          target.code = resource.code
          target.subject = subjectReference
          target.encounter = encounterReference
          target.status =
            if (existing != null) {
              Observation.ObservationStatus.AMENDED
            } else {
              Observation.ObservationStatus.FINAL
            }
          target.effective = DateTimeType(Date())
          target.value = value

          if (existing != null) {
            updateResourceToDatabase(target)
          } else {
            createResourceToDatabase(target)
          }
        } else {
          // purge all existing and create fresh one-per-coding
          matching.forEach { fhirEngine.purge(it.resourceType, it.idElement.idPart) }

          codings.forEach { coding ->
            val obs =
              Observation().apply {
                id = generateUuid()
                code = resource.code
                subject = subjectReference
                encounter = encounterReference
                status = Observation.ObservationStatus.FINAL
                effective = DateTimeType(Date())
              }
            obs.setValue(CodeableConcept().apply { addCoding(coding) })
            createResourceToDatabase(obs)
          }
        }
      }
      else -> {}
    }
  }

  private suspend fun handleCondition(
    resource: Condition,
    encounterReference: Reference,
    subjectReference: Reference,
    conditions: List<Condition>,
  ) {
    val existingCondition =
      conditions.find { cond ->
        cond.code.coding.any { coding -> coding.code == resource.code.codingFirstRep.code }
      }
    if (existingCondition != null) {
      resource.id = existingCondition.id
    } else {
      resource.id = UUID.randomUUID().toString()
    }
    resource.subject = subjectReference
    resource.encounter = encounterReference

    updateResourceToDatabase(resource)
  }

  private suspend fun getObservationsEncounterId(encounterId: String): List<Observation> {
    val searchResult =
      fhirEngine.search<Observation> {
        filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" })
      }
    return searchResult.map { it.resource }
  }

  private suspend fun getConditionsEncounterId(encounterId: String): List<Condition> {
    val searchResult =
      fhirEngine.search<Condition> {
        filter(Condition.ENCOUNTER, { value = "Encounter/$encounterId" })
      }
    return searchResult.map { it.resource }
  }

  private suspend fun updateResourceToDatabase(resource: Resource) {
    fhirEngine.update(resource)
  }

  private suspend fun createResourceToDatabase(resource: Resource) {
    fhirEngine.create(resource)
  }

  private fun generateUuid(): String {
    return UUID.randomUUID().toString()
  }
}
