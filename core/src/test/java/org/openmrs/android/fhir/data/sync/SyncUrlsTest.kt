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

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.hl7.fhir.r4.model.ResourceType
import org.junit.Test

class SyncUrlsTest {

  @Test
  fun history_asksForAPageOfTheDataOfSomePatients() {
    assertEquals(
      "Encounter?patient=a,b&_count=${SyncUrls.HISTORY_PAGE_SIZE}&_sort=_lastUpdated",
      SyncUrls.history(ResourceType.Encounter, listOf("a", "b")),
    )
    assertEquals(
      "Patient?_id=a,b&_count=${SyncUrls.HISTORY_PAGE_SIZE}&_sort=_lastUpdated",
      SyncUrls.history(ResourceType.Patient, listOf("a", "b")),
    )
  }

  @Test
  fun history_sinceIsAfterOrAtTheTimeAsAsked() {
    val after = SyncUrls.history(ResourceType.Observation, listOf("a"), "2026-11-05T10:00:00Z")
    val atOrAfter =
      SyncUrls.history(
        ResourceType.Observation,
        listOf("a"),
        "2026-11-05T10:00:00Z",
        inclusive = true,
      )
    assertTrue(after.endsWith("&_lastUpdated=gt2026-11-05T10:00:00Z"))
    assertTrue(atOrAfter.endsWith("&_lastUpdated=ge2026-11-05T10:00:00Z"))
  }

  @Test
  fun pagesAndBatchesAreSmall() {
    assertTrue(SyncUrls.HISTORY_PAGE_SIZE in 1..100)
    assertTrue(SyncUrls.HISTORY_BATCH_SIZE in 1..25)
    // The ids of a batch make a short URL.
    val ids = List(SyncUrls.KNOWN_BATCH_SIZE) { "a1b2c3d4-0000-4000-8000-%012d".format(it) }
    assertTrue(SyncUrls.history(ResourceType.Observation, ids).length < 2_000)
  }

  @Test
  fun idSearches_keepOnlyTheIdsAndTheListsAreFilledIn() {
    val search = SyncUrls.patientIdSearches(listOf("b", "a")).single()
    assertEquals(
      "Patient?_has:Group:member:id=a,b&_elements=id&_count=${SyncUrls.PATIENT_IDS_PAGE_SIZE}",
      search,
    )
  }

  @Test
  fun idSearches_areNoneWithoutLists() {
    assertTrue(SyncUrls.patientIdSearches(emptyList()).isEmpty())
  }
}
