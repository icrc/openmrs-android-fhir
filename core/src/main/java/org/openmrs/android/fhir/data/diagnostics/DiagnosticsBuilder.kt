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
package org.openmrs.android.fhir.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import androidx.datastore.preferences.core.Preferences
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.BuildConfig
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.remote.ServerSettings
import org.openmrs.android.fhir.data.transfer.DataTransfer
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.ColorVisionStore
import org.openmrs.android.fhir.extensions.EmbeddedFormsStore
import org.openmrs.android.fhir.extensions.FileLoggingTree.Companion.APPLICATION_LOG_FILE_NAME
import org.openmrs.android.fhir.extensions.ThemeModeStore
import org.openmrs.android.fhir.extensions.bundledQuestionnaireIds
import org.openmrs.android.fhir.extensions.getApplicationLogs
import timber.log.Timber

/** What a diagnostics report can hold; the user picks, everything is picked by default. */
enum class DiagnosticsOption(val fileName: String) {
  APP_LOGS("application-logs.txt"),
  AUDIT_TRAIL("audit-trail.jsonl"),
  SYNC_INFO("sync-info.txt"),
  UNSYNCED_RESOURCES("unsynced-resources.ndjson"),
  DEVICE_INFO("device-info.txt"),
}

/** How far back the logs, the audit trail and the sync information go. */
enum class DiagnosticsRange(val hours: Long?) {
  EVERYTHING(null),
  LAST_24_HOURS(24),
  LAST_7_DAYS(24 * 7),
}

/** Roughly what each part holds, shown next to its checkbox. */
data class DiagnosticsEstimate(
  val logsBytes: Long,
  val auditEntries: Int,
  val syncSessions: Int,
  val unsyncedResources: Int,
)

/** The report: an AES-encrypted archive, and what went into it. */
data class DiagnosticsReport(val file: File, val included: List<DiagnosticsOption>)

/**
 * Builds the diagnostics archive the support team gets. Everything runs on a background thread of
 * low priority, one piece at a time and straight into files (the unsynced resources are written one
 * resource at a time), so a big report neither blocks nor slows the app down.
 */
