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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Why WorkManager stopped a sync that nobody cancelled (the connection changed, the system
 * restricted the app, a time limit...). A cancelled sync only says "cancelled", which looks the
 * same as one the user asked for; the reason goes to the log and the audit trail so that a
 * diagnostics report tells them apart.
 */
object SyncStopReason {

  fun nameOf(code: Int): String =
    when (code) {
      WorkInfo.STOP_REASON_NOT_STOPPED -> "NOT_STOPPED"
      WorkInfo.STOP_REASON_UNKNOWN -> "UNKNOWN"
      WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "CANCELLED_BY_APP"
      WorkInfo.STOP_REASON_PREEMPT -> "PREEMPT"
      WorkInfo.STOP_REASON_TIMEOUT -> "TIMEOUT"
      WorkInfo.STOP_REASON_DEVICE_STATE -> "DEVICE_STATE"
      WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "CONSTRAINT_BATTERY_NOT_LOW"
      WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "CONSTRAINT_CHARGING"
      WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "CONSTRAINT_CONNECTIVITY"
      WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "CONSTRAINT_DEVICE_IDLE"
      WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "CONSTRAINT_STORAGE_NOT_LOW"
      WorkInfo.STOP_REASON_QUOTA -> "QUOTA"
      WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "BACKGROUND_RESTRICTION"
      WorkInfo.STOP_REASON_APP_STANDBY -> "APP_STANDBY"
      WorkInfo.STOP_REASON_USER -> "USER"
      WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "SYSTEM_PROCESSING"
      WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "ESTIMATED_APP_LAUNCH_TIME_CHANGED"
      WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT -> "FOREGROUND_SERVICE_TIMEOUT"
      else -> code.toString()
    }

  /**
   * The reasons the sync workers were last stopped, for the log: [own] is that of the worker that
   * runs the sync, the others are those of the SDK's workers it starts.
   */
  suspend fun describe(context: Context, own: Int? = null): String {
    val parts = mutableListOf<String>()
    if (own != null) parts += "sync=" + nameOf(own)
    val manager = WorkManager.getInstance(context)
    listOf(
        FhirSyncWorker::class.java,
        UploadFhirSyncWorker::class.java,
        DownloadOnlyFhirSyncWorker::class.java,
      )
      .forEach { worker ->
        val infos =
          withContext(Dispatchers.IO) {
            runCatching { manager.getWorkInfosForUniqueWork(worker.name + ONE_TIME_SUFFIX).get() }
              .getOrDefault(emptyList())
          }
        infos
          .map { it.stopReason }
          .filter { it != WorkInfo.STOP_REASON_NOT_STOPPED }
          .forEach { parts += worker.simpleName + "=" + nameOf(it) }
      }
    return parts.joinToString(", ").ifEmpty { "none recorded" }
  }

  /** How the SDK names the unique work of a one-time sync. */
  private const val ONE_TIME_SUFFIX = "-oneTimeSync"
}
