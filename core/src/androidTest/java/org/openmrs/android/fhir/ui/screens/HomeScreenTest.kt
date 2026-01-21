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
package org.openmrs.android.fhir.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun showsAllDashboardCards() {
    composeRule.setContent {
      MaterialTheme {
        HomeScreen(
          onNewPatientClicked = {},
          onPatientListClicked = {},
          onCustomPatientListClicked = {},
          onGroupEncounterClicked = {},
          onSyncInfoClicked = {},
          onUnsyncedResourcesClicked = {},
        )
      }
    }

    composeRule.onNodeWithTag(HomeTestTags.NewPatientCard).assertIsDisplayed()
    composeRule.onNodeWithTag(HomeTestTags.PatientListCard).assertIsDisplayed()
    composeRule.onNodeWithTag(HomeTestTags.CustomPatientListCard).assertIsDisplayed()
    composeRule.onNodeWithTag(HomeTestTags.GroupEncounterCard).assertIsDisplayed()
    composeRule.onNodeWithTag(HomeTestTags.SyncInfoCard).assertIsDisplayed()
    composeRule.onNodeWithTag(HomeTestTags.UnsyncedResourcesCard).assertIsDisplayed()
  }

  @Test
  fun dashboardCardsTriggerQuickActions() {
    val triggeredActions = mutableSetOf<String>()

    composeRule.setContent {
      MaterialTheme {
        HomeScreen(
          onNewPatientClicked = { triggeredActions.add(HomeTestTags.NewPatientCard) },
          onPatientListClicked = { triggeredActions.add(HomeTestTags.PatientListCard) },
          onCustomPatientListClicked = { triggeredActions.add(HomeTestTags.CustomPatientListCard) },
          onGroupEncounterClicked = { triggeredActions.add(HomeTestTags.GroupEncounterCard) },
          onSyncInfoClicked = { triggeredActions.add(HomeTestTags.SyncInfoCard) },
          onUnsyncedResourcesClicked = { triggeredActions.add(HomeTestTags.UnsyncedResourcesCard) },
        )
      }
    }

    composeRule.onNodeWithTag(HomeTestTags.NewPatientCard).performClick()
    composeRule.onNodeWithTag(HomeTestTags.PatientListCard).performClick()
    composeRule.onNodeWithTag(HomeTestTags.CustomPatientListCard).performClick()
    composeRule.onNodeWithTag(HomeTestTags.GroupEncounterCard).performClick()
    composeRule.onNodeWithTag(HomeTestTags.SyncInfoCard).performClick()
    composeRule.onNodeWithTag(HomeTestTags.UnsyncedResourcesCard).performClick()

    composeRule.runOnIdle {
      assertTrue(triggeredActions.contains(HomeTestTags.NewPatientCard))
      assertTrue(triggeredActions.contains(HomeTestTags.PatientListCard))
      assertTrue(triggeredActions.contains(HomeTestTags.CustomPatientListCard))
      assertTrue(triggeredActions.contains(HomeTestTags.GroupEncounterCard))
      assertTrue(triggeredActions.contains(HomeTestTags.SyncInfoCard))
      assertTrue(triggeredActions.contains(HomeTestTags.UnsyncedResourcesCard))
    }
  }
}
