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

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import com.google.android.fhir.LocalChangeToken
import com.google.android.fhir.SearchResult
import com.google.android.fhir.search.Search
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ServerDeletionsTest {
  private val localEncounters = mutableListOf<String>()
  private val localObservations = mutableListOf<String>()
  private val pending = mutableSetOf<String>()
  private val purged = mutableMapOf<ResourceType, Set<String>>()

  /** A device holding [localEncounters] and [localObservations]; the search ignores its filters. */
  private val engine: FhirEngine = mock {
    on { search<Resource>(any<Search>()) }
      .thenAnswer { call ->
        when ((call.arguments[0] as Search).type) {
          ResourceType.Encounter -> localEncounters.map { result(Encounter().apply { id = it }) }
          ResourceType.Observation ->
            localObservations.map { result(Observation().apply { id = it }) }
          else -> emptyList()
        }
      }
    on { getLocalChanges(any(), any()) }
      .thenAnswer { call ->
        val id = call.arguments[1] as String
        if (id in pending) {
          listOf(
            LocalChange(
              "Encounter",
              id,
              null,
              Instant.now(),
              LocalChange.Type.UPDATE,
              "{}",
              LocalChangeToken(listOf(1L)),
            ),
          )
        } else {
          emptyList()
        }
      }
    on { purge(any(), any<Set<String>>(), any()) }
      .thenAnswer { call ->
        purged[call.arguments[0] as ResourceType] =
          @Suppress("UNCHECKED_CAST") (call.arguments[1] as Set<String>)
        Unit
      }
  }

  private fun result(resource: Resource) = SearchResult(resource, null, null)

  /** What the server says: the resources not listed are gone unless [answer] says otherwise. */
  private class Server(
    var gone: Map<String, Boolean?> = emptyMap(),
    var existing: Set<String>? = emptySet(),
  ) : ServerPresence {
    val asked = mutableListOf<String>()

    override suspend fun isGone(type: ResourceType, id: String): Boolean? {
      asked += id
      return gone[id]
    }

    override suspend fun existingIds(type: ResourceType, ids: Collection<String>) = existing
  }

  private fun record(vararg ids: String): List<Resource> =
    listOf<Resource>(Patient().apply { id = "p1" }) + ids.map { Encounter().apply { id = it } }

  @Test
  fun anEncounterTheServerNoLongerHasIsRemovedWithItsObservations() = runBlocking {
    localEncounters += listOf("e1", "e2")
    localObservations += "o1"
    val server = Server(gone = mapOf("e2" to true))

    val removed = ServerDeletions(engine, server).afterRecordDownload("p1", record("e1"))

    assertEquals(2, removed)
    assertEquals(setOf("e2"), purged[ResourceType.Encounter])
    assertEquals(setOf("o1"), purged[ResourceType.Observation])
    assertEquals(listOf("o1"), server.asked.filter { it == "o1" })
  }

  @Test
  fun whatTheServerStillHasIsNotAsked() = runBlocking {
    localEncounters += "e1"
    val server = Server()

    ServerDeletions(engine, server).afterRecordDownload("p1", record("e1"))

    assertEquals(emptyList<String>(), server.asked.filter { it == "e1" })
    verify(engine, never()).purge(any(), any<Set<String>>(), any())
  }

  @Test
  fun anEncounterWaitingForUploadIsKeptAndNotAsked() = runBlocking {
    localEncounters += "e2"
    pending += "e2"
    val server = Server(gone = mapOf("e2" to true))

    ServerDeletions(engine, server).afterRecordDownload("p1", record())

    assertEquals(emptyList<String>(), server.asked)
    verify(engine, never()).purge(any(), any<Set<String>>(), any())
  }

  @Test
  fun anObservationWaitingForUploadKeepsItsDeletedEncounter() = runBlocking {
    localEncounters += "e2"
    localObservations += "o1"
    pending += "o1"
    val server = Server(gone = mapOf("e2" to true))

    ServerDeletions(engine, server).afterRecordDownload("p1", record())

    verify(engine, never()).purge(eq(ResourceType.Encounter), any<Set<String>>(), any())
  }

  @Test
  fun aRefusalOrAFailureOfTheServerRemovesNothing() = runBlocking {
    localEncounters += listOf("e1", "e2")
    val server = Server(gone = mapOf("e1" to null, "e2" to false))

    val removed = ServerDeletions(engine, server).afterRecordDownload("p1", record())

    assertEquals(0, removed)
    verify(engine, never()).purge(any(), any<Set<String>>(), any())
  }

  @Test
  fun aSearchThatNamesManyExistingRecordsAsMissingIsNotTrusted() = runBlocking {
    val ids = (1..30).map { "e$it" }
    localEncounters += ids
    // The server has all of them but one, which is really gone.
    val server = Server(gone = ids.associateWith { false } + ("e30" to true))

    val removed = ServerDeletions(engine, server).afterRecordDownload("p1", record())

    assertEquals(0, removed)
    verify(engine, never()).purge(any(), any<Set<String>>(), any())
  }

  @Test
  fun afterASyncTheEncountersTheServerLacksAreRemoved() = runBlocking {
    localEncounters += listOf("e1", "e2")
    val server = Server(gone = mapOf("e2" to true), existing = setOf("e1"))

    val removed = ServerDeletions(engine, server).afterSync()

    assertEquals(1, removed)
    assertEquals(setOf("e2"), purged[ResourceType.Encounter])
  }

  @Test
  fun afterASyncNothingIsRemovedWhenTheServerCouldNotSay() = runBlocking {
    localEncounters += listOf("e1", "e2")
    val server = Server(gone = mapOf("e1" to true, "e2" to true), existing = null)

    val removed = ServerDeletions(engine, server).afterSync()

    assertEquals(0, removed)
    verify(engine, never()).purge(any(), any<Set<String>>(), any())
  }
}
