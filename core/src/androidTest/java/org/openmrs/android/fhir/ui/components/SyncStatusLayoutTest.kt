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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmrs.android.fhir.R

@RunWith(AndroidJUnit4::class)
class SyncStatusLayoutTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun showsSyncingState() {
    composeRule.setContent {
      SyncStatusLayout(state = SyncStatusUiState.Syncing(completed = 50, total = 100))
    }

    composeRule.onNodeWithText("50%").assertIsDisplayed()
    composeRule
      .onNodeWithContentDescription(
        composeRule.activity.getString(R.string.syncing),
      )
      .assertIsDisplayed()
  }

  @Test
  fun showsSuccessState() {
    val message = composeRule.activity.getString(R.string.sync_completed)
    composeRule.setContent { SyncStatusLayout(state = SyncStatusUiState.Success(message)) }

    composeRule.onNodeWithText(message).assertIsDisplayed()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string._100)).assertIsDisplayed()
  }

  @Test
  fun showsErrorState() {
    val message = composeRule.activity.getString(R.string.sync_failed)
    composeRule.setContent { SyncStatusLayout(state = SyncStatusUiState.Error(message)) }

    composeRule.onNodeWithText(message).assertIsDisplayed()
  }
}
