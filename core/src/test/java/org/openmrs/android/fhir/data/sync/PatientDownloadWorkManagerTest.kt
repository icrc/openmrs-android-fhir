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

import com.google.android.fhir.sync.download.UrlDownloadRequest
import java.io.IOException
import java.time.OffsetDateTime
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.junit.Test

/**
 * The download of the sync against a small FHIR server of our own, run the way the SDK runs it: ask
 * for the next request, give the answer, and go on whatever happened to a request.
 */
class PatientDownloadWorkManagerTest {

  private val server = FakeServer()
  private val store = FakeStore()

  @Test
  fun everyResourceOfEveryListedPatientComesAndNothingElse() = runTest {
    server.addList("A", 25)
    server.addList("B", 4)
    server.addPatientsOutsideLists(3)

    val run = download(listOf("A"))

    assertEquals(server.expectedFor("A"), run.storedIds())
    assertEquals(0, run.failures)
  }

  @Test
  fun noResponseHasMoreThanAPageWhateverTheHistory() = runTest {
    server.addList("A", 12)
    server.addObservations("A-1", 230)

    download(listOf("A"))

    assertTrue(server.largestPage <= SyncUrls.HISTORY_PAGE_SIZE)
    assertTrue(server.largestPage > 0)
    assertTrue(server.requests.all { it.length < 1_500 })
    assertTrue(server.patientsPerRequest.all { it <= SyncUrls.HISTORY_BATCH_SIZE })
  }

  @Test
  fun theIdsOfTheSearchAreNotStoredAsPatients() = runTest {
    server.addList("A", 5)

    val run = download(listOf("A"))

    val patients = run.stored.filterIsInstance<Patient>()
    assertEquals(5, patients.size)
    assertTrue(patients.all { it.hasName() })
  }

  @Test
  fun aBatchIsMarkedDoneOnlyWhenEveryPageOfItWasStored() = runTest {
    server.addList("A", 25)
    server.addObservations("A-1", 120)
    server.failWhen = { it == "Observation A-1 page=2" }

    val first = download(listOf("A"))

    assertEquals(1, first.failures)
    assertTrue(store.done.keys.intersect(server.firstBatchOf("A")).isEmpty())
    assertEquals(25 - SyncUrls.HISTORY_BATCH_SIZE, store.done.size)
    assertTrue(store.cursors.isEmpty(), "a run with a failure moved the times forward")

    server.failWhen = { false }
    val second = download(listOf("A"))

    assertEquals(0, second.failures)
    assertEquals(25, store.done.size)
    assertEquals(server.expectedFor("A"), first.storedIds() + second.storedIds())
    // What was done is not downloaded again: only what changed since is asked for.
    val last = server.lastPatientOf("A")
    assertTrue(
      second.requests
        .filter { it.contains(last) && !it.startsWith("Patient?_has") }
        .all { it.contains("_lastUpdated=gt") },
    )
  }

  @Test
  fun aCutGoesOnFromTheLastPageStoredNotFromTheStartOfTheBatch() = runTest {
    server.addList("A", 5)
    server.addObservations("A-1", 190)
    var cut = 1
    var first: Run
    do {
      store.reset()
      first = download(listOf("A"), maxRequests = cut++)
    } while (store.progress.keys.none { it.startsWith("Observation") } && cut < 80)
    assertTrue(store.progress.keys.any { it.startsWith("Observation") }, "nothing was saved")
    assertTrue(store.done.isEmpty())

    val second = download(listOf("A"))

    assertEquals(server.expectedFor("A"), first.storedIds() + second.storedIds())
    assertTrue(
      second.requests.any { it.startsWith("Observation") && it.contains("_lastUpdated=ge") },
      "the batch started again from its first page",
    )
    assertTrue(store.progress.isEmpty(), "the progress of a finished batch was kept")
    assertEquals(5, store.done.size)
  }

