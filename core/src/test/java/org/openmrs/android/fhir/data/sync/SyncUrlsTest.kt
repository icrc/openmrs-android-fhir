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
import org.junit.Test

class SyncUrlsTest {

  @Test
  fun patients_askForAFewPatientsPerPage() {
    assertTrue(SyncUrls.PATIENT_SEARCH.contains("&_count=${SyncUrls.PATIENT_PAGE_SIZE}"))
    assertTrue(SyncUrls.PATIENT_PAGE_SIZE in 1..5)
  }

  @Test
  fun patients_stillIncludeTheEncountersAndObservations() {
    assertTrue(SyncUrls.PATIENT_SEARCH.contains("_revinclude=Encounter:patient"))
    assertTrue(SyncUrls.PATIENT_SEARCH.contains("_revinclude=Observation:patient"))
  }

  @Test
  fun patientLists_arePutInWithoutLosingThePageSize() {
    val url = applyPatientListScope(SyncUrls.PATIENT_SEARCH, setOf("a", "b"))
    assertEquals(
      "Patient?_has:Group:member:id=a,b&_revinclude=Encounter:patient" +
        "&_revinclude=Observation:patient&_sort=_lastUpdated&_count=${SyncUrls.PATIENT_PAGE_SIZE}",
      url,
    )
  }

  @Test
  fun lastUpdated_isAddedAfterThePageSize() {
    val url = affixLastUpdatedTimestamp(SyncUrls.PATIENT_SEARCH, "2026-11-04T16:00:00.000+00:00")
    assertTrue(url.contains("&_count=${SyncUrls.PATIENT_PAGE_SIZE}"))
    assertTrue(url.endsWith("_lastUpdated=gt2026-11-04T16:00:00.000+00:00"))
  }
}
