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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.count
import com.google.android.fhir.search.filter.ReferenceParamFilterCriterion
import com.google.android.fhir.search.search
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.RiskAssessment
import org.openmrs.android.fhir.Constants.VISIT_TYPE_CODE_SYSTEM
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.FormCatalog
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.sync.LivePatients
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.multiuser.DataVisibility
import org.openmrs.android.fhir.unidentified.UnidentifiedStore
import timber.log.Timber

/**
 * The ViewModel helper class for PatientItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
data class PatientListUiState(
  val query: String = "",
  val patients: List<PatientListViewModel.PatientItem> = emptyList(),
  val isLoading: Boolean = false,
  val isRefreshing: Boolean = false,
  /** What the live search found on the server (live mode only). */
  val serverSearch: ServerSearchState = ServerSearchState(),
)

/**
 * The "On the server" part of the patient search. It is [active] while live mode is on and the
 * search text is long enough to look up.
 */
data class ServerSearchState(
  val active: Boolean = false,
  val loading: Boolean = false,
  val failed: Boolean = false,
  val results: List<PatientListViewModel.PatientItem> = emptyList(),
  /** The patient being brought to the device. */
  val openingId: String? = null,
  /**
   * A message to show once (a string resource); cleared by
   * [PatientListViewModel.clearServerMessage].
   */
  val messageRes: Int? = null,
)

