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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus

class AdditionalRowComponentsTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun patientPropertyRowShowsHeaderValueAndSyncIcon() {
    composeRule.setContent {
      MaterialTheme { PatientPropertyRow(header = "Age", value = "30", showSyncIcon = true) }
    }

    composeRule.onNodeWithTag("PatientPropertyRow").assertIsDisplayed()
    composeRule.onNodeWithTag("SyncStatusIcon").assertIsDisplayed()
    composeRule.onNodeWithTag("PropertyLabel").assertIsDisplayed()
    composeRule.onNodeWithTag("PropertyValue").assertIsDisplayed()
  }

  @Test
  fun encounterRowHidesSyncIconWhenSynced() {
    composeRule.setContent {
      MaterialTheme {
        EncounterListItemRow(
          encounterType = "Consultation",
          encounterDate = "2024-01-01",
          showSyncIcon = false,
        )
      }
    }

    composeRule.onNodeWithTag("EncounterSyncIcon").assertIsNotDisplayed()
  }

  @Test
  fun unsyncedPatientRowShowsActions() {
    composeRule.setContent {
      MaterialTheme {
        UnsyncedPatientRow(
          name = "Patient A",
          onToggleExpand = {},
          onDownload = {},
          onDelete = {},
          showExpand = true,
          isSynced = false,
        )
      }
    }

    composeRule.onNodeWithTag("PatientName").assertIsDisplayed()
    composeRule.onNodeWithTag("DownloadPatient").assertIsDisplayed()
    composeRule.onNodeWithTag("DeletePatient").assertIsDisplayed()
  }

  @Test
  fun syncSessionRowShowsErrorsWhenPresent() {
    val session =
      SyncSession(
        startTime = "10:00",
        downloadedPatients = 1,
        totalPatientsToDownload = 2,
        uploadedPatients = 1,
        totalPatientsToUpload = 2,
        completionTime = "11:00",
        status = SyncStatus.COMPLETED_WITH_ERRORS,
        errors = listOf("Error 1", "Error 2"),
      )

    composeRule.setContent { MaterialTheme { SyncSessionRow(session = session, onDelete = {}) } }

    composeRule.onNodeWithTag("SyncErrorCount").assertIsDisplayed()
    composeRule.onNodeWithText("- Error 1").assertIsDisplayed()
  }
}
