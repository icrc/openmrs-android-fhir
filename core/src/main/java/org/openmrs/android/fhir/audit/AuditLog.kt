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
package org.openmrs.android.fhir.audit

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import org.openmrs.android.fhir.EncryptionHelper
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.extensions.decodeToByteArray
import org.openmrs.android.fhir.extensions.encodeToString

/** What the user did, at the level a support person reads: a verb, not a screen or a tap. */
enum class AuditAction {
  APP_STARTED,
  APP_CRASH,
  LOGIN,
  LOGOUT,
  GUEST_SESSION_STARTED,
  SYNC,
  LOCATION_SELECTED,
  PATIENT_LISTS_CHANGED,
  PATIENT_OPENED,
  PATIENT_CREATED,
  PATIENT_EDITED,
  ENCOUNTER_CREATED,
  ENCOUNTER_EDITED,
  GROUP_SESSION_STARTED,
  GROUP_SESSION_SUBMITTED,
  SETTINGS_CHANGED,
  FORMS_FETCHED,
  DATA_EXPORTED,
  DATA_IMPORTED,
  UNSYNCED_DELETED,
  DIAGNOSTICS_SENT,
}

enum class AuditOutcome {
  SUCCESS,
  FAILURE,
}

/**
 * A short, bounded trail of what the user did, for the support team.
 * - **Structured**: one JSON object per line (`ts` ISO-8601 UTC, `session`, `seq`, `action`,
 *   `outcome`, `user`, `details`), so it can be read by eye or filtered by a script.
 * - **Bounded**: the last `max-actions` entries (`<audit>` in `app_config.xml`) are kept; older
 *   ones are overwritten. The file is compacted only after it grew by a fifth, so a write is almost
 *   always a plain append.
 * - **Cheap**: [record] only puts a line in a queue; one low-priority thread encrypts and writes in
 *   batches. Nothing here runs on, or waits for, the caller's thread.
 * - **Private**: every line is AES-encrypted with the key of the application log, and the file
 *   stays in the app's own storage. Details hold identifiers (a patient's resource id, a form id),
 *   never names, answers, passwords or tokens: callers must keep it that way.
 * - **Safe**: it never throws into the app; a failure to write is logged and dropped.
 */
object AuditLog {
  const val DIRECTORY = "audit"
  private const val FILE_NAME = "audit_log.jsonl"
  private const val USER_PREFS = "audit"
  private const val USER_KEY = "user"
  private const val MAX_VALUE_LENGTH = 200
  private const val MAX_LIST_ITEMS = 20
  private const val CRASH_FRAMES = 15

  private sealed interface Item {
    class Line(val json: String) : Item

    class Flush(val done: java.util.concurrent.CountDownLatch) : Item
  }

  private val queue = LinkedBlockingQueue<Item>()
  private val sequence = AtomicLong()
  private val lock = Any()
  private val sessionId = UUID.randomUUID().toString().take(8)

  @Volatile private var file: File? = null

  @Volatile private var maxActions = 500

  @Volatile private var key: SecretKeySpec? = null

  @Volatile private var userPrefs: android.content.SharedPreferences? = null

  @Volatile private var started = false
  private var lineCount = -1

  /** Who is signed in; set at login, kept across restarts. */
  @Volatile
  var user: String? = null
    private set

