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
package org.openmrs.android.fhir.fragments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openmrs.android.fhir.ui.patient.PatientSelectionUiState
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class PatientSelectionDialogFragmentTest {

  @Test
  fun toggleSelection_addsAndRemovesIds() {
    val firstToggle = emptySet<String>().toggleSelection("id-1")
    assertTrue(firstToggle.contains("id-1"))

    val secondToggle = firstToggle.toggleSelection("id-1")
    assertTrue(secondToggle.isEmpty())
  }

  @Test
  fun uiStateReflectsSelectionAndStartState() {
    val patients = listOf(samplePatient("one"), samplePatient("two"))

    val initialState = PatientSelectionUiState(patients = patients, selectedIds = emptySet())
    assertTrue(initialState.isSelectAllEnabled)
    assertFalse(initialState.isSelectAllChecked)
    assertFalse(initialState.isStartEnabled)

    val fullySelectedState =
      PatientSelectionUiState(patients = patients, selectedIds = setOf("one", "two"))
    assertTrue(fullySelectedState.isSelectAllChecked)
    assertTrue(fullySelectedState.isStartEnabled)
  }

  private fun samplePatient(resourceId: String): PatientListViewModel.PatientItem {
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
