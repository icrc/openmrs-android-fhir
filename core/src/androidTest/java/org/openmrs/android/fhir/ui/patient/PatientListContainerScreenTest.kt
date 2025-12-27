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
import org.junit.Rule
import org.junit.Test
import org.openmrs.android.fhir.ui.components.ListContainerTestTags
import org.openmrs.android.fhir.ui.components.PatientListContainerScreen

class PatientListContainerScreenTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun showsEmptyStateWhenPatientsListIsEmpty() {
    composeRule.setContent {
      MaterialTheme {
        PatientListContainerScreen(
          patients = emptyList(),
          isLoading = false,
          isRefreshing = false,
          onRefresh = {},
        ) {}
      }
    }

    composeRule.onNodeWithTag(ListContainerTestTags.EmptyState).assertIsDisplayed()
  }

  @Test
  fun showsLoadingIndicatorWhenLoading() {
    composeRule.setContent {
      MaterialTheme {
        PatientListContainerScreen(
          patients = emptyList(),
          isLoading = true,
          isRefreshing = false,
          onRefresh = {},
        ) {}
      }
    }

    composeRule.onNodeWithTag(ListContainerTestTags.LoadingIndicator).assertIsDisplayed()
  }

  @Test
  fun showsRefreshIndicatorWhenRefreshing() {
    composeRule.setContent {
      MaterialTheme {
        PatientListContainerScreen(
          patients = emptyList(),
          isLoading = false,
          isRefreshing = true,
          onRefresh = {},
        ) {}
      }
    }

    composeRule.onNodeWithTag(ListContainerTestTags.RefreshIndicator).assertIsDisplayed()
  }
}
