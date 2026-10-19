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
package org.openmrs.android.fhir.worker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.fhir.sync.CurrentSyncJobStatus
import com.google.android.fhir.sync.Sync
import com.google.android.fhir.sync.SyncJobStatus
import com.google.android.fhir.sync.SyncOperation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.withLock
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.SyncStatus
import org.openmrs.android.fhir.data.sync.DownloadOnlyFhirSyncWorker
import org.openmrs.android.fhir.data.sync.FhirSyncWorker
import org.openmrs.android.fhir.data.sync.LiveSync
import org.openmrs.android.fhir.data.sync.PatientListDownloader
import org.openmrs.android.fhir.data.sync.SyncMode
import org.openmrs.android.fhir.data.sync.UploadFhirSyncWorker
import org.openmrs.android.fhir.data.sync.UploadGate
import org.openmrs.android.fhir.di.DaggerWorkerComponent
import org.openmrs.android.fhir.extensions.NotificationHelper
import org.openmrs.android.fhir.extensions.PermissionChecker
import timber.log.Timber

class SyncInfoDatabaseWriterWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params) {

  @Inject lateinit var database: AppDatabase

  @Inject lateinit var notificationHelper: NotificationHelper

  @Inject lateinit var permissionChecker: PermissionChecker

  /** What this run does: both (the default), only send, or only download. */
  private val mode by lazy { SyncMode.fromValue(inputData.getString(KEY_MODE)) }

  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  init {
    // Inject dependencies using your Dagger component
    DaggerWorkerComponent.builder()
      .appComponent((applicationContext as FhirApplication).appComponent)
      .build()
      .inject(this)
  }

  /**
   * A sync that was cut short (the app was killed, the phone restarted) never closed its session.
   * Left open, the next sync would carry on with its numbers, so it is closed as interrupted first.
   */
  private suspend fun closeInterruptedSessions() {
    val dao = database.dao()
    dao.getOngoingSyncSessions().forEach { session ->
      dao.updateSyncSessionErrors(
        session.id,
        listOf(applicationContext.getString(R.string.sync_interrupted)),
      )
      dao.updateSyncSessionStatus(session.id, SyncStatus.COMPLETED_WITH_ERRORS)
      dao.updateSyncSessionCompletionTime(session.id, LocalDateTime.now().format(formatter))
    }
  }

