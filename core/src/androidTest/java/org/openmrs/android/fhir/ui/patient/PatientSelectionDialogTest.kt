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
package org.openmrs.android.fhir.ui.patient

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openmrs.android.fhir.fragments.toggleSelection
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class PatientSelectionDialogTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val patients =
    listOf(
      samplePatient("one"),
      samplePatient("two"),
    )

  @Test
  fun patientListIsRenderedAndSelectAllEnabled() {
    composeTestRule.setContent {
      MaterialTheme {
        var selectedIds by remember { mutableStateOf(setOf<String>()) }
        PatientSelectionDialogContent(
          patients = patients,
          selectedIds = selectedIds,
          onPatientToggle = { patient ->
            selectedIds = selectedIds.toggleSelection(patient.resourceId)
          },
          onSelectAllToggle = { shouldSelectAll ->
            selectedIds =
              if (shouldSelectAll) patients.map { it.resourceId }.toSet() else emptySet()
          },
          onStartEncounter = {},
          onDismissRequest = {},
        )
      }
    }

    patients.forEach { patient -> composeTestRule.onNodeWithText(patient.name).assertIsDisplayed() }
    composeTestRule.onNodeWithTag("SelectAllCheckbox").assertIsOff()
    composeTestRule.onNodeWithTag("SelectAllCheckbox").performClick()
    composeTestRule.onNodeWithTag("SelectAllCheckbox").assertIsOn()
  }

  @Test
  fun selectingPatientEnablesStartAndFiresCallback() {
    var startClicked = false
    composeTestRule.setContent {
      MaterialTheme {
        var selectedIds by remember { mutableStateOf(setOf<String>()) }
        PatientSelectionDialogContent(
          patients = patients,
          selectedIds = selectedIds,
          onPatientToggle = { patient ->
            selectedIds = selectedIds.toggleSelection(patient.resourceId)
          },
          onSelectAllToggle = { shouldSelectAll ->
            selectedIds =
              if (shouldSelectAll) patients.map { it.resourceId }.toSet() else emptySet()
          },
          onStartEncounter = { startClicked = true },
          onDismissRequest = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("StartEncounterButton").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("PatientRow_one").performClick()
    composeTestRule.onNodeWithTag("StartEncounterButton").assertIsEnabled().performClick()
    assertTrue(startClicked)
  }

  @Test
  fun cancelDismissesDialog() {
    var dismissed = false
    composeTestRule.setContent {
      MaterialTheme {
        PatientSelectionDialogContent(
          patients = patients,
          selectedIds = emptySet(),
          onPatientToggle = {},
          onSelectAllToggle = {},
          onStartEncounter = {},
          onDismissRequest = { dismissed = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("CancelButton").performClick()
    assertTrue(dismissed)
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