class PatientListViewModel
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val applicationContext: Context,
  private val dataVisibility: DataVisibility = DataVisibility.Everything,
  private val unidentified: UnidentifiedStore? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PatientListUiState())
  val uiState: StateFlow<PatientListUiState> = _uiState.asStateFlow()

  val liveSearchedPatients: LiveData<List<PatientItem>> = uiState.map { it.patients }.asLiveData()
  val patientCount = MutableLiveData<Long>()

  private var loadJob: Job? = null
  private var serverSearchJob: Job? = null
  private val live by lazy { LivePatients(applicationContext, fhirEngine) }

  /** Number of patients that match the current search *and* are visible, set by each search. */
  private var visibleCount: Long? = null

  init {
    loadPatients()
    // An upload went through: what was marked as waiting is not any more.
    viewModelScope.launch { LiveSync.uploaded.collect { reloadQuietly() } }
  }

  /** The list again, without a spinner: a patient may have been registered or changed meanwhile. */
  fun reload() = reloadQuietly()

  private fun reloadQuietly() {
    updatePatientListAndPatientCount(
      { getSearchResults(nameQuery = uiState.value.query, quiet = true) },
      { count(uiState.value.query) },
    )
  }

  /** Where the last encounters are read; a test replaces it so no work outlives the test. */
  @androidx.annotation.VisibleForTesting
  internal var backgroundDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default

  fun loadPatients() {
    updatePatientListAndPatientCount(
      { getSearchResults(nameQuery = uiState.value.query) },
      { count(uiState.value.query) },
    )
  }

  fun refreshPatients() {
    updatePatientListAndPatientCount(
      { getSearchResults(nameQuery = uiState.value.query, refreshing = true) },
      { count(uiState.value.query) },
    )
  }

  fun searchPatientsByName(nameQuery: String) {
    _uiState.update { it.copy(query = nameQuery) }
    updatePatientListAndPatientCount(
      { getSearchResults(nameQuery = nameQuery) },
      { count(nameQuery) },
    )
  }

  fun clearSearch() {
    _uiState.update { it.copy(query = "") }
    searchServer("")
    loadPatients()
  }

  /**
   * [updatePatientListAndPatientCount] calls the search and count lambda and updates the live data
   * values accordingly. It is initially called when this [ViewModel] is created. Later its called
   * by the client every time search query changes or data-sync is completed.
   */
  private fun updatePatientListAndPatientCount(
    search: suspend () -> List<PatientItem>,
    count: suspend () -> Long,
  ) {
    // Only the most recent request may publish results, otherwise a slow older query can
    // overwrite the list for the current search text.
    loadJob?.cancel()
    loadJob =
      viewModelScope.launch {
        try {
          val patients = search()
          // A superseded refresh never reaches its own "finished" update, so clear both flags here.
          _uiState.update { it.copy(patients = patients, isLoading = false, isRefreshing = false) }
          patientCount.value = count()
          // The list is on screen; the last encounters follow, one query for the whole page.
          val withEncounters = withContext(backgroundDispatcher) { withLastEncounters(patients) }
          _uiState.update { it.copy(patients = withEncounters) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.e(e, "Failed to load patients")
          _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
      }
  }

  /**
   * Returns count of all the [Patient] who match the filter criteria unlike [getSearchResults]
   * which only returns a fixed range.
   */
  private suspend fun count(nameQuery: String = ""): Long {
    return visibleCount ?: fhirEngine.count<Patient> { applyPatientTextQuery(nameQuery) }
  }

  /**
   * Ids of the patients that belong to the patient lists selected in settings, or `null` when
   * nothing restricts the view (no list selected, or the lists have not been downloaded yet).
   */
  private suspend fun selectedListMemberIds(): Set<String>? {
    val selectedLists =
      try {
        applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_PATIENT_LISTS]
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }
    if (selectedLists.isNullOrEmpty()) return null
    var listsFound = 0
    val memberIds = mutableSetOf<String>()
    selectedLists.forEach { listId ->
      try {
        val group = fhirEngine.get<Group>(listId)
        listsFound++
        group.member.mapNotNullTo(memberIds) { it.entity?.referenceElement?.idPart }
      } catch (e: ResourceNotFoundException) {
        Timber.w("Selected patient list %s is not on the device yet", listId)
      }
    }
    return if (listsFound == 0) null else memberIds
  }

  /*
   * Fetches Patient list
   */
  private suspend fun getSearchResults(
    nameQuery: String = "",
    refreshing: Boolean = false,
    quiet: Boolean = false,
  ): List<PatientItem> {
    if (quiet) {
      // The list is only redrawn: no spinner.
    } else if (refreshing) {
      _uiState.update { it.copy(isRefreshing = true) }
    } else {
      _uiState.update { it.copy(isLoading = true) }
    }
    val patients: MutableList<PatientItem> = mutableListOf()
    val memberIds = selectedListMemberIds()
    // On a device shared by several users some patients may have to be left out (below), so more
    // candidates are looked at, as with a list restriction.
    val sharedDevice = dataVisibility.isSharedDevice()
    val restricted = memberIds != null || sharedDevice
    // Patients brought from the server by the live search belong to the list too.
    val liveIds = if (restricted) live.rememberedIds() else emptySet()
    val matches =
      fhirEngine
        .search<Patient> {
          applyPatientTextQuery(nameQuery)
          sort(Patient.GIVEN, Order.ASCENDING)
          // Without a list restriction the page is the result; with one, look at more candidates
          // because the ones outside the selected lists are dropped below.
          count = if (restricted) CANDIDATE_LIMIT else PAGE_SIZE
          from = 0
        }
        .map { it.resource }
    // Only patients of the selected lists are shown. A patient registered on this device is kept
    // until it is uploaded, otherwise it would vanish right after being saved.
    val toIdentify = unidentified?.ids().orEmpty()
    val inLists =
      if (memberIds == null) {
        matches
      } else {
        matches.filter { patient ->
          patient.idElement.idPart in toIdentify ||
            patient.idElement.idPart in memberIds ||
            patient.idElement.idPart in liveIds ||
            fhirEngine.getLocalChanges(ResourceType.Patient, patient.idElement.idPart).isNotEmpty()
        }
      }
    // What another user of the device brought is not shown, unless they share it or this user has
    // access to it too (it is in one of their lists, or they opened it from the server).
    val visible =
      dataVisibility.visibleAmong(inLists, (memberIds ?: emptySet()) + liveIds) {
        it.idElement.idPart
      }
    visibleCount = if (restricted) visible.size.toLong() else null
    visible
      .take(PAGE_SIZE)
      .mapIndexed { index, fhirPatient ->
        fhirPatient.toPatientItem(index + 1).let { item ->
          if (item.resourceId in toIdentify) item.copy(unidentified = true) else item
        }
      }
      .let {
        patients.addAll(it)
        patients.map { patientItem ->
          patientItem.isSynced =
            fhirEngine.getLocalChanges(ResourceType.Patient, patientItem.resourceId).isEmpty()
        }
      }

    val risks = getRiskAssessments()
    patients.forEach { patient ->
      risks["Patient/${patient.resourceId}"]?.let {
        patient.risk = it.prediction?.first()?.qualitativeRisk?.coding?.first()?.code
      }
    }
    if (quiet) {
      // Nothing was shown.
    } else if (refreshing) {
      _uiState.update { it.copy(isRefreshing = false) }
    } else {
      _uiState.update { it.copy(isLoading = false) }
    }
    return patients
  }

  // ---- Live search -----------------------------------------------------------------------

  /**
   * Looks [query] up on the server too, below the patients of the device (live mode). Called with
   * every change of the search text; a new text replaces the previous lookup, and nothing is asked
   * before the typing pauses.
   */
  fun searchServer(query: String) {
    serverSearchJob?.cancel()
    val text = query.trim()
    if (text.length < MIN_SERVER_QUERY_LENGTH) {
      updateServerSearch { ServerSearchState(openingId = it.openingId) }
      return
    }
    serverSearchJob =
      viewModelScope.launch {
        if (!liveSearchAvailable()) {
          updateServerSearch { ServerSearchState(openingId = it.openingId) }
          return@launch
        }
        updateServerSearch { it.copy(active = true, loading = true, failed = false) }
        delay(SERVER_SEARCH_PAUSE_MILLIS)
        try {
          val found =
            withTimeout(SERVER_SEARCH_TIMEOUT_MILLIS) {
              withContext(Dispatchers.IO) { live.search(text) }
            }
          updateServerSearch {
            it.copy(
              loading = false,
              results =
                found.mapIndexed { index, patient ->
                  patient.toPatientItem(index + 1).also { item -> item.isSynced = true }
                },
            )
          }
        } catch (e: TimeoutCancellationException) {
          updateServerSearch { it.copy(loading = false, failed = true, results = emptyList()) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "The server search failed")
          updateServerSearch { it.copy(loading = false, failed = true, results = emptyList()) }
        }
      }
  }

  /** Live mode is on and the device has a network to ask with. */
  private suspend fun liveSearchAvailable(): Boolean =
    try {
      LiveSync.isEnabled(applicationContext) && hasNetwork()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      false
    }

  private fun hasNetwork(): Boolean {
    val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
    return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
  }

  /**
   * Brings [patient], found on the server, to the device and calls [onOpened] with their id once
   * they can be opened like any other patient.
   */
  fun openServerPatient(patient: PatientItem, onOpened: (String) -> Unit) {
    if (_uiState.value.serverSearch.openingId != null) return
    updateServerSearch { it.copy(openingId = patient.resourceId) }
    viewModelScope.launch {
      try {
        withTimeout(SERVER_OPEN_TIMEOUT_MILLIS) {
          withContext(Dispatchers.IO) { live.download(patient.resourceId) }
        }
        // The list now includes them.
        reloadQuietly()
        onOpened(patient.resourceId)
      } catch (e: TimeoutCancellationException) {
        updateServerSearch { it.copy(messageRes = R.string.live_server_open_failed) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.w(e, "Could not bring the patient from the server")
        updateServerSearch { it.copy(messageRes = R.string.live_server_open_failed) }
      } finally {
        updateServerSearch { it.copy(openingId = null) }
      }
    }
  }

  fun clearServerMessage() {
    updateServerSearch { it.copy(messageRes = null) }
  }

  private fun updateServerSearch(change: (ServerSearchState) -> ServerSearchState) {
    _uiState.update { it.copy(serverSearch = change(it.serverSearch)) }
  }

  /** Form names by encounter type code, read once. */
  private var formNamesByCode: Map<String, String>? = null

  private suspend fun encounterTypeNames(): Map<String, String> =
    formNamesByCode
      ?: try {
          FormCatalog(applicationContext, fhirEngine).load().formsByCode.mapValues { it.value.name }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not read the form names")
          emptyMap()
        }
        .also { formNamesByCode = it }

  /** Adds each patient's most recent encounter (what it was and when). */
  private suspend fun withLastEncounters(patients: List<PatientItem>): List<PatientItem> {
    val ids = patients.map { it.resourceId }.filter { it.isNotBlank() }
    if (ids.isEmpty()) return patients
    val names = encounterTypeNames()
    val latest = mutableMapOf<String, Encounter>()
    ids.chunked(ENCOUNTER_QUERY_CHUNK).forEach { chunk ->
      val filters =
        chunk.map { id ->
          val filter: ReferenceParamFilterCriterion.() -> Unit = { value = "Patient/$id" }
          filter
        }
      fhirEngine
        .search<Encounter> { filter(Encounter.SUBJECT, *filters.toTypedArray()) }
        .map { it.resource }
        .filter {
          it.status != Encounter.EncounterStatus.CANCELLED &&
            it.status != Encounter.EncounterStatus.ENTEREDINERROR &&
            // A visit is the container of the encounters, not one of them.
            it.type.none { type -> type.coding.any { c -> c.system == VISIT_TYPE_CODE_SYSTEM } }
        }
        .forEach { encounter ->
          val patientId = encounter.subject?.reference?.substringAfter("Patient/") ?: return@forEach
          val current = latest[patientId]
          if (current == null || encounter.happenedAt() > current.happenedAt()) {
            latest[patientId] = encounter
          }
        }
    }
    return patients.map { patient ->
      val encounter = latest[patient.resourceId]
      patient.copy(
        lastEncounter =
          if (encounter == null) {
            LastEncounter()
          } else {
            LastEncounter(
              name = encounter.typeName(names),
              date =
                encounter
                  .happenedAtDate()
                  ?.toInstant()
                  ?.atZone(ZoneId.systemDefault())
                  ?.toLocalDate(),
            )
          },
      )
    }
  }

  private fun Encounter.happenedAtDate(): Date? =
    if (hasPeriod() && period.hasStart()) period.start else meta?.lastUpdated

  private fun Encounter.happenedAt(): Long = happenedAtDate()?.time ?: 0L

  /** The form the encounter was recorded with, else the type's own label. */
  private fun Encounter.typeName(names: Map<String, String>): String? {
    val coding = type.firstOrNull()?.coding?.firstOrNull()
    return coding?.code?.let { names[it] }
      ?: coding?.display?.takeIf { it.isNotBlank() }
        ?: type.firstOrNull()?.text?.takeIf { it.isNotBlank() }
  }

  private suspend fun getRiskAssessments(): Map<String, RiskAssessment?> {
    return fhirEngine
      .search<RiskAssessment> {}
      .groupBy { it.resource.subject.reference }
      .mapValues { entry ->
        entry.value
          .filter { it.resource.hasOccurrence() }
          .maxByOrNull { it.resource.occurrenceDateTimeType.value }
          ?.resource
      }
  }

  private companion object {
    const val PAGE_SIZE = 100
    const val MIN_SERVER_QUERY_LENGTH = 3
    const val SERVER_SEARCH_PAUSE_MILLIS = 450L
    const val SERVER_SEARCH_TIMEOUT_MILLIS = 15_000L
    const val SERVER_OPEN_TIMEOUT_MILLIS = 60_000L
    private const val ENCOUNTER_QUERY_CHUNK = 30
    const val CANDIDATE_LIMIT = 1000
  }

  /**
   * The most recent encounter of a patient. [name] is the form it was recorded with (when known);
   * both are null for a patient that has none.
   */
  data class LastEncounter(val name: String? = null, val date: LocalDate? = null) {
    val exists: Boolean
      get() = name != null || date != null
  }

  /** The Patient's details for display purposes. */
  data class PatientItem(
    val id: String,
    val resourceId: String,
    val name: String,
    val gender: String,
    val dob: LocalDate? = null,
    val phone: String,
    val city: String,
    val country: String,
    val isActive: Boolean,
    val identifiers: List<Identifier>,
    val html: String,
    var isSynced: Boolean? = false,
    var risk: String? = "",
    var riskItem: RiskAssessmentItem? = null,
    /** The patient's most recent encounter; null until it has been looked up. */
    val lastEncounter: LastEncounter? = null,
    /** Recorded for "identify patient after": a placeholder, the real patient is not known yet. */
    val unidentified: Boolean = false,
  ) {
    override fun toString(): String = name
  }

  /** The Observation's details for display purposes. */
  data class ObservationItem(
    val id: String,
    val code: String,
    val effective: String,
    val value: String,
  ) {
    override fun toString(): String = code
  }

  data class ConditionItem(
    val id: String,
    val code: String,
    val effective: String,
    val value: String,
  ) {
    override fun toString(): String = code
  }

  data class VisitItem(
    val id: String,
    val code: String,
    val startDate: String,
    val endDate: String,
  ) {
    override fun toString(): String = code + ", " + startDate + " - " + endDate

    fun getPeriods(): String = startDate + " - " + endDate
  }

  data class EncounterItem(
    val encounterId: String?,
    val type: String,
    val dateTime: String,
    val formCode: String?,
    val formDisplay: String?,
    val isSynced: Boolean?,
    val encounterType: String?,
  ) {
    override fun toString(): String = encounterId ?: type
  }
}

/**
 * Applies the free-text patient query: a partial match on the name, or an exact match on an
 * identifier or phone number. The three are OR-ed so that scanning/typing an identifier finds the
 * patient the same way a name does. An empty query leaves the search unfiltered.
 */
internal fun Search.applyPatientTextQuery(query: String) {
  val text = query.trim()
  if (text.isEmpty()) return
  filter(
    Patient.NAME,
    {
      modifier = StringFilterModifier.CONTAINS
      value = text
    },
  )
  filter(Patient.IDENTIFIER, { value = of(text) })
  filter(Patient.PHONE, { value = of(text) })
  operation = Operation.OR
}

internal fun filterSelectedIdsForPatients(
  patients: List<PatientListViewModel.PatientItem>,
  selectedIds: Set<String>,
): Set<String> {
  val validIds = patients.map { it.resourceId }.toSet()
  return selectedIds.filter { it in validIds }.toSet()
}

internal fun Patient.toPatientItem(position: Int): PatientListViewModel.PatientItem {
  // Show nothing if no values available for gender and date of birth.
  val patientId = if (hasIdElement()) idElement.idPart else ""
  val name = if (hasName()) name[0].nameAsSingleString else ""
  val gender = if (hasGenderElement()) genderElement.valueAsString else ""
  val dob =
    if (hasBirthDateElement()) {
      try {
        birthDateElement.value.toInstant().atOffset(ZoneOffset.UTC).toLocalDate()
      } catch (_: Exception) {
        Timber.e("${birthDateElement.valueAsString} can't be parsed")
        null
      }
    } else {
      null
    }
  val phone = if (hasTelecom()) telecom[0].value else ""
  val city = if (hasAddress()) address[0].city else ""
  val country = if (hasAddress()) address[0].country else ""
  val identifiers = identifier
  val isActive = active
  val html: String = if (hasText()) text.div.valueAsString else ""

  return PatientListViewModel.PatientItem(
    id = position.toString(),
    resourceId = patientId,
    name = name,
    gender = gender ?: "",
    dob = dob,
    phone = phone ?: "",
    city = city ?: "",
    country = country ?: "",
    isActive = isActive,
    identifiers = identifiers,
    html = html,
  )
}
