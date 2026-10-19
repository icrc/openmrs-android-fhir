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
package org.openmrs.android.fhir.multiuser

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import com.google.android.fhir.db.LocalChangeResourceReference
import com.google.android.fhir.get
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import com.google.android.fhir.sync.ConflictResolver
import com.google.android.fhir.sync.upload.SyncUploadProgress
import com.google.android.fhir.sync.upload.UploadRequestResult
import com.google.android.fhir.sync.upload.UploadStrategy
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.unidentified.UnidentifiedStore

/**
 * The FHIR engine of the app: the SDK's, with a note taken for the signed-in user whenever a record
 * is created, changed, deleted or downloaded (see [UserDataLedger]). Everything else is the SDK's
 * own, unchanged, so reading and searching cost exactly what they did.
 *
 * It is used wherever the app gets its engine (injection, and the sync workers), so the notes do
 * not depend on which screen or job did the work.
 */
class TrackingFhirEngine(
  private val delegate: FhirEngine,
  private val ledger: UserDataLedger,
  private val unidentified: UnidentifiedStore,
) : FhirEngine by delegate {

  override suspend fun create(vararg resource: Resource): List<String> {
    val held = heldIndexes(resource)
    if (held.isEmpty()) {
      val ids = delegate.create(*resource)
      ledger.created(resource.toList(), ids)
      return ids
    }
    val ids = arrayOfNulls<String>(resource.size)
    held.forEach { index ->
      val record = resource[index]
      if (!record.hasIdElement() || record.idElement.idPart.isNullOrBlank()) {
        record.id = UUID.randomUUID().toString()
      }
      ids[index] = record.idElement.idPart
    }
    saveLocalOnly(held.map { resource[it] })
    val rest = resource.indices.filter { it !in held }
    if (rest.isNotEmpty()) {
      val restRecords = rest.map { resource[it] }
      val restIds = delegate.create(*restRecords.toTypedArray())
      ledger.created(restRecords, restIds)
      rest.forEachIndexed { position, index -> ids[index] = restIds[position] }
    }
    return ids.map { it.orEmpty() }
  }

  override suspend fun update(vararg resource: Resource) {
    val held = heldIndexes(resource)
    if (held.isEmpty()) {
      delegate.update(*resource)
      ledger.changed(resource.toList())
      return
    }
    saveLocalOnly(held.map { resource[it] })
    val rest = resource.indices.filter { it !in held }.map { resource[it] }
    if (rest.isNotEmpty()) {
      delegate.update(*rest.toTypedArray())
      ledger.changed(rest)
    }
  }

  override suspend fun delete(type: ResourceType, id: String) {
    if (unidentified.ids().isNotEmpty()) {
      val record = runCatching { delegate.get(type, id) }.getOrNull()
      if (record != null && record.isAboutUnidentified(unidentified.ids())) {
        // Never uploaded, so there is nothing to delete on the server.
        delegate.purge(type, id, true)
        return
      }
    }
    delegate.delete(type, id)
    ledger.deleted(type, id)
  }

  // ---- "identify patient after": the records of a placeholder patient are never uploaded
  // -----------

  private suspend fun heldIndexes(records: Array<out Resource>): List<Int> {
    val ids = unidentified.ids()
    if (ids.isEmpty()) return emptyList()
    return records.indices.filter { records[it].isAboutUnidentified(ids) }
  }

  /**
   * Saved the way a download is: it is not a local change, so the sync, automatic or not, has
   * nothing to send for it (see UnidentifiedPatients).
   */
  private suspend fun saveLocalOnly(records: List<Resource>) {
    delegate.syncDownload(AcceptLocalConflictResolver) { flowOf(records) }
  }

  private fun Resource.isAboutUnidentified(ids: Set<String>): Boolean =
    when (this) {
      is Patient -> idElement?.idPart in ids
      is Encounter -> subject.pointsAt(ids)
      is Observation -> subject.pointsAt(ids)
      is Condition -> subject.pointsAt(ids)
      else -> false
    }

  private fun Reference?.pointsAt(ids: Set<String>): Boolean =
    this != null && hasReference() && referenceElement.idPart?.let { it in ids } == true

  /**
   * Nothing is sent while records entered without a location (as a guest, or before one was chosen)
   * wait to be completed with it: the server would refuse them (no identifier, no location). The
   * completion runs when the location is chosen, right before the sync.
   */
  override suspend fun syncUpload(
    uploadStrategy: UploadStrategy,
    upload:
      suspend (List<LocalChange>, List<LocalChangeResourceReference>) -> Flow<UploadRequestResult>,
  ): Flow<SyncUploadProgress> =
    if (ledger.holdsUploads()) emptyFlow() else delegate.syncUpload(uploadStrategy, upload)

  override suspend fun syncDownload(
    conflictResolver: ConflictResolver,
    download: suspend () -> Flow<List<Resource>>,
  ) {
    delegate.syncDownload(conflictResolver) { download().onEach { ledger.downloaded(it) } }
  }

  /**
   * What the block does to the engine it is given is noted too: it gets this engine, not the SDK's.
   */
  override suspend fun withTransaction(block: suspend FhirEngine.() -> Unit) {
    delegate.withTransaction { block(this@TrackingFhirEngine) }
  }
}
