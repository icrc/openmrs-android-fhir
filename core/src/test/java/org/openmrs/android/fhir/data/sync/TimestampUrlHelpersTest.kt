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
import kotlin.test.assertNull
import org.junit.Test

/**
 * Characterization tests for the URL helpers used by [TimestampBasedDownloadWorkManagerImpl]. They
 * pin how the last-updated timestamp is attached to incremental download requests.
 */
class TimestampUrlHelpersTest {
  private val ts = "2026-10-08T10:28:23.000+00:00"

  @Test
  fun affix_appendsLastUpdatedToUrlWithQuery() {
    assertEquals(
      "Patient?_sort=_lastUpdated&_lastUpdated=gt$ts",
      affixLastUpdatedTimestamp("Patient?_sort=_lastUpdated", ts),
    )
  }

  @Test
  fun affix_startsQueryWhenUrlHasNone() {
    assertEquals("Patient?_lastUpdated=gt$ts", affixLastUpdatedTimestamp("Patient", ts))
  }

  @Test
  fun affix_replacesAnExistingLastUpdatedValue() {
    assertEquals(
      "Patient?_lastUpdated=gt$ts&_sort=_lastUpdated",
      affixLastUpdatedTimestamp(
        "Patient?_lastUpdated=gt2026-06-01T00:00:00.000Z&_sort=_lastUpdated",
        ts,
      ),
    )
  }

  @Test
  fun affix_usesSinceForEverythingOperation() {
    assertEquals(
      "Patient/abc/\$everything?_since=$ts",
      affixLastUpdatedTimestamp("Patient/abc/\$everything", ts),
    )
  }

  @Test
  fun affix_leavesServerPagingUrlsUntouched() {
    val pagingUrl = "Patient?_getpages=xyz&page_token=1&_count=10"
    // A "&page_token" URL was produced by the server; it must not be modified.
    assertEquals(
      "Patient?_getpages=xyz&page_token=1&_count=10",
      affixLastUpdatedTimestamp(pagingUrl, ts),
    )
  }

  @Test
  fun removeSummary_dropsOnlyTheSummaryParameter() {
    assertEquals(
      "Patient?_sort=_lastUpdated&_count=5",
      removeSummaryParameter("Patient?_sort=_lastUpdated&_summary=count&_count=5"),
    )
  }

  @Test
  fun patientListScope_fillsPlaceholderWithSelectedLists() {
    assertEquals(
      "Patient?_has:Group:member:id=g1,g2&_revinclude=Encounter:patient",
      applyPatientListScope(
        "Patient?_has:Group:member:id=&_revinclude=Encounter:patient",
        linkedSetOf("g1", "g2"),
      ),
    )
  }

  @Test
  fun patientListScope_skipsRequestWhenNothingIsSelected() {
    val url = "Patient?_has:Group:member:id=&_revinclude=Encounter:patient"
    // Must never degrade into an unscoped "all patients" download.
    assertNull(applyPatientListScope(url, null))
    assertNull(applyPatientListScope(url, emptySet()))
  }

  @Test
  fun patientListScope_leavesOtherRequestsUntouched() {
    assertEquals(
      "Location?_sort=_lastUpdated",
      applyPatientListScope("Location?_sort=_lastUpdated", null),
    )
  }
}
