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
package org.openmrs.android.fhir.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmrs.android.fhir.patientlists.PatientSummary

class GroupSessionPatientsTest {
  private fun row(
    id: String,
    onDevice: Boolean = true,
    ticked: Boolean = true,
    notSentYet: Boolean = false,
  ) =
    SessionPatient(
      patient = PatientSummary(id, "Patient $id", null, null, null),
      onDevice = onDevice,
      ticked = ticked,
      notSentYet = notSentYet,
    )

  @Test
  fun participants_areTheTickedPatientsOfTheListThatAreHere_andEverybodyAddedAndTicked() {
    val members =
      listOf(
        row("a"),
        row("b", ticked = false),
        row("c", onDevice = false),
      )
    val added = listOf(row("d"), row("e", ticked = false))

    assertThat(sessionParticipants(members, added)).containsExactly("a", "d").inOrder()
  }

  @Test
  fun participants_aPatientIsCountedOnce() {
    assertThat(sessionParticipants(listOf(row("a")), listOf(row("a")))).containsExactly("a")
  }

  @Test
  fun patientsToJoinList_onlyThoseTakingPartAndNotInTheListYet() {
    val added =
      listOf(
        row("new"),
        row("untickedAgain", ticked = false),
        row("already"),
        row("notSent", notSentYet = true),
      )

    val join = patientsToJoinList(added, memberIds = setOf("already"))

    assertThat(join.map { it.id }).containsExactly("new", "notSent").inOrder()
  }

  @Test
  fun patientsToJoinList_nothingAddedMeansNothingJoins() {
    assertThat(patientsToJoinList(emptyList(), emptySet())).isEmpty()
  }

  @Test
  fun allTicked_ignoresPatientsThatAreNotOnTheDevice() {
    val state =
      GroupSessionUiState(
        members = listOf(row("a"), row("far", onDevice = false, ticked = false)),
        added = listOf(row("b")),
      )

    assertThat(state.allTicked).isTrue()
    assertThat(state.copy(added = listOf(row("b", ticked = false))).allTicked).isFalse()
    assertThat(GroupSessionUiState().allTicked).isFalse()
  }
}
