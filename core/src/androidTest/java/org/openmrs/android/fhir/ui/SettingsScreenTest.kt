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
package org.openmrs.android.fhir.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openmrs.android.fhir.ui.screens.SettingsDefaults
import org.openmrs.android.fhir.ui.screens.SettingsScreen
import org.openmrs.android.fhir.ui.screens.SettingsTestTags
import org.openmrs.android.fhir.viewmodel.SettingsUiState

class SettingsScreenTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun togglingNetworkSwitchUpdatesState() {
    var uiState by mutableStateOf(SettingsUiState(isNetworkStatusVisible = false))

    composeRule.setContent {
      MaterialTheme {
        SettingsScreen(
          uiState = uiState,
          tokenDelayOptions = SettingsDefaults.TokenDelayOptions,
          periodicSyncDelayOptions = SettingsDefaults.PeriodicSyncDelayOptions,
          onNetworkStatusToggle = { uiState = uiState.copy(isNetworkStatusVisible = it) },
          onNotificationsToggle = { uiState = uiState.copy(isNotificationsEnabled = it) },
          onTokenDelaySelected = { uiState = uiState.copy(tokenCheckDelayMinutes = it) },
          onPeriodicSyncDelaySelected = { uiState = uiState.copy(periodicSyncDelayMinutes = it) },
          onInitialSyncClicked = {},
          onCancelClicked = {},
          onSaveClicked = {},
        )
      }
    }

    composeRule.onNodeWithTag(SettingsTestTags.NetworkSwitch).performClick()

    assertTrue(uiState.isNetworkStatusVisible)
  }

  @Test
  fun selectingDropdownUpdatesDelays() {
    var uiState by mutableStateOf(SettingsUiState())

    composeRule.setContent {
      MaterialTheme {
        SettingsScreen(
          uiState = uiState,
          tokenDelayOptions = SettingsDefaults.TokenDelayOptions,
          periodicSyncDelayOptions = SettingsDefaults.PeriodicSyncDelayOptions,
          onNetworkStatusToggle = { uiState = uiState.copy(isNetworkStatusVisible = it) },
          onNotificationsToggle = { uiState = uiState.copy(isNotificationsEnabled = it) },
          onTokenDelaySelected = { uiState = uiState.copy(tokenCheckDelayMinutes = it) },
          onPeriodicSyncDelaySelected = { uiState = uiState.copy(periodicSyncDelayMinutes = it) },
          onInitialSyncClicked = {},
          onCancelClicked = {},
          onSaveClicked = {},
        )
      }
    }

    composeRule.onNodeWithTag(SettingsTestTags.TokenDelayField).performClick()
    composeRule.onNodeWithText("5").performClick()

    composeRule.onNodeWithTag(SettingsTestTags.PeriodicDelayField).performClick()
    composeRule.onNodeWithText("30").performClick()

    assertEquals(5, uiState.tokenCheckDelayMinutes)
    assertEquals(30, uiState.periodicSyncDelayMinutes)
  }

  @Test
  fun initialSyncButtonDisabledWhenInProgress() {
    val uiState = SettingsUiState(isInitialSyncInProgress = true)

    composeRule.setContent {
      MaterialTheme {
        SettingsScreen(
          uiState = uiState,
          tokenDelayOptions = SettingsDefaults.TokenDelayOptions,
          periodicSyncDelayOptions = SettingsDefaults.PeriodicSyncDelayOptions,
          onNetworkStatusToggle = {},
          onNotificationsToggle = {},
          onTokenDelaySelected = {},
          onPeriodicSyncDelaySelected = {},
          onInitialSyncClicked = {},
          onCancelClicked = {},
          onSaveClicked = {},
        )
      }
    }

    composeRule.onNodeWithTag(SettingsTestTags.InitialSyncButton).assertIsNotEnabled()
  }
}
