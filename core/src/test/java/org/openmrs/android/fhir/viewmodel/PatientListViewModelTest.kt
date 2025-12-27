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

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.Patient
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientListViewModelTest {

  @Test
  fun filterSelectedIdsForPatients_removesUnknownIds() {
    val patients =
      listOf(
        samplePatientItem("first"),
        samplePatientItem("second"),
      )

    val filtered = filterSelectedIdsForPatients(patients, setOf("first", "missing"))

    assertEquals(setOf("first"), filtered)
  }

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

  private fun samplePatientItem(resourceId: String): PatientListViewModel.PatientItem {
    return PatientListViewModel.PatientItem(
      id = resourceId,
      resourceId = resourceId,
      name = "Patient $resourceId",
      gender = "",
      dob = null,
      phone = "",
      city = "",
      country = "",
      isActive = true,
      identifiers = emptyList(),
      html = "",
    )
  }
}