  @Test
  fun newDataOfAKnownPatientIsFoundEvenThoughThePatientDidNotChange() = runTest {
    server.addList("A", 12)
    download(listOf("A"))
    server.clock += 60_000
    val added = server.addEncounter("A-3", at = server.clock + 10_000)
    server.clock += 120_000

    val run = download(listOf("A"))

    assertEquals(setOf(added), run.storedIds())
    assertTrue(
      run.requests
        .filter { !it.startsWith("Patient?_has") && !it.startsWith("Location") }
        .all { it.contains("_lastUpdated=gt") },
    )
  }

  @Test
  fun aPatientAddedToAListGetsAllOfTheirDataAndGoesBeforeTheOthers() = runTest {
    server.addList("A", 12)
    download(listOf("A"))
    server.clock += 60_000
    server.addPatientToList("A", "A-new", observations = 70, encounters = 3)

    val run = download(listOf("A"))

    assertEquals(server.idsOfPatient("A-new") + "Patient/A-new", run.storedIds())
    val firstData = run.requests.first { it.startsWith("Patient?_id") }
    assertTrue(firstData.contains("A-new"), "the known patients were checked before the new one")
    assertTrue(!firstData.contains("_lastUpdated="), "a new patient got only what changed")
  }

  @Test
  fun withoutListsNoPatientIsAskedFor() = runTest {
    server.addList("A", 5)

    val run = download(emptyList())

    assertTrue(run.requests.none { it.startsWith("Patient") || it.startsWith("Encounter") })
    assertTrue(run.requests.any { it.startsWith("Location") })
  }

  @Test
  fun theDownloadOfAddedListsLeavesTheKnownPatientsAndTheTimesToTheSync() = runTest {
    server.addList("A", 12)
    download(listOf("A"))
    val timesAfterTheSync = store.cursors.toMap()
    val markedAfterTheSync = store.done.toMap()
    server.addList("B", 6)
    server.addPatientToExistingList("B", "A-1")
    server.clock += 60_000

    val run = download(listOf("B"), forLists = true)

    val withoutKnown = server.expectedFor("B") - server.idsOfPatient("A-1") - "Patient/A-1"
    assertEquals(withoutKnown, run.storedIds())
    assertEquals(timesAfterTheSync, store.cursors)
    assertEquals(markedAfterTheSync[("A-1")], store.done["A-1"])
    assertTrue(store.done.keys.containsAll(server.patientsOf("B")))
  }

  @Test
  fun searchesNeverCarryMoreListsThanTheLimit() {
    val lists = (1..150).map { "list-%03d".format(it) }

    val searches = SyncUrls.patientIdSearches(lists)

    assertTrue(searches.all { it.length < 2_000 })
    val used =
      searches.flatMap { search ->
        search.substringAfter("_has:Group:member:id=").substringBefore("&").split(",")
      }
    assertEquals(lists.sorted(), used.sorted())
  }

  // ----------------------------------------------------------------------------------- helpers

  private class Run(val stored: MutableList<Resource>, val requests: MutableList<String>) {
    var failures = 0

    fun storedIds(): Set<String> =
      stored.map { "${it.resourceType.name}/${it.idElement.idPart}" }.toSet()
  }

  /** Runs the download as the SDK does; [maxRequests] stops it as WorkManager would. */
  private suspend fun download(
    lists: List<String>,
    forLists: Boolean = false,
    maxRequests: Int = Int.MAX_VALUE,
  ): Run {
    val manager =
      PatientDownloadWorkManager(
        searches =
          if (forLists) {
            emptyList()
          } else {
            listOf("Location?_sort=_lastUpdated&_summary=data&_tag=Offline")
          },
        idSearches = SyncUrls.patientIdSearches(lists),
        store = if (forLists) ListStore(store) else store,
        skipKnownPatients = forLists,
        now = { Date(server.clock) },
      )
    val run = Run(mutableListOf(), mutableListOf())
    manager.getSummaryRequestUrls()
    var request = manager.getNextRequest()
    var asked = 0
    while (request != null) {
      val url = (request as UrlDownloadRequest).url
      run.requests += url
      try {
        run.stored += manager.processResponse(server.get(url))
      } catch (e: Exception) {
        run.failures++
      }
      if (++asked >= maxRequests) return run
      request = manager.getNextRequest()
    }
    return run
  }

