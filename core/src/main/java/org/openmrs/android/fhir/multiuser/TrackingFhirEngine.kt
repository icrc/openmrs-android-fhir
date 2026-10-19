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
import com.google.android.fhir.sync.ConflictResolver
import com.google.android.fhir.sync.upload.SyncUploadProgress
import com.google.android.fhir.sync.upload.UploadRequestResult
import com.google.android.fhir.sync.upload.UploadStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType

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
) : FhirEngine by delegate {

  override suspend fun create(vararg resource: Resource): List<String> {
    val ids = delegate.create(*resource)
    ledger.created(resource.toList(), ids)
    return ids
  }

  override suspend fun update(vararg resource: Resource) {
    delegate.update(*resource)
    ledger.changed(resource.toList())
  }

  override suspend fun delete(type: ResourceType, id: String) {
    delegate.delete(type, id)
    ledger.deleted(type, id)
  }

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
