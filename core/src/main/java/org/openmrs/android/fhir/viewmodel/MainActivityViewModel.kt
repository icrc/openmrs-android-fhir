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
import android.net.Network
import android.text.format.DateFormat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.WorkManager
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.CurrentSyncJobStatus
import com.google.android.fhir.sync.PeriodicSyncConfiguration
import com.google.android.fhir.sync.PeriodicSyncJobStatus
import com.google.android.fhir.sync.RepeatInterval
import com.google.android.fhir.sync.Sync
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.FhirSyncWorker
import org.openmrs.android.fhir.data.IdentifierTypeManager
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.data.remote.model.IdentifierWrapper
import org.openmrs.android.fhir.data.remote.model.SessionLocation

/** View model for [MainActivity]. */
class MainActivityViewModel
@Inject
constructor(
  private val applicationContext: Context,
  val fhirEngine: FhirEngine,
  val database: AppDatabase,
  val apiManager: ApiManager,
  val identifierTypeManager: IdentifierTypeManager,
) : ViewModel() {
  private var _stopSync: Boolean = false
  val stopSync
    get() = _stopSync

  private val _lastSyncTimestampLiveData = MutableLiveData<String>()
  val lastSyncTimestampLiveData: LiveData<String>
    get() = _lastSyncTimestampLiveData

  private val _oneTimeSyncTrigger = MutableStateFlow(false)

  private val restApiManager = FhirApplication.restApiClient(applicationContext)

  private val _networkStatus = MutableStateFlow(false)
  val networkStatus: StateFlow<Boolean>
    get() = _networkStatus

  private var connectivityManager: ConnectivityManager =
    applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private var networkCallBack: ConnectivityManager.NetworkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        super.onAvailable(network)
        // Network is available
        _networkStatus.value = true
      }

      override fun onLost(network: Network) {
        super.onLost(network)
        // Network is lost
        _networkStatus.value = false
      }
    }

  val pollPeriodicSyncJobStatus: SharedFlow<PeriodicSyncJobStatus> =
    Sync.periodicSync<FhirSyncWorker>(
        applicationContext,
        periodicSyncConfiguration =
          PeriodicSyncConfiguration(
            syncConstraints = Constraints.Builder().build(),
            repeat = RepeatInterval(interval = 15, timeUnit = TimeUnit.MINUTES),
          ),
      )
      .shareIn(viewModelScope, SharingStarted.Eagerly, 10)

  val pollState: SharedFlow<CurrentSyncJobStatus> =
    _oneTimeSyncTrigger
      .combine(
        flow = Sync.oneTimeSync<FhirSyncWorker>(context = applicationContext),
      ) { _, syncJobStatus ->
        syncJobStatus
      }
      .shareIn(viewModelScope, SharingStarted.Eagerly, 0)

  fun triggerOneTimeSync(context: Context) {
    viewModelScope.launch {
      if (!stopSync) {
        fetchIdentifierTypesIfEmpty()
        embeddIdentifierToUnsyncedPatients(context)
        _oneTimeSyncTrigger.value = !_oneTimeSyncTrigger.value
        _oneTimeSyncTrigger.combine(
          flow = Sync.oneTimeSync<FhirSyncWorker>(context = applicationContext),
        ) { _, syncJobStatus ->
          syncJobStatus
        }
      }
    }
  }

  fun triggerIdentifierTypeSync(context: Context) {
    viewModelScope.launch {
      if (!stopSync) {
        fetchIdentifierTypesIfEmpty()
      }
    }
  }

  private suspend fun embeddIdentifierToUnsyncedPatients(context: Context) {
    // Setting location session first.
    context.dataStore.data.first()[PreferenceKeys.LOCATION_ID]?.let {
      apiManager.setLocationSession(SessionLocation(it))
    }
    var filteredIdentifierTypes = setOf<String>()
    val identifierTypeToSourceIdMap = mutableMapOf<String, String>()
    val selectedIdentifierTypes =
      context.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.toList()
    if (selectedIdentifierTypes != null) {
      filteredIdentifierTypes =
        selectedIdentifierTypes
          .filter { identifierTypeId ->
            val identifierType = let { database.dao().getIdentifierTypeById(identifierTypeId) }
            val isAutoGenerated = identifierType?.isAutoGenerated
            if (isAutoGenerated == true) {
              identifierTypeToSourceIdMap[identifierTypeId] = identifierType.sourceId
            }
            isAutoGenerated != null && isAutoGenerated == true
          }
          .toSet()
    }

    val patients =
      fhirEngine
        .search<org.hl7.fhir.r4.model.Patient> {
          filter(org.hl7.fhir.r4.model.Patient.IDENTIFIER, { value = of("unsynced") })
        }
        .map { it.resource }
        .toMutableList()

    patients.forEach {
      val identifiers = it.identifier
      identifiers.forEach { identifier ->
        if (identifier.value in filteredIdentifierTypes) {
          val value =
            identifierTypeToSourceIdMap[identifier.value]?.let { it1 ->
              fetchIdentifierFromEndpoint(
                it1,
              )
            }
          identifier.value = value
        }
      }
      identifiers.removeAt(0)
      it.identifier = identifiers
      fhirEngine.update(it)
    }
  }

  private suspend fun fetchIdentifierFromEndpoint(identifierId: String): String? {
    val response = apiManager.getIdentifier(identifierId)
    return when (response) {
      is ApiResponse.Success<IdentifierWrapper> -> response.data?.identifier
      else -> null
    }
  }

  private suspend fun fetchIdentifierTypesIfEmpty() {
    val identifierTypes = database.dao().getIdentifierTypesCount()
    if (identifierTypes == 0) {
      identifierTypeManager.fetchIdentifiers()
    }
  }

  /** Emits last sync time. */
  fun updateLastSyncTimestamp(lastSync: OffsetDateTime? = null) {
    val formatter =
      DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(applicationContext)) formatString24 else formatString12,
      )
    _lastSyncTimestampLiveData.value =
      lastSync?.let { it.toLocalDateTime()?.format(formatter) ?: "" }
        ?: Sync.getLastSyncTimestamp(applicationContext)?.toLocalDateTime()?.format(formatter) ?: ""
  }

  fun registerNetworkCallback() {
    connectivityManager.registerDefaultNetworkCallback(networkCallBack)
  }

  // Unregister the network callback
  fun unregisterNetworkCallback() {
    connectivityManager.unregisterNetworkCallback(networkCallBack)
  }

  fun isServerAvailable(): Boolean {
    return restApiManager.isServerLive()
  }

  fun cancelPeriodicSyncWorker(context: Context) {
    WorkManager.getInstance(context)
      .cancelUniqueWork("${FhirSyncWorker::class.java.name}-periodicSync")
  }

  fun setStopSync(stopSync: Boolean) {
    this._stopSync = stopSync
  }

  companion object {
    private const val formatString24 = "yyyy-MM-dd HH:mm:ss"
    private const val formatString12 = "yyyy-MM-dd hh:mm:ss a"
    const val PREFS_NAME = "tokenDialogPrefs"
  }
}