  /** The store the downloads of added lists use: no times of the plain searches. */
  private class ListStore(base: FakeStore) : SyncProgressStore by base {
    override suspend fun lastUpdated(type: ResourceType): String? = null

    override suspend fun saveLastUpdated(type: ResourceType, value: String) = Unit
  }

  private class FakeStore : SyncProgressStore {
    val cursors = mutableMapOf<ResourceType, String>()
    val done = mutableMapOf<String, String>()
    val progress = mutableMapOf<String, String>()

    fun reset() {
      cursors.clear()
      done.clear()
      progress.clear()
    }

    override suspend fun lastUpdated(type: ResourceType) = cursors[type]

    override suspend fun saveLastUpdated(type: ResourceType, value: String) {
      cursors[type] = value
    }

    override suspend fun historyDone(): Map<String, String> = done.toMap()

    override suspend fun markHistoryDone(ids: Collection<String>, upTo: String) {
      ids.forEach { done[it] = upTo }
    }

    override suspend fun historyProgress(key: String) = progress[key]

    override suspend fun saveHistoryProgress(key: String, value: String) {
      progress[key] = value
    }

    override suspend fun clearHistoryProgress(keys: Collection<String>) {
      keys.forEach { progress.remove(it) }
    }
  }

  private class Item(val id: String, val patient: String, var lastUpdated: Long)

  /** A FHIR server with patients in lists, their encounters and observations, and paging. */
  private class FakeServer {
    var clock = 1_900_000_000_000L
    var failWhen: (String) -> Boolean = { false }
    val requests = mutableListOf<String>()
    var largestPage = 0
    val patientsPerRequest = mutableListOf<Int>()

    private val lists = linkedMapOf<String, MutableList<String>>()
    private val patients = linkedMapOf<String, Long>()
    private val encounters = mutableListOf<Item>()
    private val observations = mutableListOf<Item>()
    private val pages = mutableMapOf<String, Pair<List<Resource>, String>>()
    private var counter = 0

    /** Older and older... no: each one a second after the one before, all long ago. */
    private fun old() = clock - DAY + (counter++) * 1_000L

    fun addList(name: String, size: Int) {
      for (i in 1..size) {
        addPatientToList(name, "$name-$i", observations = (i % 7) * 10, encounters = i % 4)
      }
    }

    fun addPatientToList(list: String, patient: String, observations: Int, encounters: Int) {
      lists.getOrPut(list) { mutableListOf() } += patient
      patients[patient] = old()
      addObservations(patient, observations)
      repeat(encounters) { addEncounter(patient, old()) }
    }

    fun addPatientToExistingList(list: String, patient: String) {
      lists.getValue(list) += patient
    }

    fun addPatientsOutsideLists(count: Int) {
      for (i in 1..count) {
        patients["outside-$i"] = old()
        addObservations("outside-$i", 5)
      }
    }

    fun addObservations(patient: String, count: Int) {
      repeat(count) { observations += Item("o-$patient-${observations.size}", patient, old()) }
    }

    fun addEncounter(patient: String, at: Long): String {
      val item = Item("e-$patient-${encounters.size}", patient, at)
      encounters += item
      return "Encounter/${item.id}"
    }

    fun patientsOf(list: String): List<String> = lists.getValue(list)

    fun expectedFor(list: String): Set<String> =
      lists.getValue(list).flatMap { listOf("Patient/$it") + idsOfPatient(it) }.toSet()

    fun idsOfPatient(patient: String): Set<String> =
      (encounters.filter { it.patient == patient }.map { "Encounter/${it.id}" } +
          observations.filter { it.patient == patient }.map { "Observation/${it.id}" })
        .toSet()

    fun firstBatchOf(list: String): Set<String> =
      lists.getValue(list).take(SyncUrls.HISTORY_BATCH_SIZE).toSet()

    fun lastPatientOf(list: String): String = lists.getValue(list).last()

    fun get(url: String): Bundle {
      requests += url
      return if (url.startsWith("R4?")) nextPage(url) else search(url)
    }

