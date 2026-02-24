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
package org.openmrs.android.fhir.ui.entrypoints

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.SplashActivity

@RunWith(AndroidJUnit4::class)
class SplashActivityTest {

  private val intent =
    Intent(ApplicationProvider.getApplicationContext(), SplashActivity::class.java)
      .putExtra(
        SplashActivity.EXTRA_SKIP_AUTH_FLOW,
        true,
      )

  @get:Rule val activityRule = ActivityScenarioRule<SplashActivity>(intent)

  @get:Rule
  val composeTestRule = AndroidComposeTestRule(activityRule) { currentActivity(activityRule) }

  @Test
  fun splashStatus_updatesProgressAndText() {
    val statusText =
      composeTestRule.activity.getString(R.string.splash_status_checking_connectivity)
    composeTestRule.runOnUiThread {
      composeTestRule.activity.updateSplashStatusForTest(true, statusText)
    }

    composeTestRule.onNodeWithTag("SplashProgress").assertIsDisplayed()
    composeTestRule.onNodeWithTag("SplashStatusText").assertIsDisplayed()
    composeTestRule.onNodeWithText(statusText).assertIsDisplayed()

    composeTestRule.runOnUiThread {
      composeTestRule.activity.updateSplashStatusForTest(false, null)
    }

    composeTestRule.onNodeWithTag("SplashProgress").assertDoesNotExist()
    composeTestRule.onNodeWithTag("SplashStatusText").assertDoesNotExist()
  }
}

private fun currentActivity(rule: ActivityScenarioRule<SplashActivity>): SplashActivity {
  lateinit var activity: SplashActivity
  rule.scenario.onActivity { activity = it }
  return activity
}