  fun init(context: Context) {
    if (started) return
    synchronized(lock) {
      if (started) return
      val app = context.applicationContext
      val config = AppConfig.get(app)
      maxActions = config.audit.maxActions
      key =
        SecretKeySpec(
          MessageDigest.getInstance("SHA-256").digest(config.diagnosticsPassword.toByteArray()),
          "AES",
        )
      file = File(File(app.filesDir, DIRECTORY).apply { mkdirs() }, FILE_NAME)
      userPrefs = app.getSharedPreferences(USER_PREFS, Context.MODE_PRIVATE)
      user = userPrefs?.getString(USER_KEY, null)
      started = true
    }
    Thread(::drain, "audit-log").apply {
      isDaemon = true
      start()
    }
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      // The process is about to die: write this one line now, on this thread.
      writeNow(
        build(
          AuditAction.APP_CRASH,
          AuditOutcome.FAILURE,
          arrayOf(
            "thread" to thread.name,
            "error" to error.javaClass.name,
            "where" to error.stackTrace.firstOrNull()?.toString(),
            // The frames, not the message: messages can quote data.
            "stack" to error.stackTrace.take(CRASH_FRAMES).map { it.toString() },
            "cause" to generateSequence(error.cause) { it.cause }.lastOrNull()?.javaClass?.name,
          ),
        ),
      )
      previous?.uncaughtException(thread, error)
    }
  }

  /** Who the following entries belong to; `null` once they signed out. */
  fun setUser(name: String?) {
    user = name?.takeIf { it.isNotBlank() }
    userPrefs
      ?.edit()
      ?.apply { if (user == null) remove(USER_KEY) else putString(USER_KEY, user) }
      ?.apply()
  }

  /**
   * Records one step. [details] are short facts about it (ids, counts, a method name); `null`
   * values are left out.
   */
  fun record(
    action: AuditAction,
    outcome: AuditOutcome = AuditOutcome.SUCCESS,
    vararg details: Pair<String, Any?>,
  ) {
    if (!started) return
    try {
      queue.offer(Item.Line(build(action, outcome, arrayOf(*details))))
    } catch (e: Exception) {
      Log.w("AuditLog", "Could not record $action", e)
    }
  }

  fun failure(action: AuditAction, vararg details: Pair<String, Any?>) =
    record(action, AuditOutcome.FAILURE, *details)

  /** Writes what is queued, then returns; for the diagnostics, not for the app's hot paths. */
  fun flush() {
    if (!started) return
    val latch = java.util.concurrent.CountDownLatch(1)
    queue.offer(Item.Flush(latch))
    latch.await(3, TimeUnit.SECONDS)
  }

  /** How many entries are kept right now. */
  fun count(): Int {
    val source = file ?: return 0
    return synchronized(lock) {
      if (!source.exists()) 0 else minOf(source.useLines { it.count() }, maxActions)
    }
  }

  /**
   * Copies the readable entries (the last `max-actions`, or those from [since] on) to [target];
   * returns how many.
   */
  fun exportTo(target: File, since: java.time.Instant? = null): Int {
    flush()
    val source = file ?: return 0
    val secret = key ?: return 0
    var written = 0
    synchronized(lock) {
      if (!source.exists()) return 0
      val lines = source.readLines().takeLast(maxActions)
      target.bufferedWriter().use { out ->
        lines.forEach { line ->
          val parts = line.split(":", limit = 2)
          if (parts.size != 2) return@forEach
          try {
            val entry = EncryptionHelper.decrypt(parts[1], secret, parts[0].decodeToByteArray())
            if (since != null && !isSince(entry, since)) return@forEach
            out.appendLine(entry)
            written++
          } catch (e: Exception) {
            Log.w("AuditLog", "Skipping an unreadable entry", e)
          }
        }
      }
    }
    return written
  }

  /** Entries start with `{"ts":"<instant>"`; an entry whose time cannot be read is kept. */
  private fun isSince(entry: String, since: java.time.Instant): Boolean =
    try {
      !java.time.Instant.parse(entry.substring(7, entry.indexOf('"', 7))).isBefore(since)
    } catch (e: Exception) {
      true
    }

  private fun build(
    action: AuditAction,
    outcome: AuditOutcome,
    details: Array<out Pair<String, Any?>>,
  ): String {
    val json =
      JSONObject()
        .put("ts", Instant.now().toString())
        .put("session", sessionId)
        .put("seq", sequence.incrementAndGet())
        .put("action", action.name)
        .put("outcome", outcome.name)
    user?.let { json.put("user", it) }
    val extra = JSONObject()
    details.forEach { (name, value) ->
      when (value) {
        null -> Unit
        is Collection<*> ->
          extra.put(
            name,
            JSONArray(value.take(MAX_LIST_ITEMS).map { it.toString().take(MAX_VALUE_LENGTH) }),
          )
        is Number,
        is Boolean, -> extra.put(name, value)
        else -> extra.put(name, value.toString().take(MAX_VALUE_LENGTH))
      }
    }
    if (extra.length() > 0) json.put("details", extra)
    return json.toString()
  }

  /** The writer: waits for lines, then writes everything that arrived meanwhile in one go. */
  private fun drain() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
    while (true) {
      try {
        val batch = mutableListOf<Item>(queue.take())
        queue.drainTo(batch, 200)
        val lines = batch.filterIsInstance<Item.Line>().map { it.json }
        if (lines.isNotEmpty()) writeLines(lines)
        batch.filterIsInstance<Item.Flush>().forEach { it.done.countDown() }
      } catch (e: InterruptedException) {
        return
      } catch (e: Exception) {
        Log.w("AuditLog", "Writing the audit trail failed", e)
      }
    }
  }

  private fun writeNow(json: String) =
    try {
      writeLines(listOf(json))
    } catch (e: Exception) {
      Log.w("AuditLog", "Could not write the last entry", e)
    }

  private fun writeLines(lines: List<String>) {
    val target = file ?: return
    val secret = key ?: return
    synchronized(lock) {
      try {
        if (lineCount < 0) lineCount = if (target.exists()) target.useLines { it.count() } else 0
        java.io.FileOutputStream(target, true).bufferedWriter().use { out ->
          lines.forEach { line ->
            val (cipher, iv) = EncryptionHelper.encrypt(line, secret)
            out.append(iv.encodeToString()).append(':').append(cipher)
            out.newLine()
          }
        }
        lineCount += lines.size
        if (lineCount > maxActions + maxOf(maxActions / 5, 20)) compact(target)
      } catch (e: IOException) {
        Log.w("AuditLog", "Could not write to the audit file", e)
      }
    }
  }

  /** Keeps the newest [maxActions] lines; the oldest are overwritten. */
  private fun compact(target: File) {
    val keep = target.readLines().takeLast(maxActions)
    val temporary = File(target.parentFile, "$FILE_NAME.tmp")
    temporary.bufferedWriter().use { out ->
      keep.forEach {
        out.append(it)
        out.newLine()
      }
    }
    if (temporary.renameTo(target)) {
      lineCount = keep.size
    } else {
      temporary.delete()
    }
  }
}
