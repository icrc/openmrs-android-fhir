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
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import com.google.android.fhir.db.LocalChangeResourceReference
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import com.google.android.fhir.sync.upload.SyncUploadProgress
import com.google.android.fhir.sync.upload.UploadRequestResult
import com.google.android.fhir.sync.upload.UploadStrategy
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.data.remote.ApiManager
import timber.log.Timber

/**
 * The app's engine, with one difference in what it uploads: a change to a visit that is already on
 * the server (closing it, reopening it) is sent as a PUT of the whole visit.
 *
 * Every other update is a PATCH, but the server does not find a visit when it is patched
 * ("Encounter with ID ... is not known", 404) while it accepts the same visit in a PUT. So the
 * visit is sent here, right before the rest is handed to the SDK, and then replaced on the device
 * by what was sent, the way a download stores it, so nothing about it is left waiting to be sent. A
 * visit that was created on the device and has not been sent yet is left to the SDK: it goes as a
 * POST with its changes already in it.
 */
class VisitUploadingEngine(private val delegate: FhirEngine, context: Context) :
  FhirEngine by delegate {
  private val appContext = context.applicationContext
  private val api by lazy { ApiManager(appContext) }

  override suspend fun syncUpload(
    uploadStrategy: UploadStrategy,
    upload:
      suspend (List<LocalChange>, List<LocalChangeResourceReference>) -> Flow<UploadRequestResult>,
  ): Flow<SyncUploadProgress> =
    delegate.syncUpload(uploadStrategy) { changes, references ->
      val visits = visitsToPut(changes)
      var failure: IOException? = null
      visits.forEach { visit ->
        try {
          put(visit)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not send the visit")
          AuditLog.record(
            AuditAction.SYNC,
            AuditOutcome.FAILURE,
            "state" to "visit_update_failed",
            "visit" to visit.idElement.idPart,
          )
          failure = failure ?: IOException("Could not send the visit: ${e.message}", e)
        }
      }
      if (visits.isNotEmpty()) LiveSync.notifyUploaded()
      val rest = changes.filterNot { it.resourceType == ENCOUNTER && it.resourceId in visits.ids() }
      val results = if (rest.isEmpty()) emptyFlow() else upload(rest, references)
      // The sync fails as it would for the SDK's own upload, after everything else was sent.
      results.onCompletion { cause -> if (cause == null) failure?.let { throw it } }
    }

  /** The visits with changes to send as updates: on the server already, nothing else pending. */
  private suspend fun visitsToPut(changes: List<LocalChange>): List<Encounter> {
    val byEncounter = changes.filter { it.resourceType == ENCOUNTER }.groupBy { it.resourceId }
    return byEncounter
      .filterValues { own -> own.all { it.type == LocalChange.Type.UPDATE } }
      .keys
      .mapNotNull { id ->
        try {
          (delegate.get(ResourceType.Encounter, id) as? Encounter)?.takeIf { it.isVisit() }
        } catch (e: Exception) {
          null
        }
      }
  }

  private suspend fun put(visit: Encounter) {
    api.putResource(visit)
    val id = visit.idElement.idPart
    // Sent: what waits for it goes, and the visit is stored as it was sent.
    delegate.purge(ResourceType.Encounter, id, true)
    delegate.syncDownload(AcceptLocalConflictResolver) { flowOf(listOf(visit)) }
    AuditLog.record(
      AuditAction.SYNC,
      AuditOutcome.SUCCESS,
      "state" to "visit_updated",
      "visit" to id,
    )
  }

  private fun List<Encounter>.ids(): Set<String> = map { it.idElement.idPart }.toSet()

  private fun Encounter.isVisit(): Boolean =
    type.any { type -> type.coding.any { it.system == Constants.VISIT_TYPE_CODE_SYSTEM } }

  private companion object {
    const val ENCOUNTER = "Encounter"
  }
}