    private fun query(url: String) =
      url.substringAfter("?").split("&").associate {
        it.substringBefore("=") to it.substringAfter("=", "")
      }

    private fun search(url: String): Bundle {
      val type = url.substringBefore("?")
      val params = query(url)
      val since = params["_lastUpdated"]?.let { Since.of(it) }
      val all: List<Resource>
      val described: String
      when {
        type == "Location" || type == "Questionnaire" -> {
          all = emptyList()
          described = type
        }
        type == "Patient" && "_has:Group:member:id" in params -> {
          val inLists =
            params.getValue("_has:Group:member:id").split(",").flatMap { lists[it].orEmpty() }
          all =
            inLists.distinct().sortedBy { patients.getValue(it) }.map { patientResource(it, true) }
          described = "PatientIds"
        }
        type == "Patient" -> {
          val ids = params.getValue("_id").split(",")
          patientsPerRequest += ids.size
          all =
            ids
              .filter { since?.accepts(patients.getValue(it)) ?: true }
              .sortedBy { patients.getValue(it) }
              .map { patientResource(it, false) }
          described = "Patient ${ids.first()}"
        }
        else -> {
          val ids = params.getValue("patient").split(",")
          patientsPerRequest += ids.size
          val items = if (type == "Encounter") encounters else observations
          all =
            items
              .filter { it.patient in ids && (since?.accepts(it.lastUpdated) ?: true) }
              .sortedBy { it.lastUpdated }
              .map { resourceOf(type, it) }
          described = "$type ${ids.first()}"
        }
      }
      return page(all, 0, params["_count"]?.toInt() ?: 10, described, null)
    }

    private fun nextPage(url: String): Bundle {
      val params = query(url)
      val token = params.getValue("_getpages")
      val (all, described) = pages.getValue(token)
      return page(
        all,
        params.getValue("_getpagesoffset").toInt(),
        params.getValue("_count").toInt(),
        described,
        token,
      )
    }

    private fun page(
      all: List<Resource>,
      offset: Int,
      count: Int,
      described: String,
      token: String?,
    ): Bundle {
      val label = "$described page=${offset / count + 1}"
      if (failWhen(label)) throw IOException("cut: $label")
      val slice = all.drop(offset).take(count)
      if (described != "Location" && described != "PatientIds") {
        largestPage = maxOf(largestPage, slice.size)
      }
      return Bundle().apply {
        type = Bundle.BundleType.SEARCHSET
        meta.lastUpdated = Date(clock)
        total = all.size
        slice.forEach { addEntry().resource = it }
        if (offset + count < all.size) {
          val key = token ?: "t${pages.size}".also { pages[it] = all to described }
          addLink().apply {
            relation = "next"
            url =
              "R4?_getpages=$key&_getpagesoffset=${offset + count}&_count=$count&_bundletype=searchset"
          }
        }
      }
    }

    private fun patientResource(id: String, idsOnly: Boolean) =
      Patient().apply {
        this.id = id
        meta.lastUpdated = Date(patients.getValue(id))
        if (!idsOnly) addName().family = "Family $id"
      }

    private fun resourceOf(type: String, item: Item): Resource =
      (if (type == "Encounter") Encounter() else Observation()).also {
        it.id = item.id
        it.meta.lastUpdated = Date(item.lastUpdated)
        val reference = Reference("Patient/${item.patient}")
        if (it is Encounter) it.subject = reference else (it as Observation).subject = reference
      }

    /** `_lastUpdated=gt<time>` or `ge<time>`. */
    private class Since(val at: Long, val inclusive: Boolean) {
      fun accepts(time: Long) = if (inclusive) time >= at else time > at

      companion object {
        fun of(value: String): Since {
          val inclusive = value.startsWith("ge")
          val time = value.drop(2).replace(" ", "+")
          return Since(OffsetDateTime.parse(time).toInstant().toEpochMilli(), inclusive)
        }
      }
    }

    companion object {
      private const val DAY = 86_400_000L
    }
  }
}
