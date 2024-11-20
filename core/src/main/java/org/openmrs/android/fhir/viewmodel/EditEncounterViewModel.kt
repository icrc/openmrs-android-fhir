package org.openmrs.android.fhir.viewmodel

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.*
import org.openmrs.android.fhir.di.ViewModelAssistedFactory
import org.openmrs.android.fhir.extensions.readFileFromAssets
import java.util.UUID
import java.util.Date

class EditEncounterViewModel @AssistedInject constructor(private val applicationContext: Context, private val fhirEngine: FhirEngine, @Assisted val state: SavedStateHandle) :
    ViewModel() {

    @AssistedFactory
    interface Factory : ViewModelAssistedFactory<EditEncounterViewModel> {
        override fun create(
            handle: SavedStateHandle
        ): EditEncounterViewModel
    }

    private val encounterId: String = requireNotNull(state["encounter_id"])
        ?: throw IllegalArgumentException("Encounter ID is required")
    private val formResource: String = requireNotNull(state["form_resource"])
        ?: throw IllegalArgumentException("Form resource is required")
    val liveEncounterData = liveData { emit(prepareEditEncounter()) }
    val isResourcesSaved = MutableLiveData<Boolean>()

    private lateinit var questionnaireResource: Questionnaire
    private lateinit var observations: List<Observation>
    private lateinit var contitions: List<Condition>
    private lateinit var patientReference: Reference

    private suspend fun prepareEditEncounter(): Pair<String, String> {
        val encounter = fhirEngine.get<Encounter>(encounterId)
        observations = getObservationsEncounterId(encounterId)
        contitions = getConditionsEncounterId(encounterId)
        patientReference = encounter.subject

        val questionnaireJson =
            applicationContext.readFileFromAssets(formResource).trimIndent()
        val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
        questionnaireResource =
            parser.parseResource(Questionnaire::class.java, questionnaireJson) as Questionnaire


        val observationBundle = Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            observations.forEach {
                addEntry().resource = it.apply {
                    id = "Observation/$id"
                }
            }
        }

        val launchContexts = mapOf("observations" to observationBundle)
        val questionnaireResponse = ResourceMapper.populate(questionnaireResource, launchContexts)
        val questionnaireResponseJson = parser.encodeResourceToString(questionnaireResponse)
        return questionnaireJson to questionnaireResponseJson
    }

    fun updateEncounter(questionnaireResponse: QuestionnaireResponse) {
        viewModelScope.launch {
            val bundle = ResourceMapper.extract(questionnaireResource, questionnaireResponse)
            saveResources(bundle)
            isResourcesSaved.value = true
        }
    }

    private suspend fun saveResources(bundle: Bundle) {
        val encounterReference = Reference("Encounter/$encounterId")
        val encounterSubject = fhirEngine.get<Encounter>(encounterId).subject

        bundle.entry.forEach {
            when (val resource = it.resource) {
                is Observation -> {
                    if (resource.hasCode()) {
                        handleObservation(resource, encounterReference, patientReference)
                    }
                }

                is Condition -> {
                    if (resource.hasCode()) {
                        handleCondition(resource, encounterReference, encounterSubject)
                    }
                }
            }
        }
    }

    private suspend fun handleObservation(
        resource: Observation,
        encounterReference: Reference,
        subjectReference: Reference
    ) {
        val existingObservation = observations.find { obs ->
            obs.code.coding.any { coding -> coding.code == resource.code.codingFirstRep.code }
        }

        if(existingObservation != null && existingObservation.value.equalsDeep(resource.value)) {
            return;
        }

        existingObservation?.apply {
            id = existingObservation.id
            status = Observation.ObservationStatus.AMENDED
            value = resource.value
        }

        if (existingObservation != null && existingObservation.hasValue()) {
            updateResourceToDatabase(existingObservation)
        } else {
            resource.apply {
                id = UUID.randomUUID().toString()
                subject = subjectReference
                encounter = encounterReference
                status = Observation.ObservationStatus.FINAL
                effective = DateTimeType(Date())
            }
            if(resource.hasValue()){
                createResourceToDatabase(resource)
            }
        }
    }

    private suspend fun handleCondition(
        resource: Condition,
        encounterReference: Reference,
        subjectReference: Reference
    ) {
        val existingCondition = contitions.find { cond ->
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
        val searchResult = fhirEngine.search<Observation> {
            filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" })
        }
        return searchResult.map { it.resource }
    }

    private suspend fun getConditionsEncounterId(encounterId: String): List<Condition> {
        val searchResult = fhirEngine.search<Condition> {
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
}

