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
package org.openmrs.android.fhir.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.sync.BackoffCriteria
import com.google.android.fhir.sync.CurrentSyncJobStatus
import com.google.android.fhir.sync.RetryConfiguration
import com.google.android.fhir.sync.Sync
import com.google.android.fhir.sync.SyncJobStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.IdentifierTypeManager
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import timber.log.Timber

/**
 * Live mode, the sending half: what the user saves is uploaded to the server a moment later instead
 * of waiting for the next sync.
 *
 * Nothing changes for the data itself. A save still goes to the device first (so the screen never
 * waits for the network) and is queued as a local change; [requestUpload] then starts an
 * upload-only run of the same sync, with the same upload strategy, authentication and queue. What
 * the server refuses or cannot be reached for simply stays queued, and the next full sync (or the
 * next live upload) sends it.
 */
object LiveSync {
  /** What the live upload is doing, for the connection banner. */
  sealed interface State {
    data object Idle : State

    /** An upload is running. */
    data object Sending : State

    /** The last attempt did not get through; it is tried again, and the next sync sends it too. */
    data object Retrying : State
  }

  private val _state = MutableStateFlow<State>(State.Idle)
  val state: StateFlow<State> = _state.asStateFlow()

  private val _uploaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /** Emits each time an upload went through, so the screens showing "not synced" can refresh. */
  val uploaded: SharedFlow<Unit> = _uploaded

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var pending: Job? = null

  /** Live mode is on and this is a signed-in user (a guest has no server to send to). */
  suspend fun isEnabled(context: Context): Boolean =
    !GuestSession.isActive(context) &&
      DemoDataStore(context.applicationContext).getLiveModeEnabledFlow().first()

  /**
   * Called after something was saved. Saves that follow each other (an encounter and its visit, a
   * group session) are sent together: the upload starts a moment after the last one.
   */
  fun requestUpload(context: Context) {
    val appContext = context.applicationContext
    synchronized(this) {
      pending?.cancel()
      pending =
        scope.launch {
          try {
            if (!isEnabled(appContext)) return@launch
            delay(DEBOUNCE_MILLIS)
            enqueue(appContext)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Timber.w(e, "Could not start the live upload")
          }
        }
    }
  }

  private fun enqueue(context: Context) {
    val request =
      OneTimeWorkRequestBuilder<LiveUploadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
        .build()
    // Appended: a request that arrives while one runs goes after it (it may hold what was saved
    // since), and a request that finds the previous one failed starts afresh.
    WorkManager.getInstance(context)
      .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
  }

  internal fun setState(state: State) {
    _state.value = state
  }

  internal fun notifyUploaded() {
    _uploaded.tryEmit(Unit)
  }

  const val WORK_NAME = "live_upload"
  internal const val MAX_ATTEMPTS = 3
  private const val DEBOUNCE_MILLIS = 800L
  private const val RETRY_DELAY_SECONDS = 20L
}

/**
 * Only one upload runs at a time: two runs would both read the same queue and create what is in it
 * twice on the server. The full sync and the live upload each hold it while they upload.
 */
object UploadGate {
  val mutex = Mutex()
}

class LiveUploadWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val context = applicationContext
    return try {
      if (!LiveSync.isEnabled(context)) {
        LiveSync.setState(LiveSync.State.Idle)
        return Result.success()
      }
      UploadGate.mutex.withLock { upload(context) }
    } catch (e: CancellationException) {
      LiveSync.setState(LiveSync.State.Idle)
      throw e
    } catch (e: Exception) {
      Timber.e(e, "The live upload failed")
      outcome(succeeded = false, reason = e.javaClass.simpleName)
    }
  }

  private suspend fun upload(context: Context): Result {
    // The same conditions as a sync: without a location the server refuses what is uploaded, and
    // what was entered as a guest has to be completed (by the sync) before it can go.
    if (context.dataStore.data.first()[PreferenceKeys.LOCATION_ID].isNullOrBlank()) {
      LiveSync.setState(LiveSync.State.Idle)
      return Result.success()
    }
    if (GuestSession.isCompletionPending(context)) {
      LiveSync.setState(LiveSync.State.Idle)
      return Result.success()
    }
    LiveSync.setState(LiveSync.State.Sending)

    val fhirEngine = FhirEngineProvider.getInstance(context)
    val database = FhirApplication.appComponent(context).database()
    val apiManager = ApiManager(context)
    // Who is signed in is the provider of what is uploaded.
    OpenMRSHelper(fhirEngine, context).ensureIdentity()
    if (AppConfig.get(context).fetchIdentifiers) {
      val preparer =
        UploadPreparer(
          context,
          fhirEngine,
          database,
          apiManager,
          IdentifierTypeManager(context, database, apiManager),
        )
      preparer.fetchIdentifierTypesIfEmpty()
      preparer.embedIdentifiersInUnsyncedPatients()
    }

    var failure: String? = null
    val finalStatus =
      try {
        withTimeout(UPLOAD_TIMEOUT_MILLIS) {
          // One attempt: the sync's own retries wait minutes, and the upload gate is held all that
          // time. This worker is the one that tries again.
          Sync.oneTimeSync<UploadFhirSyncWorker>(context, NO_RETRY)
            .onEach { status ->
              val failed =
                (status as? CurrentSyncJobStatus.Running)?.inProgressSyncJob
                  as? SyncJobStatus.Failed
              failed?.exceptions?.firstOrNull()?.exception?.message?.let { failure = it }
            }
            .first { status ->
              status is CurrentSyncJobStatus.Failed ||
                status is CurrentSyncJobStatus.Succeeded ||
                status is CurrentSyncJobStatus.Cancelled ||
                status is CurrentSyncJobStatus.Blocked
            }
        }
      } catch (e: TimeoutCancellationException) {
        // A connection that hangs: give the gate back and leave what is queued for later.
        Sync.cancelOneTimeSync<UploadFhirSyncWorker>(context)
        failure = "timed out"
        null
      }
    return outcome(
      finalStatus is CurrentSyncJobStatus.Succeeded,
      failure ?: finalStatus.toString(),
    )
  }

  private companion object {
    const val UPLOAD_TIMEOUT_MILLIS = 90_000L
    val NO_RETRY =
      RetryConfiguration(BackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS), 0)
  }

  private fun outcome(succeeded: Boolean, reason: String): Result {
    if (succeeded) {
      AuditLog.record(AuditAction.SYNC, AuditOutcome.SUCCESS, "state" to "live_upload")
      LiveSync.setState(LiveSync.State.Idle)
      LiveSync.notifyUploaded()
      return Result.success()
    }
    AuditLog.failure(AuditAction.SYNC, "state" to "live_upload", "reason" to reason.take(120))
    // What did not go stays in the queue for the next sync; it is only tried again a few times.
    return if (runAttemptCount + 1 < LiveSync.MAX_ATTEMPTS) {
      LiveSync.setState(LiveSync.State.Retrying)
      Result.retry()
    } else {
      LiveSync.setState(LiveSync.State.Retrying)
      Result.success()
    }
  }
}
