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
package org.openmrs.android.fhir.data

import kotlin.test.assertEquals
import org.junit.Test

class RecentPatientsOrderTest {
  @Test
  fun aPatientTouchedGoesToTheFront() {
    assertEquals(
      listOf("c", "a", "b"),
      RecentPatientsOrder.moveToFront(listOf("a", "b", "c"), listOf("c")),
    )
  }

  @Test
  fun aNewPatientGoesToTheFrontAndNobodyIsListedTwice() {
    val list = RecentPatientsOrder.moveToFront(listOf("a", "b"), listOf("n"))

    assertEquals(listOf("n", "a", "b"), list)
    assertEquals(list.size, list.toSet().size)
  }

  @Test
  fun severalTouchedAtOnceKeepTheirOrderAtTheFront() {
    assertEquals(
      listOf("x", "y", "a", "b"),
      RecentPatientsOrder.moveToFront(listOf("a", "b", "x"), listOf("x", "y")),
    )
  }

  @Test
  fun theListIsCutAtTheCap() {
    val current = (1..20).map { "p$it" }

    val list = RecentPatientsOrder.moveToFront(current, listOf("new"), cap = 20)

    assertEquals(20, list.size)
    assertEquals("new", list.first())
    assertEquals("p19", list.last())
  }

  @Test
  fun blankIdsAreIgnored() {
    assertEquals(listOf("a"), RecentPatientsOrder.moveToFront(listOf("a"), listOf("", " ")).take(1))
    assertEquals(listOf("a"), RecentPatientsOrder.moveToFront(listOf("a"), listOf("")))
  }

  @Test
  fun theListSurvivesBeingWrittenAndReadBack() {
    val ids = listOf("0f8fad5b-d9cb-469f-a165-70867728950e", "7c9e6679-7425-40de-944b-e07fc1f90ae7")

    assertEquals(ids, RecentPatientsOrder.parse(RecentPatientsOrder.write(ids)))
  }

  @Test
  fun nothingStoredIsAnEmptyList() {
    assertEquals(emptyList(), RecentPatientsOrder.parse(null))
    assertEquals(emptyList(), RecentPatientsOrder.parse(""))
  }
}