  override suspend fun doWork(): Result {
    return try {
      closeInterruptedSessions()
      setProgress(
        workDataOf(
          PROGRESS_STATUS to "STARTED",
          PROGRESS_MESSAGE to "Initializing sync process",
        ),
      )

      if (mode != SyncMode.UPLOAD_ONLY) {
        // The patients of patient lists added since: the sync below only asks for what changed.
        try {
          val fhirEngine = (applicationContext as FhirApplication).appComponent.fhirEngine()
          PatientListDownloader(applicationContext, fhirEngine).downloadPending()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not download the patients of the added lists")
        }
      }

      val runSync: suspend () -> CurrentSyncJobStatus = {
        // This flow can still be shared if needed elsewhere
        val syncFlow =
          when (mode) {
            SyncMode.UPLOAD_ONLY -> Sync.oneTimeSync<UploadFhirSyncWorker>(applicationContext)
            SyncMode.DOWNLOAD_ONLY ->
              Sync.oneTimeSync<DownloadOnlyFhirSyncWorker>(applicationContext)
            SyncMode.BOTH -> Sync.oneTimeSync<FhirSyncWorker>(applicationContext)
          }.shareIn(workerScope, SharingStarted.Eagerly, 10)

        // Process each update, but wait for a final state to finish
        syncFlow
          .onEach { syncJobStatus -> handleCurrentSyncJobStatus(syncJobStatus) }
          .first { syncJobStatus ->
            syncJobStatus is CurrentSyncJobStatus.Failed ||
              syncJobStatus is CurrentSyncJobStatus.Succeeded ||
              syncJobStatus is CurrentSyncJobStatus.Cancelled ||
              syncJobStatus is CurrentSyncJobStatus.Blocked
          }
      }
      // A live upload may be running: two uploads at once would send the same queue twice. A
      // download sends nothing, so it does not wait for one.
      val finalStatus =
        if (mode == SyncMode.DOWNLOAD_ONLY) runSync() else UploadGate.mutex.withLock { runSync() }

      // Now, check the final status to determine the worker's result
      return if (finalStatus is CurrentSyncJobStatus.Succeeded) {
        setProgress(
          workDataOf(
            PROGRESS_STATUS to "COMPLETED",
            PROGRESS_MESSAGE to "Sync process completed successfully",
          ),
        )
        Result.success()
      } else {
        // The flow ended with a failure state
        setProgress(
          workDataOf(
            PROGRESS_STATUS to "FAILED",
            PROGRESS_MESSAGE to "SYNC Failed",
          ),
        )
        Result.failure()
      }
    } catch (e: CancellationException) {
      // The work was stopped (for example replaced by a newer request). Let WorkManager see it.
      throw e
    } catch (e: Exception) {
      Timber.e(e, "Sync info worker failed")
      Result.failure()
    } finally {
      // shareIn(Eagerly) keeps collecting from the sync flow until its scope is cancelled.
      workerScope.cancel()
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun handleCurrentSyncJobStatus(syncJobStatus: CurrentSyncJobStatus) {
    when (syncJobStatus) {
      is CurrentSyncJobStatus.Enqueued -> {}
      is CurrentSyncJobStatus.Running -> {
        if (syncJobStatus.inProgressSyncJob is SyncJobStatus.Started) {
          handleSyncStarted()
        } else {
          handleInProgressSync(syncJobStatus)
        }
      }
      is CurrentSyncJobStatus.Succeeded -> {
        handleSuccessSync(syncJobStatus)
        permissionChecker.checkNotificationPermissionStatus { isGranted ->
          if (isGranted) {
            showSyncCompletedNotification()
          }
        }
      }
      is CurrentSyncJobStatus.Failed -> {
        handleFailedSync(syncJobStatus)
        permissionChecker.checkNotificationPermissionStatus { isGranted ->
          if (isGranted) {
            showSyncFailedNotification()
          }
        }
      }
      else -> {
        handleUnknownSyncState()
      }
    }
  }

  private suspend fun handleSyncStarted() {
    AuditLog.record(
      AuditAction.SYNC,
      AuditOutcome.SUCCESS,
      "state" to "started",
      "mode" to mode.value,
    )
    database.dao().getOrCreateInProgressSyncSession(formatter)
  }

  @SuppressLint("MissingPermission")
  private suspend fun handleInProgressSync(state: CurrentSyncJobStatus) {
    if (
      state is CurrentSyncJobStatus.Running && state.inProgressSyncJob is SyncJobStatus.InProgress
    ) {
      val inProgressSyncSession = database.dao().getInProgressSyncSession()
      if (inProgressSyncSession != null) {
        val inProgressState = state.inProgressSyncJob as SyncJobStatus.InProgress
        if (inProgressState.syncOperation == SyncOperation.UPLOAD) {
          // Adding case if there's nothing to sync then it will delete the sync record.
          database
            .dao()
            .updateSyncSessionUploadValues(
              sessionId = inProgressSyncSession.id,
              completed = inProgressState.completed,
              total = inProgressState.total,
            )
          permissionChecker.checkNotificationPermissionStatus { isGranted ->
            if (isGranted) {
              showInProgressNotifiation(
                inProgressState.completed,
                inProgressState.total,
                isDownload = false,
              )
            }
          }
        } else {
          database
            .dao()
            .updateSyncSessionDownloadValues(
              sessionId = inProgressSyncSession.id,
              completed = inProgressState.completed,
              total = inProgressState.total,
            )
          permissionChecker.checkNotificationPermissionStatus { isGranted ->
            if (isGranted) {
              showInProgressNotifiation(
                inProgressState.completed,
                inProgressState.total,
                isDownload = true,
              )
            }
          }
        }
      }
    }
    if (state is CurrentSyncJobStatus.Running) {
      val failed = state.inProgressSyncJob as? SyncJobStatus.Failed ?: return

      val session = database.dao().getInProgressSyncSession() ?: return

      val errors: List<String> =
        failed.exceptions
          .mapNotNull { it.exception.message } // drops nulls
          .ifEmpty { listOf("Sync failed (no error message)") }

      database.dao().updateSyncSessionErrors(session.id, errors)
    }
  }

  private suspend fun handleSuccessSync(state: CurrentSyncJobStatus.Succeeded) {
    // Only a sync that downloaded is a "last sync".
    if (mode != SyncMode.UPLOAD_ONLY) {
      DemoDataStore(applicationContext).saveLastFullSync(state.timestamp)
    }
    // What a live upload could not send went with this sync.
    LiveSync.setState(LiveSync.State.Idle)
    AuditLog.record(AuditAction.SYNC, AuditOutcome.SUCCESS, "state" to "completed")
    val inProgressSyncSession = database.dao().getInProgressSyncSession()
    if (inProgressSyncSession != null) {
      if (inProgressSyncSession.downloadedPatients + inProgressSyncSession.uploadedPatients == 0) {
        database.dao().deleteSyncSession(inProgressSyncSession.id)
      } else {
        database
          .dao()
          .updateSyncSessionUploadValues(
            sessionId = inProgressSyncSession.id,
            completed = inProgressSyncSession.uploadedPatients,
            total =
              inProgressSyncSession
                .uploadedPatients, // TODO: Make this total once total sync number is accurate
          )
        database
          .dao()
          .updateSyncSessionDownloadValues(
            sessionId = inProgressSyncSession.id,
            completed = inProgressSyncSession.downloadedPatients,
            total =
              inProgressSyncSession.downloadedPatients, // TODO: Same as above, make this total
          )
        database.dao().updateSyncSessionStatus(inProgressSyncSession.id, SyncStatus.COMPLETED)
        database
          .dao()
          .updateSyncSessionCompletionTime(
            inProgressSyncSession.id,
            state.timestamp.format(formatter).toString(),
          )
      }
    }
  }

  private suspend fun handleFailedSync(syncJobStatus: CurrentSyncJobStatus.Failed) {
    if (mode != SyncMode.UPLOAD_ONLY) {
      DemoDataStore(applicationContext).saveLastFullSync(syncJobStatus.timestamp)
    }
    AuditLog.failure(AuditAction.SYNC, "state" to "failed")
    val inProgressSyncSession = database.dao().getInProgressSyncSession()
    if (inProgressSyncSession != null) {
      database
        .dao()
        .updateSyncSessionStatus(inProgressSyncSession.id, SyncStatus.COMPLETED_WITH_ERRORS)
      database
        .dao()
        .updateSyncSessionCompletionTime(
          inProgressSyncSession.id,
          syncJobStatus.timestamp.format(formatter).toString(),
        )
    }
  }

  private suspend fun handleUnknownSyncState() {
    val inProgressSyncSession = database.dao().getInProgressSyncSession()
    if (inProgressSyncSession != null) {
      database
        .dao()
        .updateSyncSessionStatus(inProgressSyncSession.id, SyncStatus.COMPLETED_WITH_ERRORS)
    }
  }

  @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
  private fun showSyncCompletedNotification() {
    try {
      notificationHelper.showSyncCompleted()
    } catch (e: Exception) {
      Timber.w(e, "Failed to show sync notification")
    }
  }

  @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
  private fun showInProgressNotifiation(currentCount: Int, totalCount: Int, isDownload: Boolean) {
    try {
      notificationHelper.updateSyncProgress(currentCount, totalCount, isDownload)
    } catch (e: Exception) {
      Timber.w(e, "Failed to show sync notification")
    }
  }

  @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
  private fun showSyncFailedNotification() {
    try {
      notificationHelper.showSyncFailed(
        applicationContext.getString(R.string.please_try_again_later),
      )
    } catch (e: Exception) {
      Timber.w(e, "Failed to show sync notification")
    }
  }

  companion object {
    const val WORK_NAME = "sync_manager_work"
    const val PROGRESS_STATUS = "progress_status"
    const val PROGRESS_MESSAGE = "progress_message"
    private const val KEY_MODE = "sync_mode"

    fun enqueue(context: Context, mode: SyncMode = SyncMode.BOTH) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

      val syncRequest =
        OneTimeWorkRequestBuilder<SyncInfoDatabaseWriterWorker>()
          .setConstraints(constraints)
          .setInputData(workDataOf(KEY_MODE to mode.value))
          .build()

      WorkManager.getInstance(context)
        .enqueueUniqueWork(
          WORK_NAME,
          ExistingWorkPolicy.REPLACE,
          syncRequest,
        )
    }
  }
}
