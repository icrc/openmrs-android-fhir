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
package org.openmrs.android.fhir.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Identifier
import org.junit.Rule
import org.junit.Test
import org.openmrs.android.fhir.R

class PatientDetailsComponentsTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun patientDetailsHeaderShowsNameAndIdentifiers() {
    val identifier =
      Identifier().apply {
        type = CodeableConcept().apply { text = "MRN" }
        value = "12345"
      }
    val filteredIdentifier =
      Identifier().apply {
        type = CodeableConcept().apply { text = "unsynced" }
        value = "ignore"
      }

    composeRule.setContent {
      MaterialTheme {
        PatientDetailsOverviewHeader(
          name = "Alice Kazadi",
          identifiers = listOf(identifier, filteredIdentifier),
        )
      }
    }

    composeRule.onNodeWithTag("PatientDetailsOverviewHeader").assertIsDisplayed()
    composeRule.onNodeWithText("Alice Kazadi").assertIsDisplayed()
    composeRule.onNodeWithText("MRN: 12345").assertIsDisplayed()
    composeRule.onNodeWithText("ignore").assertDoesNotExist()
  }

  @Test
  fun unsyncedCardRendersCopy() {
    val cardText = composeRule.activity.getString(R.string.patient_unsynced_info)

    composeRule.setContent { MaterialTheme { PatientUnsyncedCard() } }

    composeRule.onNodeWithTag("PatientUnsyncedCard").assertIsDisplayed()
    composeRule.onNodeWithText(cardText).assertIsDisplayed()
  }

  @Test
  fun encounterSyncIconUpdatesWhenStateChanges() {
    val showSyncIcon = mutableStateOf(true)

    composeRule.setContent {
      MaterialTheme {
        EncounterListItemRow(
          encounterType = "Consultation",
          encounterDate = "2024-01-01",
          showSyncIcon = showSyncIcon.value,
        )
      }
    }

    composeRule.onNodeWithTag("EncounterSyncIcon").assertIsDisplayed()

    composeRule.runOnUiThread { showSyncIcon.value = false }
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("EncounterSyncIcon").assertIsNotDisplayed()
  }
}
