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
package org.openmrs.android.fhir.extensions

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import javax.inject.Inject
import javax.inject.Singleton
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R

@Singleton
class NotificationHelper
@Inject
constructor(
  private val context: Context,
) {

  companion object {
    private const val CHANNEL_ID = "sync_tasks_channel"
    private const val SYNC_NOTIFICATION_ID = 100
  }

  init {
    createNotificationChannel()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            "Sync Tasks",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
          .apply {
            description = "Task synchronization notifications"
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(true)
          }

      val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
  fun showSyncStarted() {
    val notification =
      createBaseNotification(
          title = context.getString(R.string.sync_started),
          message = context.getString(R.string.patient_resources_are_being_synchronized),
          ongoing = true,
        )
        .setProgress(100, 0, true)

    NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification.build())
  }

  @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
  fun updateSyncProgress(progress: Int, maxProgress: Int = 100, isDownload: Boolean = true) {
    val notification =
      if (isDownload) {
        createBaseNotification(
            title = context.getString(R.string.syncing_patient_data),
            message = "${context.getString(R.string.downloaded)}: $progress/$maxProgress",
            ongoing = true,
            setSilent = true,
          )
          .setProgress(maxProgress, progress, false)
      } else {
        createBaseNotification(
            title = context.getString(R.string.uploading_patient_data),
            message = "${context.getString(R.string.uploaded)}: $progress/$maxProgress",
            ongoing = true,
            setSilent = true,
          )
          .setProgress(maxProgress, progress, false)
      }

    NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification.build())
  }

  @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
  fun showSyncCompleted() {
    val notification =
      createBaseNotification(
        title = context.getString(R.string.sync_completed),
        message = context.getString(R.string.all_data_synchronized_successfully),
        autoCancel = true,
      )

    NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification.build())
  }

  @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
  fun showSyncFailed(error: String) {
    val notification =
      createBaseNotification(
        title = context.getString(R.string.sync_failed),
        message = "${context.getString(R.string.error)}: $error",
        autoCancel = true,
      )

    NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification.build())
  }

  private fun createBaseNotification(
    title: String,
    message: String,
    ongoing: Boolean = false,
    autoCancel: Boolean = false,
    setSilent: Boolean = false,
  ): NotificationCompat.Builder {
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_baseline_sync_24)
      .setContentTitle(title)
      .setContentText(message)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setContentIntent(pendingIntent)
      .setOngoing(ongoing)
      .setAutoCancel(autoCancel)
      .setSilent(setSilent)
  }
}
