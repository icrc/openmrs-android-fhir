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

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openmrs.android.fhir.ui.components.ListContainerTestTags
import org.openmrs.android.fhir.viewmodel.PatientListUiState
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class PatientListScreenTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun searchFieldUpdatesQuery() {
    var query = ""
    composeRule.setContent {
      MaterialTheme {
        PatientListScreen(
          uiState = PatientListUiState(),
          onQueryChange = { query = it },
          onRefresh = {},
          onPatientClick = {},
          onFabClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(PatientListScreenTestTags.SearchField).performTextInput("Ana")

    composeRule.runOnIdle { assertEquals("Ana", query) }
  }

  @Test
  fun swipeToRefreshTriggersRefresh() {
    var refreshCount = 0
    composeRule.setContent {
      MaterialTheme {
        PatientListScreen(
          uiState = PatientListUiState(patients = listOf(samplePatient())),
          onQueryChange = {},
          onRefresh = { refreshCount += 1 },
          onPatientClick = {},
          onFabClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(ListContainerTestTags.PatientList).performTouchInput { swipeDown() }

    composeRule.runOnIdle { assertTrue(refreshCount >= 1) }
  }

  @Test
  fun emptyStateShowsWhenNoPatients() {
    composeRule.setContent {
      MaterialTheme {
        PatientListScreen(
          uiState = PatientListUiState(),
          onQueryChange = {},
          onRefresh = {},
          onPatientClick = {},
          onFabClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(PatientListScreenTestTags.EmptyState).assertIsDisplayed()
    composeRule.onNodeWithTag(PatientListScreenTestTags.EmptyStateMessage).assertIsDisplayed()
  }

  @Test
  fun fabClickInvokesCallback() {
    var tapped = false
    composeRule.setContent {
      MaterialTheme {
        PatientListScreen(
          uiState = PatientListUiState(patients = listOf(samplePatient())),
          onQueryChange = {},
          onRefresh = {},
          onPatientClick = {},
          onFabClick = { tapped = true },
        )
      }
    }

    composeRule.onNodeWithTag(PatientListScreenTestTags.Fab).performClick()

    composeRule.runOnIdle { assertTrue(tapped) }
  }

  private fun samplePatient(): PatientListViewModel.PatientItem {
    return PatientListViewModel.PatientItem(
      id = "1",
      resourceId = "patient-1",
      name = "Patient One",
      gender = "female",
      dob = LocalDate.now().minusYears(30),
      phone = "555-123",
      city = "Nairobi",
      country = "KE",
      isActive = true,
      identifiers = emptyList(),
      html = "",
    )
  }
}