class DiagnosticsBuilder
@Inject
constructor(
  private val context: Context,
  private val fhirEngine: FhirEngine,
  private val database: AppDatabase,
  private val dataTransfer: DataTransfer,
) {

  /** Cheap counts, taken without reading the data itself. */
  suspend fun estimate(): DiagnosticsEstimate =
    withContext(Dispatchers.IO) {
      DiagnosticsEstimate(
        // The log file holds each line encrypted and encoded, about a third larger than the text.
        logsBytes = File(context.filesDir, APPLICATION_LOG_FILE_NAME).length() * 3 / 4,
        auditEntries = AuditLog.count(),
        syncSessions = database.dao().getAllSyncSessionsAsList().size,
        unsyncedResources = dataTransfer.pendingCount(),
      )
    }

  suspend fun build(
    options: Set<DiagnosticsOption>,
    range: DiagnosticsRange,
    lastSync: String?,
  ): DiagnosticsReport =
    withContext(Dispatchers.IO) {
      val previousPriority = Process.getThreadPriority(Process.myTid())
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      val workDir = File(context.cacheDir, WORK_DIR).apply { deleteRecursively() }
      workDir.mkdirs()
      try {
        val password = AppConfig.get(context).diagnosticsPassword
        val since =
          range.hours?.let {
            java.time.Instant.now().minus(it, java.time.temporal.ChronoUnit.HOURS)
          }
        val parts = linkedMapOf<DiagnosticsOption, File>()
        val notes = mutableListOf<String>()
        for (option in DiagnosticsOption.entries.filter { it in options }) {
          val target = File(workDir, option.fileName)
          val written =
            try {
              when (option) {
                DiagnosticsOption.APP_LOGS ->
                  getApplicationLogs(context, password)?.let { logs ->
                    val kept = copyLogs(logs, target, since)
                    logs.delete()
                    kept > 0
                  }
                    ?: false
                DiagnosticsOption.AUDIT_TRAIL -> AuditLog.exportTo(target, since) > 0
                DiagnosticsOption.SYNC_INFO -> writeSyncInfo(target, lastSync, since)
                DiagnosticsOption.UNSYNCED_RESOURCES ->
                  dataTransfer.writeUnsynced(target).also { notes += "Unsynced resources: $it" } >=
                    0
                DiagnosticsOption.DEVICE_INFO -> writeDeviceInfo(target)
              }
            } catch (e: Exception) {
              Timber.e(e, "Diagnostics: $option failed")
              target.writeText("This part could not be collected: ${e.javaClass.simpleName}\n")
              notes += "$option failed: ${e.javaClass.simpleName}"
              true
            }
          if (written && target.exists()) parts[option] = target else notes += "$option: empty"
        }

        val manifest = File(workDir, "manifest.txt")
        manifest.writeText(
          buildString {
            appendLine("OpenMRS diagnostics report")
            appendLine("Created: ${timestamp()}")
            appendLine("App: ${context.packageName}")
            appendLine("Included: ${parts.keys.joinToString { it.fileName }.ifEmpty { "nothing" }}")
            appendLine("Time range: ${range.name}")
            notes.forEach { appendLine(it) }
          },
        )

        val zipFile =
          File(context.cacheDir, "diagnostics.zip").also { if (it.exists()) it.delete() }
        val zip = ZipFile(zipFile, password.toCharArray())
        val params =
          ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
          }
        (parts.values + manifest).forEach { zip.addFile(it, params) }
        DiagnosticsReport(zipFile, parts.keys.toList())
      } finally {
        workDir.deleteRecursively()
        Process.setThreadPriority(previousPriority)
      }
    }

  /** Copies the log lines from [since] on (all of them without it); returns how many. */
  private fun copyLogs(source: File, target: File, since: java.time.Instant?): Int {
    var kept = 0
    target.bufferedWriter().use { out ->
      source.forEachLine { line ->
        if (since == null || logLineIsSince(line, since)) {
          out.appendLine(line)
          kept++
        }
      }
    }
    return kept
  }

  /** A line starts with its time; one without (an older line) is older than any range. */
  private fun logLineIsSince(line: String, since: java.time.Instant): Boolean =
    try {
      !java.time.Instant.parse(line.substringBefore(' ')).isBefore(since)
    } catch (e: Exception) {
      false
    }

  private suspend fun writeSyncInfo(
    target: File,
    lastSync: String?,
    since: java.time.Instant?,
  ): Boolean {
    val cutoff =
      since?.let { java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault()) }
    val sessions =
      database.dao().getAllSyncSessionsAsList().filter { session ->
        cutoff == null ||
          try {
            !java.time.LocalDateTime.parse(
                session.startTime,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
              )
              .isBefore(cutoff)
          } catch (e: Exception) {
            true
          }
      }
    target.bufferedWriter().use { out ->
      out.appendLine("Sync Session Information")
      out.appendLine("========================")
      out.appendLine()
      out.appendLine("FHIR Server URL: ${ServerSettings.fhirBaseUrl(context)}")
      lastSync?.let { out.appendLine("Last sync: $it") }
      out.appendLine()
      if (sessions.isEmpty()) out.appendLine("No Sync Info Available")
      sessions.forEach { session ->
        out.appendLine("Start Time: ${session.startTime}")
        out.appendLine(
          "Downloaded Resources: ${session.downloadedPatients}/${session.totalPatientsToDownload}",
        )
        out.appendLine(
          "Uploaded Resources: ${session.uploadedPatients}/${session.totalPatientsToUpload}",
        )
        out.appendLine("Completion Time: ${session.completionTime ?: "In Progress"}")
        out.appendLine("Status: ${session.status}")
        if (session.errors.isNotEmpty()) {
          out.appendLine("Errors:")
          session.errors.forEach { out.appendLine("- $it") }
        }
        out.appendLine()
      }
    }
    return true
  }

  /** Facts about the device, the app and its setup. No secrets, no patient data. */
  private suspend fun writeDeviceInfo(target: File): Boolean {
    val config = AppConfig.get(context)
    val prefs: Preferences = context.dataStore.data.first()
    val store = DemoDataStore(context)
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val stats = StatFs(context.filesDir.path)
    val memory =
      ActivityManager.MemoryInfo().also {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
      }
    val stored = fhirEngine.search<Questionnaire> {}.map { it.resource }
    target.bufferedWriter().use { out ->
      fun section(title: String) {
        out.appendLine()
        out.appendLine("== $title ==")
      }
      fun line(name: String, value: Any?) = out.appendLine("$name: ${value ?: "-"}")

      out.appendLine("Device and app information (${timestamp()})")
      section("App")
      line("Package", context.packageName)
      // longVersionCode needs Android 9; before, the version code is a plain int.
      val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          info.longVersionCode
        } else {
          @Suppress("DEPRECATION") info.versionCode.toLong()
        }
      line("Version", "${info.versionName} ($versionCode)")
      line("Debug build", BuildConfig.DEBUG)
      section("Device")
      line("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
      line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      line("Locale", Locale.getDefault().toLanguageTag())
      line("Time zone", TimeZone.getDefault().id)
      line(
        "Airplane mode",
        Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0) == 1,
      )
      line("Network", networkDescription())
      section("Resources")
      line("Free internal storage", "${stats.availableBytes / MEGABYTE} MB")
      line("Memory available", "${memory.availMem / MEGABYTE} of ${memory.totalMem / MEGABYTE} MB")
      line("Low memory", memory.lowMemory)
      listOf("resources.db", "AppDatabase", "app_database").forEach { name ->
        val db = context.getDatabasePath(name)
        if (db.exists()) line("Database $name", "${db.length() / 1024} KB")
      }
      section("Session")
      line("User", AuditLog.user)
      line("Guest session", GuestSession.isActive(context))
      line("Location", prefs[PreferenceKeys.LOCATION_NAME])
      line("Selected patient lists", prefs[PreferenceKeys.SELECTED_PATIENT_LISTS]?.size ?: 0)
      section("Settings")
      line("Theme", ThemeModeStore.get(context))
      line("Color vision", ColorVisionStore.get(context))
      line("Language", AppLanguage.currentTag(context))
      line("Embedded forms only", EmbeddedFormsStore.get(context))
      line("Auto sync", store.getAutoSyncEnabledFlow().first())
      line("Live mode", store.getLiveModeEnabledFlow().first())
      line("Periodic sync delay (min)", store.getPeriodicSyncDelayMinutesFlow().first())
      section("Forms")
      line("Embedded questionnaires", context.bundledQuestionnaireIds().size)
      line("Questionnaires in the database", stored.size)
      line(
        "Newest questionnaire update",
        stored.mapNotNull { it.meta.lastUpdated }.maxOrNull(),
      )
      section("Build configuration")
      line("Server", config.serverUrl)
      line("Auth methods", config.authMethod)
      line("Guest mode", config.guestMode)
      line("Sync location tag", config.sync.locationTag)
      line("Sync cohort type", config.sync.cohortType.ifEmpty { null })
      line("Filter patient lists by location", config.sync.filterPatientListsByLocation)
      line("Registration form", config.registrationForm)
      line("Fetch identifiers", config.fetchIdentifiers)
      line("Audit trail size", config.audit.maxActions)
      appendExitReasons(out)
    }
    return true
  }

  private fun appendExitReasons(out: java.io.BufferedWriter) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
    if (exits.isEmpty()) return
    out.appendLine()
    out.appendLine("== Recent process exits ==")
    exits.forEach {
      out.appendLine(
        "${Date(it.timestamp)}: reason ${it.reason}, status ${it.status}, ${it.description.orEmpty()}",
      )
    }
  }

  private fun networkDescription(): String {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "none"
    val transport =
      listOf(
          NetworkCapabilities.TRANSPORT_WIFI to "wifi",
          NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
          NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
        )
        .firstOrNull { capabilities.hasTransport(it.first) }
        ?.second
        ?: "other"
    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return "$transport, internet ${if (validated) "reachable" else "not confirmed"}"
  }

  private fun timestamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())

  private companion object {
    const val WORK_DIR = "diagnostics-work"
    const val MEGABYTE = 1024L * 1024L
  }
}
