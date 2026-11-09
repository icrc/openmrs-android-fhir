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

import com.google.android.fhir.sync.DownloadWorkManager
import com.google.android.fhir.sync.SyncDataParams
import com.google.android.fhir.sync.download.DownloadRequest
import java.time.Instant
import java.util.Date
import java.util.LinkedList
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType

/**
 * What the download remembers between runs: where the plain searches got to, which patients have
 * their data and up to when, and how far a batch of patients got.
 */
internal interface SyncProgressStore {
  /** The time the search of [type] is complete up to, or null: it starts from the beginning. */
  suspend fun lastUpdated(type: ResourceType): String?

  suspend fun saveLastUpdated(type: ResourceType, value: String)

  /**
   * The patients whose data (the patient, encounters, observations) is on the device, and up to
   * when.
   */
  suspend fun historyDone(): Map<String, String>

  suspend fun markHistoryDone(ids: Collection<String>, upTo: String)

  /** How far the data of a batch of patients got, to go on from there after a cut. */
  suspend fun historyProgress(key: String): String?

  suspend fun saveHistoryProgress(key: String, value: String)

  suspend fun clearHistoryProgress(keys: Collection<String>)
}

/**
 * The download of the sync (and of the patient lists just added), in pieces that are small and that
 * are never lost.
 *
 * The server cannot page what a search includes, so asking for patients with their encounters and
 * observations makes one response with the whole history of each: on a real server a page of ten
 * patients was almost 2 MB, took 25 s to build and, on a phone, was cut before it arrived, again
 * and again. So:
 * 1. [searches] (locations, forms) go as they are;
 * 2. [idSearches] list the patients of the selected lists: ids only, nothing is stored from them;
 * 3. the patients are then taken [SyncUrls.HISTORY_BATCH_SIZE] at a time, and for each batch the
 *    patients, their encounters and their observations are asked for in turn,
 *    [SyncUrls.HISTORY_PAGE_SIZE] resources a page, following `next` ([SyncUrls.history]).
 *
 * Each patient carries the time its data is complete up to. A patient without one gets all of it,
 * first; the others only what changed since (new encounters of a patient who did not change are
 * found too), in larger batches since there is little to fetch. The time moves forward when a batch
 * is complete, so a run that is cut loses at most the batch it was in, and the next run does not go
 * over what was done.
 *
 * Nothing is trusted before it is stored. The SDK stores a page before it asks for the next
 * request, so what is saved here is saved at the start of [getNextRequest]. A request that got no
 * answer is a failure the SDK went on after: its batch is not marked, and the run does not move the
 * times of the plain searches. Within a batch, how far a chain of pages got is saved page by page,
 * so a cut in the middle of a very long history goes on from the last page stored.
 *
 * @param skipKnownPatients leave alone the patients that have their data (the download of added
 *   patient lists, which does not touch what the sync keeps up to date).
 */
