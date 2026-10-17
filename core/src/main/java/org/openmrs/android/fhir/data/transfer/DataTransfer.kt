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
package org.openmrs.android.fhir.data.transfer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.search.search
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import timber.log.Timber

/** What was written by [DataTransfer.export]. */
data class ExportResult(val file: File, val items: Int, val sizeBytes: Long)

/** What [DataTransfer.import] did with the items of a file. */
data class ImportResult(val added: Int, val updated: Int, val skipped: Int)

/**
 * Moves the data that could not be synced from one device to another: the resources with changes
 * not yet uploaded are written to one compressed, PIN-protected file ([TransferCrypto]); importing
 * it stores them as local changes of the other device, and its next sync uploads them like anything
 * entered there.
 *
 * The file holds a FHIR `Bundle` of type `transaction`; each entry carries the whole current
 * resource and says in `request.method` whether it is new (`POST`) or an edit of a resource the
 * server already has (`PUT`).
 */
class DataTransfer
@Inject
constructor(
  private val context: Context,
  private val fhirEngine: FhirEngine,
) {
  private val parser = FhirContext.forR4Cached().newJsonParser()

  /** A resource with changes that were not uploaded yet. */
  private data class Pending(val type: ResourceType, val id: String, val isNew: Boolean)

  /** How many resources are waiting to be uploaded. */
  suspend fun pendingCount(): Int = withContext(Dispatchers.IO) { pendingChanges().size }

  /**
   * Writes every resource with changes not uploaded yet to [target], one JSON object per line
   * (`method`, `url`, `resource`), a resource at a time so a lot of data costs little memory.
   * Returns how many were written.
   */
  suspend fun writeUnsynced(target: File): Int =
    withContext(Dispatchers.IO) {
      var written = 0
      target.bufferedWriter().use { out ->
        pendingChanges()
          .sortedBy { dependencyOrder(it.type) }
          .forEach { pending ->
            val resource =
              try {
                fhirEngine.get(pending.type, pending.id)
              } catch (e: ResourceNotFoundException) {
                return@forEach
              }
            out.append("{\"method\":\"")
            out.append(if (pending.isNew) "POST" else "PUT")
            out.append("\",\"url\":\"${pending.type.name}/${pending.id}\",\"resource\":")
            out.append(parser.encodeResourceToString(resource))
            out.append("}")
            out.newLine()
            written++
          }
      }
      written
    }

  suspend fun export(pin: String): ExportResult =
    withContext(Dispatchers.IO) {
      val bundle =
        Bundle().apply {
          type = Bundle.BundleType.TRANSACTION
          id = UUID.randomUUID().toString()
          timestamp = Date()
        }
      pendingChanges()
        .sortedBy { dependencyOrder(it.type) }
        .forEach { pending ->
          val resource =
            try {
              fhirEngine.get(pending.type, pending.id)
            } catch (e: ResourceNotFoundException) {
              return@forEach // changed and then removed again
            }
          bundle.addEntry().apply {
            this.resource = resource
            request.method = if (pending.isNew) Bundle.HTTPVerb.POST else Bundle.HTTPVerb.PUT
            request.url = "${pending.type.name}/${pending.id}"
          }
        }
      val sealed =
        withContext(Dispatchers.Default) {
          TransferCrypto.encrypt(gzip(parser.encodeResourceToString(bundle)), pin)
        }
      val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
      dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
      val name =
        "openmrs-export-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.omrs"
      val file = File(dir, name).apply { writeBytes(sealed) }
      AuditLog.record(AuditAction.DATA_EXPORTED, AuditOutcome.SUCCESS, "items" to bundle.entry.size)
      ExportResult(file, bundle.entry.size, file.length())
    }

  /**
   * @throws InvalidTransferFileException if this is not an export file
   * @throws WrongPinException if the PIN does not open it
   */
  suspend fun import(file: ByteArray, pin: String): ImportResult =
    withContext(Dispatchers.Default) {
      val json = gunzip(TransferCrypto.decrypt(file, pin))
      val bundle =
        try {
          parser.parseResource(Bundle::class.java, json)
        } catch (e: Exception) {
          throw InvalidTransferFileException()
        }
      withContext(Dispatchers.IO) { apply(bundle) }
        .also {
          AuditLog.record(
            AuditAction.DATA_IMPORTED,
            AuditOutcome.SUCCESS,
            "added" to it.added,
            "updated" to it.updated,
            "skipped" to it.skipped,
          )
        }
    }

  /** New resources are created, edits are applied on top of the copy this device has. */
  private suspend fun apply(bundle: Bundle): ImportResult {
    var added = 0
    var updated = 0
    var skipped = 0
    val entries =
      bundle.entry
        .filter { it.hasResource() }
        .sortedBy { dependencyOrder(it.resource.resourceType) }
    fhirEngine.withTransaction {
      entries.forEach { entry ->
        val resource = entry.resource
        val local = findLocal(resource)
        when {
          entry.request.method == Bundle.HTTPVerb.PUT && local != null -> {
            update(resource)
            updated++
          }
          entry.request.method == Bundle.HTTPVerb.POST && local == null -> {
            create(resource)
            added++
          }
          // Already here (the file was imported before) or an edit whose original this device has
          // not downloaded: nothing can safely be done with it.
          else -> skipped++
        }
      }
    }
    return ImportResult(added, updated, skipped)
  }

  private suspend fun findLocal(resource: Resource): Resource? =
    try {
      fhirEngine.get(resource.resourceType, resource.idElement.idPart)
    } catch (e: ResourceNotFoundException) {
      null
    }

  /**
   * Every resource with a change that has not been uploaded. The engine keeps them in one table;
   * reading it directly is one cheap query where asking the engine resource by resource is slow. If
   * the table is not as expected the engine's own API is used.
   */
  private suspend fun pendingChanges(): List<Pending> =
    try {
      readLocalChangeTable()
    } catch (e: Exception) {
      Timber.w(e, "Reading the local changes directly failed, asking the engine instead")
      askEngineForLocalChanges()
    }

  private fun readLocalChangeTable(): List<Pending> {
    val path = context.getDatabasePath(ENGINE_DATABASE).path
    val changes = LinkedHashMap<Pair<ResourceType, String>, Boolean?>() // value: new? null: deleted
    SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
      db
        .rawQuery("SELECT resourceType, resourceId, type FROM LocalChangeEntity ORDER BY id", null)
        .use { cursor ->
          while (cursor.moveToNext()) {
            val key = ResourceType.fromCode(cursor.getString(0)) to cursor.getString(1)
            when (cursor.getInt(2)) {
              CHANGE_INSERT -> changes[key] = true
              CHANGE_UPDATE -> if (changes[key] == null && key !in changes) changes[key] = false
              CHANGE_DELETE -> changes[key] = null
            }
          }
        }
    }
    return changes.mapNotNull { (key, isNew) -> isNew?.let { Pending(key.first, key.second, it) } }
  }

  private suspend fun askEngineForLocalChanges(): List<Pending> {
    val pending = mutableListOf<Pending>()
    suspend fun check(type: ResourceType, id: String) {
      val changes = fhirEngine.getLocalChanges(type, id)
      if (changes.isEmpty() || changes.last().type == LocalChange.Type.DELETE) return
      pending += Pending(type, id, changes.any { it.type == LocalChange.Type.INSERT })
    }
    fhirEngine
      .search<Patient> {}
      .forEach { check(ResourceType.Patient, it.resource.idElement.idPart) }
    fhirEngine
      .search<Encounter> {}
      .forEach { check(ResourceType.Encounter, it.resource.idElement.idPart) }
    fhirEngine
      .search<Observation> {}
      .forEach { check(ResourceType.Observation, it.resource.idElement.idPart) }
    return pending
  }

  /** Patients before the encounters that refer to them, those before their observations. */
  private fun dependencyOrder(type: ResourceType): Int =
    when (type) {
      ResourceType.Patient,
      ResourceType.Person,
      ResourceType.Practitioner,
      ResourceType.Location, -> 0
      ResourceType.Encounter -> 1
      ResourceType.Observation -> 3
      else -> 2
    }

  private fun gzip(text: String): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    return out.toByteArray()
  }

  private fun gunzip(bytes: ByteArray): String =
    try {
      GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: java.io.IOException) {
      throw InvalidTransferFileException()
    }

  companion object {
    /** Folder of the cache the export is written to; it is shared from there. */
    const val EXPORT_DIR = "exports"

    /** The largest file the import will read (a file from this app is a few megabytes at most). */
    const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

    private const val ENGINE_DATABASE = "resources.db"
    private const val CHANGE_INSERT = 1
    private const val CHANGE_UPDATE = 2
    private const val CHANGE_DELETE = 3
  }
}
