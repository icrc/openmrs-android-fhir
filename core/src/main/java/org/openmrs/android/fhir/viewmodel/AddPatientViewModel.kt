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
import com.google.android.fhir.LocalChange
import com.google.android.fhir.datacapture.extensions.allItems
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.search
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.Constants.PATIENT_IDENTIFIER_DEFINITION_URL
import org.openmrs.android.fhir.Constants.PATIENT_LOCATION_IDENTIFIER_URL
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.data.PatientIdentifierFactory
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.RecentPatients
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.remote.ServerCapabilities
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.di.DefaultDispatcher
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets
import org.openmrs.android.fhir.extensions.pagedBySections
import org.openmrs.android.fhir.extensions.requireAnswersBeforeReview
import org.openmrs.android.fhir.patientlists.GroupMapper
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import timber.log.Timber

/** ViewModel for patient registration screen {@link AddPatientFragment}. */
class AddPatientViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
  private val database: AppDatabase,
  private val openMRSHelper: OpenMRSHelper,
  private val patientLists: PatientListsRepository,
  private val capabilities: ServerCapabilities,
  @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

  var locationId: String? = null
  private val identifierFactory by lazy { PatientIdentifierFactory(applicationContext, database) }
  val isPatientSaved = MutableLiveData<Boolean>()

  /**
   * The patient to identify after that this registration is for (empty: a normal registration):
   * once the patient is saved, what was recorded for it is linked to the new patient.
   */
  var identifyEntryId: String = ""

  var joinListId: String = ""

  var joinedListName: String? = null
    private set

  /** The patient just saved, and how many records of [identifyEntryId] were linked to them. */
  var savedPatientId: String? = null
  var linkedRecords: Int = 0

  /** Why the last save was refused, when it is more specific than missing inputs. */
  val saveFailure = MutableLiveData<Int?>()
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
      val form = questionnaire
      if (form == null) {
        saveInProgress = false
        isPatientSaved.value = false
        return@launch
      }
      if (
        QuestionnaireResponseValidator.validateQuestionnaireResponse(
            form,
            questionnaireResponse,
            applicationContext,
          )
          .values
          .flatten()
          .any { it is Invalid }
      ) {
        saveInProgress = false
        AuditLog.failure(AuditAction.PATIENT_CREATED, "reason" to "invalid_answers")
        isPatientSaved.value = false
        return@launch
      }

      val entry =
        ResourceMapper.extract(
            form,
            questionnaireResponse,
          )
          .entryFirstRep
      if (entry.resource !is Patient) {
        saveInProgress = false
        return@launch
      }

      val patient = entry.resource as Patient
      patient.id = generateUuid()

      val birthDate = openMRSHelper.birthDateFrom(questionnaireResponse)
      if (birthDate != null && openMRSHelper.isInTheFuture(birthDate)) {
        saveFailure.value = R.string.birth_date_in_the_future
        AuditLog.failure(AuditAction.PATIENT_CREATED, "reason" to "birth_date_in_future")
        saveInProgress = false
        isPatientSaved.value = false
        return@launch
      }
      patient.birthDateElement = birthDate
      // The extraction of the answers leaves the gender out of the paged form: set it from them.
      openMRSHelper.genderFrom(questionnaireResponse)?.let { patient.gender = it }

      val location = locationId?.let { fhirEngine.get(ResourceType.Location, it) } as Location?
      // Without a location the patient has no identifiers yet: they come with the location.
      if (location == null) GuestSession.markCompletionPending(applicationContext)
      if (location != null) {
        val identifiers =
          extractLocationIdentifiersFromQuestionnaireResponse(questionnaireResponse, location)
        identifiers.addAll(identifierFactory.locationIdentifiers(location, fetchIdentifiers))
        if (fetchIdentifiers) {
          identifiers.add(0, identifierFactory.unsyncedIdentifier(location))
        }
        patient.identifier = identifiers
      }

      val personAttributeExtensions =
        openMRSHelper.extractPersonAttributeFromQuestionnaireResponse(
          form,
          questionnaireResponse,
        )

      if (patient.hasExtension()) {
        personAttributeExtensions.toMutableList().addAll(0, patient.extension)
      }
      patient.extension = personAttributeExtensions

      fhirEngine.create(patient)
      AuditLog.record(
        AuditAction.PATIENT_CREATED,
        AuditOutcome.SUCCESS,
        "patient" to patient.idElement.idPart,
      )
      savedPatientId = patient.idElement.idPart
      if (identifyEntryId.isNotBlank()) {
        // Not found on the server: everything recorded for the patient to identify goes to this
        // one.
        linkedRecords =
          try {
            FhirApplication.appComponent(applicationContext)
              .unidentifiedPatients()
              .linkToNewPatient(identifyEntryId, patient.idElement.idPart)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Timber.e(e, "Could not link the records to the new patient")
            0
          }
        AuditLog.record(
          AuditAction.PATIENT_CREATED,
          AuditOutcome.SUCCESS,
          "linked_records" to linkedRecords,
          "from" to "identify_later",
        )
      }
      // Live mode sends it now; otherwise it waits for the sync.
      val uploaded = uploadWaiter()
      LiveSync.requestUpload(applicationContext)
      if (uploaded != null) {
        uploaded.await()
        savedPatientId = idAfterUpload(patient)
      }
      savedPatientId?.let { RecentPatients.record(applicationContext, it) }
      joinList()
      isPatientSaved.value = true
      saveInProgress = false
    }
  }

  /**
   * The registration was started from a patient list: the patient joins it. One not uploaded yet is
   * added for their id of now, and the add waits for the upload (the server gives them another).
   */
  private suspend fun joinList() {
    val listId = joinListId.takeIf { it.isNotBlank() } ?: return
    val id = savedPatientId ?: return
    if (!capabilities.current().changeMembers) return
    try {
      val saved = fhirEngine.get<Patient>(id)
      val name = patientLists.nameOf(listId) ?: return
      val unsent =
        fhirEngine.getLocalChanges(ResourceType.Patient, id).any {
          it.type == LocalChange.Type.INSERT
        }
      patientLists.addMembers(
        listId,
        listOf(GroupMapper.summary(saved)),
        heldForUpload = if (unsent) setOf(id) else emptySet(),
      )
      joinedListName = name
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Could not add the new patient to the patient list")
    }
  }

  /** Live mode with a network: waits for the upload that is about to start. */
  private suspend fun uploadWaiter(): Deferred<Unit?>? {
    if (!LiveSync.isAvailable(applicationContext)) return null
    // Collecting starts before the upload is requested, so its end cannot be missed.
    return viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
      withTimeoutOrNull(UPLOAD_WAIT_MILLIS) { LiveSync.uploaded.first() }
    }
  }

  /**
   * The server gives a new patient its own id and the device's copy takes it once uploaded: the
   * patient's page has to open under that one. Without a sign that the upload went through, the id
   * the patient was created with is kept.
   */
  private suspend fun idAfterUpload(created: Patient): String {
    val createdId = created.idElement.idPart
    try {
      fhirEngine.get<Patient>(createdId)
      return createdId
    } catch (e: ResourceNotFoundException) {
      // Replaced by the server's id: found again by what was entered.
    }
    val family = created.nameFirstRep.family
    if (family.isNullOrBlank()) return createdId
    return fhirEngine
      .search<Patient> {
        filter(
          Patient.FAMILY,
          {
            modifier = StringFilterModifier.MATCHES_EXACTLY
            value = family
          },
        )
      }
      .map { it.resource }
      .filter { it.idElement.idPart != createdId && sameEntry(it, created) }
      .maxByOrNull { it.meta.lastUpdated?.time ?: 0L }
      ?.idElement
      ?.idPart
      ?: createdId
  }

  private fun sameEntry(a: Patient, b: Patient): Boolean =
    a.nameFirstRep.givenAsSingleString.equals(b.nameFirstRep.givenAsSingleString, true) &&
      a.birthDateElement.valueAsString == b.birthDateElement.valueAsString &&
      a.gender == b.gender

  private fun extractLocationIdentifiersFromQuestionnaireResponse(
    questionnaireResponse: QuestionnaireResponse,
    location: Location,
  ): MutableList<Identifier> {
    // Anywhere in the form: they are on its first page, inside the page's group.
    val identifierItems =
      questionnaireResponse.allItems.filter { it.linkId.contains("identifier-") }

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
      // Loading the form and writing it as JSON is heavy for the registration form: not on main.
      val json =
        withContext(defaultDispatcher) {
          val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
          questionnaire =
            fhirEngine.getQuestionnaireOrFromAssets(questionnaireName, applicationContext, parser)
          questionnaire?.let {
            // "Review" waits for the required answers, as in the encounter forms.
            it.requireAnswersBeforeReview()
            val withIdentifiers = embeddIdentifierInQuestionnaire(it)
            // One page per section; the identifiers go on the first.
            withIdentifiers.pagedBySections()
            parser.encodeResourceToString(withIdentifiers)
          }
            ?: ""
        }
      embeddedQuestionnaire.value = json
    }
  }

  /** Adds an input for every selected identifier type the server does not generate itself. */
  private suspend fun embeddIdentifierInQuestionnaire(questionnaire: Questionnaire): Questionnaire {
    val selectedIdentifiers =
      applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]
        ?: return questionnaire
    selectedIdentifiers
      .mapNotNull { database.dao().getIdentifierTypeById(it) }
      .filter { it.isAutoGenerated == false }
      .forEach { identifierType ->
        questionnaire.item.add(
          0,
          QuestionnaireItemComponent().apply {
            linkId = "identifier-${identifierType.uuid}"
            type = Questionnaire.QuestionnaireItemType.STRING
            definition = PATIENT_IDENTIFIER_DEFINITION_URL
            text = identifierType.display
            required = identifierType.required
          },
        )
      }
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

  private companion object {
    const val UPLOAD_WAIT_MILLIS = 20_000L
  }
}
