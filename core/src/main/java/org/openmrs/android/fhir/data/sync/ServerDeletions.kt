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
import com.google.android.fhir.SearchResult
import com.google.android.fhir.search.search
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.remote.ApiManager
import timber.log.Timber

/** What the server can say about resources the device holds. Read only. */
interface ServerPresence {
  /**
   * Whether the server says the resource no longer exists (not found or gone): `true`. `false`: it
   * is there. `null`: the answer says nothing (a refusal, a failure).
   */
  suspend fun isGone(type: ResourceType, id: String): Boolean?

  /**
   * Which of [ids] the server has, or `null` when that could not be told for all of them (a
   * failure, or an answer that was cut).
   */
  suspend fun existingIds(type: ResourceType, ids: Collection<String>): Set<String>?
}

/**
 * Removes from the device the encounters (and their observations) that were deleted on the server.
 *
 * A download only brings what exists, and the sync only asks for what changed since the last time:
 * neither can notice that a resource is gone, so an encounter deleted on the server would stay on
 * the device for ever. Two moments look for them:
 * * a patient's record read again from the server ([afterRecordDownload]): what the device holds
 *   for the patient and the server did not return, observations too;
 * * after a sync ([afterSync]): every encounter on the device is asked for in batches, and its
 *   observations go with it.
 *
 * What is absent from an answer is only a suspect. It is removed when the server then says, for
 * that resource alone, that it does not exist (404 or 410), so a partial answer, a search that
 * ignored its filter or a server error never costs a record. Anything waiting for upload on the
 * device is kept, an encounter included when one of its observations waits. If too many suspects
 * turn out to exist, the answers are not trusted and nothing is removed.
 */
class ServerDeletions(private val fhirEngine: FhirEngine, private val server: ServerPresence) {

  /** [downloaded] is the whole record of the patient as the server returned it, never a part. */
  suspend fun afterRecordDownload(patientId: String, downloaded: Collection<Resource>): Int {
    val reference = "Patient/$patientId"
    val encounters = localIds {
      search<Encounter> { filter(Encounter.SUBJECT, { value = reference }) }
    }
    val observations = localIds {
      search<Observation> { filter(Observation.SUBJECT, { value = reference }) }
    }
    val onServer = downloaded.groupBy({ it.resourceType }, { it.idElement.idPart })
    return removeGone(
      encounters - onServer[ResourceType.Encounter].orEmpty().toSet(),
      observations - onServer[ResourceType.Observation].orEmpty().toSet(),
    )
  }

  suspend fun afterSync(): Int {
    val suspects = mutableSetOf<String>()
    for (batch in localIds { search<Encounter> {} }.chunked(BATCH)) {
      val found = server.existingIds(ResourceType.Encounter, batch) ?: return 0
      suspects += batch.toSet() - found
    }
    return removeGone(suspects, emptySet())
  }

  private suspend fun localIds(
    find: suspend FhirEngine.() -> List<SearchResult<out Resource>>,
  ): Set<String> = fhirEngine.find().map { it.resource.idElement.idPart }.toSet()

  private suspend fun hasPendingChanges(type: ResourceType, id: String) =
    fhirEngine.getLocalChanges(type, id).isNotEmpty()

  private suspend fun removeGone(
    encounterSuspects: Set<String>,
    observationSuspects: Set<String>,
  ): Int {
    if (encounterSuspects.isEmpty() && observationSuspects.isEmpty()) return 0
    var falseAlarms = 0
    var asked = 0

    suspend fun confirmed(type: ResourceType, suspects: Set<String>): Set<String>? {
      val gone = mutableSetOf<String>()
      for (id in suspects) {
        // The pending check comes first: what has nothing pending was sent already, so the server's
        // answer that follows is about the same state.
        if (hasPendingChanges(type, id)) continue
        if (asked++ >= MAX_CHECKS) return null
        when (server.isGone(type, id)) {
          true -> gone += id
          false -> if (++falseAlarms > MAX_FALSE_ALARMS) return null
          null -> Unit
        }
      }
      return gone
    }

    val goneEncounters = confirmed(ResourceType.Encounter, encounterSuspects)
    val goneObservations = confirmed(ResourceType.Observation, observationSuspects)
    if (goneEncounters == null || goneObservations == null) {
      Timber.w("The server's answers about deleted records were not trusted: nothing removed")
      return 0
    }
    val observations = goneObservations.toMutableSet()
    val encounters = mutableSetOf<String>()
    for (encounterId in goneEncounters) {
      val own = localIds {
        search<Observation> { filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" }) }
      }
      if (own.any { hasPendingChanges(ResourceType.Observation, it) }) continue
      encounters += encounterId
      observations += own
    }
    var removed = 0
    removed += purge(ResourceType.Observation, observations)
    removed += purge(ResourceType.Encounter, encounters)
    if (removed > 0) Timber.i("Removed %d record(s) deleted on the server", removed)
    return removed
  }

  private suspend fun purge(type: ResourceType, ids: Set<String>): Int {
    if (ids.isEmpty()) return 0
    return try {
      fhirEngine.purge(type, ids)
      ids.size
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Could not remove the %s deleted on the server", type)
      0
    }
  }

  companion object {
    private val running = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * [afterSync] without waiting for it, and surviving the screen that asked. One run at a time; a
     * guest session has nothing from the server to compare.
     */
    fun afterSyncInBackground(context: Context, fhirEngine: FhirEngine, apiManager: ApiManager) {
      if (GuestSession.isActive(context)) return
      scope.launch {
        if (!running.tryLock()) return@launch
        try {
          if (ServerDeletions(fhirEngine, apiManager).afterSync() > 0) LiveSync.notifyUploaded()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not look for records deleted on the server")
        } finally {
          running.unlock()
        }
      }
    }

    private const val BATCH = 40

    private const val MAX_CHECKS = 500

    private const val MAX_FALSE_ALARMS = 20
  }
}
