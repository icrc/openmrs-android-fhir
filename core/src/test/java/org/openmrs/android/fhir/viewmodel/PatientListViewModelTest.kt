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
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.Patient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.patientlists.PatientListMember
import org.openmrs.android.fhir.patientlists.PatientListState
import org.openmrs.android.fhir.patientlists.SendResult

class PatientListViewModelTest {

  @Test
  fun toPatientItem_mapsCoreFields() {
    val patient =
      Patient().apply {
        id = "patient-1"
        addName().setFamily("Doe").addGiven("Jane")
        gender = Enumerations.AdministrativeGender.FEMALE
        birthDate = Date.from(LocalDate.of(1995, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant())
        addTelecom().value = "555-1234"
        addAddress().city = "Nairobi"
        addressFirstRep.country = "Kenya"
        addIdentifier().value = "identifier-1"
        active = true
      }

    val item = patient.toPatientItem(position = 1)

    assertEquals("patient-1", item.resourceId)
    assertEquals(patient.nameFirstRep.nameAsSingleString, item.name)
    assertEquals("555-1234", item.phone)
    assertEquals("Nairobi", item.city)
    assertEquals("Kenya", item.country)
    assertEquals(true, item.isActive)
    assertEquals(1, item.identifiers.size)
  }

  private fun patient(given: String, family: String, born: LocalDate? = null) =
    Patient().apply {
      addName().setFamily(family).addGiven(given)
      born?.let { birthDate = Date.from(it.atStartOfDay(ZoneOffset.UTC).toInstant()) }
      addIdentifier().value = "ID-$given"
      addTelecom().value = "555-$given"
    }

  @Test
  fun matchesText_everyWordOfTheTextIsInTheName() {
    val jane = patient("Jane", "Doe")

    assertTrue(jane.matchesText("jane doe"))
    assertTrue(jane.matchesText("  doe   jane "))
    assertFalse(jane.matchesText("jane smith"))
  }

  @Test
  fun matchesText_anIdentifierOrAPhoneAlsoMatches() {
    val jane = patient("Jane", "Doe")

    assertTrue(jane.matchesText("id-jane"))
    assertTrue(jane.matchesText("555-Jane"))
    assertFalse(jane.matchesText("ID-"))
  }

  @Test
  fun matchesText_blankMatchesEveryone() {
    assertTrue(patient("Jane", "Doe").matchesText("   "))
  }

  @Test
  fun patientOrder_byNameAndByAge() {
    val ann = patient("Ann", "A", LocalDate.of(2000, 1, 1))
    val bob = patient("bob", "B", LocalDate.of(1990, 1, 1))
    val cy = patient("Cy", "C")
    val all = listOf(cy, bob, ann)

    assertEquals(listOf(ann, bob, cy), all.sortedWith(patientOrder(PatientSort.NAME_ASC)))
    assertEquals(listOf(cy, bob, ann), all.sortedWith(patientOrder(PatientSort.NAME_DESC)))
    assertEquals(listOf(bob, ann, cy), all.sortedWith(patientOrder(PatientSort.AGE_OLDEST)))
  }

  private fun patientList(name: String, description: String? = null, location: String? = null) =
    PatientListEntry(
      id = "c",
      name = name,
      description = description,
      typeName = null,
      locationId = null,
      locationName = location,
      creatorName = null,
      dateCreated = null,
      changedByName = null,
      dateChanged = null,
      quantity = 0,
      attributes = emptyList(),
      state = PatientListState.SYNCED,
      pendingChanges = 0,
      error = null,
      membersLoaded = true,
    )

  @Test
  fun everyWordOfTheSearchMustMatchTheNameTheDescriptionOrTheLocation() {
    val item = patientList("Morning clinic", "weekday follow up", "North")

    assertThat(item.matches("")).isTrue()
    assertThat(item.matches("morn clin")).isTrue()
    assertThat(item.matches("north follow")).isTrue()
    assertThat(item.matches("morning evening")).isFalse()
  }

  @Test
  fun aMemberIsFoundByNameOrIdentifier() {
    val member = PatientListMember("p1", "Amara Okafor", "female", null, "DEMO-0001000", null, null)

    assertThat(member.matches("amara oka")).isTrue()
    assertThat(member.matches("0001000")).isTrue()
    assertThat(member.matches("daniel")).isFalse()
  }

  @Test
  fun aSendIsToldAccordingToWhatHappened() {
    assertThat(SendResult(unreachable = true).toMessage().res)
      .isEqualTo(R.string.plists_send_unreachable)
    assertThat(SendResult(sent = 2, refused = 1).toMessage().res)
      .isEqualTo(R.plurals.plists_refused)
    assertThat(SendResult(dropped = 3).toMessage())
      .isEqualTo(PatientListMessage(R.plurals.plists_dropped, 3, plural = true, isError = true))
    assertThat(SendResult(sent = 4).toMessage())
      .isEqualTo(PatientListMessage(R.plurals.plists_sent, 4, plural = true))
    assertThat(SendResult().toMessage().res).isEqualTo(R.string.plists_send_nothing)
  }
}