internal class PatientDownloadWorkManager(
  private val searches: List<String>,
  private val idSearches: List<String>,
  private val store: SyncProgressStore,
  private val skipKnownPatients: Boolean = false,
  private val now: () -> Date = { Date() },
) : DownloadWorkManager {

  private enum class Kind {
    SEARCH,
    PATIENT_IDS,
    HISTORY,
  }

  private class Batch(val ids: List<String>) {
    val progressKeys = mutableMapOf<ResourceType, String>()
    var openChains = HISTORY_TYPES.size
    var failed = false
  }

  private class Queued(
    val url: String,
    val kind: Kind,
    val first: Boolean,
    val type: ResourceType,
    val batch: Batch? = null,
  )

  private var started = false
  private val queue = LinkedList<Queued>()
  private var awaiting: Queued? = null
  private var cursors = emptyMap<ResourceType, String?>()
  private var done: Map<String, String> = emptyMap()
  private val serverStart = mutableMapOf<ResourceType, Date>()
  private val newestSeen = mutableMapOf<ResourceType, Date>()
  private var historyStart: Date? = null
  private val patientIds = LinkedHashSet<String>()
  private var openIdChains = idSearches.size
  private var planned = false
  private val pendingProgress = mutableMapOf<String, String>()
  private val finishedBatches = mutableListOf<Batch>()
  private var failures = 0
  private var ended = false

  override suspend fun getNextRequest(): DownloadRequest? {
    start()
    awaiting?.let {
      // The SDK asks again without having had an answer for it: the request failed.
      failed(it)
      awaiting = null
    }
    saveProgress()
    planWhenIdsAreIn()
    val next = queue.pollFirst()
    if (next == null) {
      endRun()
      return null
    }
    awaiting = next
    return DownloadRequest.of(urlOf(next))
  }

  override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> {
    val urls = LinkedHashMap<ResourceType, String>()
    for (url in searches) {
      val type = typeOf(url)
      if (type in urls) continue
      val lastUpdated = store.lastUpdated(type)
      var base =
        if (lastUpdated.isNullOrEmpty()) url else affixLastUpdatedTimestamp(url, lastUpdated)
      base = removeSummaryParameter(base)
      urls[type] = base + "&${SyncDataParams.SUMMARY_KEY}=${SyncDataParams.SUMMARY_COUNT_VALUE}"
    }
    // The patients are counted by their ids search (without the part that keeps only the ids).
    idSearches.firstOrNull()?.let {
      urls[ResourceType.Patient] =
        it.replace("&_elements=id", "") +
          "&${SyncDataParams.SUMMARY_KEY}=${SyncDataParams.SUMMARY_COUNT_VALUE}"
    }
    return urls
  }

  override suspend fun processResponse(response: Resource): Collection<Resource> {
    val request = awaiting ?: return emptyList()
    awaiting = null
    return try {
      read(request, response)
    } catch (e: Exception) {
      failed(request)
      throw e
    }
  }

  private fun read(request: Queued, response: Resource): Collection<Resource> {
    // If the search fails (it cannot be executed, not that there are no matches), the answer is a
    // 4xx or 5xx with an OperationOutcome (https://www.hl7.org/fhir/http.html#search).
    if (response is OperationOutcome) throw FHIRException(response.issueFirstRep.diagnostics)
    if (response !is Bundle) return emptyList()

    val next = response.link.firstOrNull { it.relation == "next" }?.url
    val resources =
      if (response.type == Bundle.BundleType.SEARCHSET) {
        response.entry.mapNotNull { it.resource }
      } else {
        emptyList()
      }

    return when (request.kind) {
      Kind.SEARCH -> {
        next?.let { queue.addLast(Queued(it, Kind.SEARCH, false, request.type)) }
        response.meta.lastUpdated?.let { time ->
          serverStart[request.type] = serverStart[request.type]?.let { earlier(it, time) } ?: time
        }
        resources
          .mapNotNull { it.meta?.lastUpdated }
          .maxOrNull()
          ?.let { time ->
            newestSeen[request.type] = newestSeen[request.type]?.let { later(it, time) } ?: time
          }
        resources
      }
      Kind.PATIENT_IDS -> {
        // Only the ids came: storing these would replace the patients with empty ones.
        resources.mapNotNullTo(patientIds) { (it as? Patient)?.idElement?.idPart }
        if (next != null) {
          queue.addFirst(Queued(next, Kind.PATIENT_IDS, false, request.type))
        } else {
          openIdChains--
        }
        emptyList()
      }
      Kind.HISTORY -> {
        val batch = checkNotNull(request.batch)
        response.meta.lastUpdated?.let { time ->
          historyStart = historyStart?.let { earlier(it, time) } ?: time
        }
        resources
          .mapNotNull { it.meta?.lastUpdated }
          .maxOrNull()
          ?.let { pendingProgress[batch.progressKeys.getValue(request.type)] = timeOf(it) }
        if (next != null) {
          queue.addFirst(Queued(next, Kind.HISTORY, false, request.type, batch))
        } else {
          batch.openChains--
          if (batch.openChains == 0 && !batch.failed) finishedBatches += batch
        }
        resources
      }
    }
  }

  /**
   * Once every id is in, queues the batches: the patients that have nothing first (they are what a
   * cut run must get to), then those that only need what changed.
   */
  private suspend fun planWhenIdsAreIn() {
    if (planned || openIdChains > 0 || !started || idSearches.isEmpty()) return
    planned = true
    val fresh = patientIds.filter { it !in done }
    fresh.chunked(SyncUrls.HISTORY_BATCH_SIZE).forEach { queueBatch(it, since = null) }
    if (skipKnownPatients) return
    patientIds
      .filter { it in done }
      .groupBy { done.getValue(it) }
      .toSortedMap()
      .forEach { (since, ids) ->
        ids.chunked(SyncUrls.KNOWN_BATCH_SIZE).forEach { queueBatch(it, since) }
      }
  }

  private suspend fun queueBatch(ids: List<String>, since: String?) {
    val batch = Batch(ids)
    HISTORY_TYPES.forEach { type ->
      val key = progressKey(type, ids)
      batch.progressKeys[type] = key
      // After a cut, from the last page stored (at or after it: not to miss those of the same
      // second).
      val resume = store.historyProgress(key)
      val url =
        if (resume != null) {
          SyncUrls.history(type, ids, resume, inclusive = true)
        } else {
          SyncUrls.history(type, ids, since, inclusive = false)
        }
      queue.addLast(Queued(url, Kind.HISTORY, true, type, batch))
    }
  }

  private suspend fun start() {
    if (started) return
    started = true
    cursors = searches.map { typeOf(it) }.distinct().associateWith { store.lastUpdated(it) }
    done = store.historyDone()
    searches.forEach { queue.add(Queued(it, Kind.SEARCH, true, typeOf(it))) }
    idSearches.forEach { queue.add(Queued(it, Kind.PATIENT_IDS, true, ResourceType.Patient)) }
  }

  private fun urlOf(queued: Queued): String {
    if (queued.kind != Kind.SEARCH || !queued.first) return queued.url
    val cursor = cursors[queued.type]
    return if (cursor.isNullOrEmpty()) queued.url else affixLastUpdatedTimestamp(queued.url, cursor)
  }

  private fun failed(request: Queued) {
    failures++
    request.batch?.failed = true
    // The ids of that search stop where they got to.
    if (request.kind == Kind.PATIENT_IDS) openIdChains--
  }

  /** Saves what the pages stored so far make true. */
  private suspend fun saveProgress() {
    pendingProgress.forEach { (key, value) -> store.saveHistoryProgress(key, value) }
    pendingProgress.clear()
    if (finishedBatches.isNotEmpty()) {
      val upTo = timeOf(Date((historyStart ?: now()).time - SKEW_MILLIS))
      val ids = finishedBatches.flatMap { it.ids }
      store.markHistoryDone(ids, upTo)
      done = done + ids.associateWith { upTo }
      store.clearHistoryProgress(finishedBatches.flatMap { it.progressKeys.values })
      finishedBatches.clear()
    }
  }

  /**
   * Everything was asked for and stored. With no failure the times of the plain searches move
   * forward to when this run started according to the server (a second earlier: it keeps whole
   * seconds and the next run asks for what is after the time), not to the newest resource seen,
   * which could be later than one that was changed meanwhile on a page already read.
   */
  private suspend fun endRun() {
    if (ended) return
    ended = true
    if (failures > 0) return
    searches
      .map { typeOf(it) }
      .distinct()
      .forEach { type ->
        val base = serverStart[type] ?: newestSeen[type] ?: return@forEach
        store.saveLastUpdated(type, timeOf(Date(base.time - SKEW_MILLIS)))
      }
  }

  private fun typeOf(url: String): ResourceType = ResourceType.fromCode(url.substringBefore("?"))

  private fun earlier(a: Date, b: Date) = if (a.before(b)) a else b

  private fun later(a: Date, b: Date) = if (a.after(b)) a else b

  companion object {
    private val HISTORY_TYPES =
      listOf(ResourceType.Patient, ResourceType.Encounter, ResourceType.Observation)
    private const val SKEW_MILLIS = 1_000L

    internal fun timeOf(date: Date): String = Instant.ofEpochMilli(date.time).toString()

    /** Names the progress of a batch: the same patients give the same name in the next run. */
    internal fun progressKey(type: ResourceType, ids: List<String>): String =
      "${type.name}-${ids.size}-${ids.sorted().joinToString(",").hashCode()}"
  }
}
