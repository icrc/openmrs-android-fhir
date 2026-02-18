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

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.viewmodel.IdentifierSelectionViewModel
import org.openmrs.android.fhir.viewmodel.LocationViewModel
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

class SelectionScreensTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun locationSelectionFiltersResults() {
    composeRule.setContent {
      MaterialTheme {
        var uiState by remember {
          mutableStateOf(
            LocationViewModel.LocationSelectionUiState(
              query = "",
              favoriteLocationIds = setOf("loc-1"),
            ),
          )
        }
        val locations =
          listOf(
            LocationViewModel.LocationItem("1", "loc-1", "active", "Alpha Clinic", ""),
            LocationViewModel.LocationItem("2", "loc-2", "active", "Beta Clinic", ""),
          )
        val favoriteLocations =
          locations.filter {
            uiState.favoriteLocationIds.contains(it.resourceId) &&
              it.name.contains(uiState.query, true)
          }
        val nonFavoriteLocations =
          locations.filter {
            !uiState.favoriteLocationIds.contains(it.resourceId) &&
              it.name.contains(uiState.query, true)
          }

        LocationSelectionScreen(
          query = uiState.query,
          onQueryChange = { uiState = uiState.copy(query = it) },
          favoriteLocations = favoriteLocations,
          locations = nonFavoriteLocations,
          selectedLocationId = uiState.selectedLocationId,
          showTitle = true,
          showActionButton = false,
          showEmptyState = false,
          isLoading = false,
          onLocationClick = {},
          onFavoriteToggle = { _, _ -> },
          onActionClick = {},
        )
      }
    }

    composeRule.onNodeWithTag("LocationSearchField").performTextInput("Beta")
    composeRule.onNodeWithText("Beta Clinic").assertIsDisplayed()
    composeRule.onNodeWithText("Alpha Clinic").assertDoesNotExist()
  }

  @Test
  fun patientListSelectionUpdatesCheckedState() {
    composeRule.setContent {
      MaterialTheme {
        var uiState by remember {
          mutableStateOf(
            SelectPatientListViewModel.SelectPatientListUiState(
              selectedPatientListIds = emptySet(),
            ),
          )
        }
        val patientLists =
          listOf(
            SelectPatientListViewModel.SelectPatientListItem("1", "group-1", "Group A"),
            SelectPatientListViewModel.SelectPatientListItem("2", "group-2", "Group B"),
          )

        PatientListSelectionScreen(
          query = uiState.query,
          onQueryChange = { uiState = uiState.copy(query = it) },
          patientLists = patientLists,
          selectedPatientListIds = uiState.selectedPatientListIds,
          showTitle = false,
          showActionButton = false,
          showEmptyState = false,
          isLoading = false,
          onPatientListToggle = { item, isSelected ->
            val updated =
              if (isSelected) {
                uiState.selectedPatientListIds - item.resourceId
              } else {
                uiState.selectedPatientListIds + item.resourceId
              }
            uiState = uiState.copy(selectedPatientListIds = updated)
          },
          onActionClick = {},
        )
      }
    }

    composeRule.onAllNodesWithTag("SelectPatientCheckbox")[0].performClick()
    composeRule.onAllNodesWithTag("SelectPatientCheckbox")[0].assertIsOn()
  }

  @Test
  fun locationSelectionShowsEmptyState() {
    composeRule.setContent {
      MaterialTheme {
        val uiState = LocationViewModel.LocationSelectionUiState(query = "")
        LocationSelectionScreen(
          query = uiState.query,
          onQueryChange = {},
          favoriteLocations = emptyList(),
          locations = emptyList(),
          selectedLocationId = uiState.selectedLocationId,
          showTitle = true,
          showActionButton = false,
          showEmptyState = true,
          isLoading = false,
          onLocationClick = {},
          onFavoriteToggle = { _, _ -> },
          onActionClick = {},
        )
      }
    }

    composeRule.onNodeWithTag("LocationEmptyState").assertIsDisplayed()
  }

  @Test
  fun identifierSelectionShowsLoading() {
    composeRule.setContent {
      MaterialTheme {
        val uiState =
          IdentifierSelectionViewModel.IdentifierSelectionUiState(
            identifierTypes =
              listOf(
                IdentifierType(
                  uuid = "id-1",
                  display = "National ID",
                  isAutoGenerated = false,
                  required = false,
                  sourceId = "",
                ),
              ),
            isLoading = true,
          )
        IdentifierSelectionScreen(
          query = uiState.query,
          onQueryChange = {},
          identifierTypes = uiState.identifierTypes,
          selectedIdentifierIds = uiState.selectedIdentifierIds,
          isLoading = uiState.isLoading,
          onIdentifierToggle = { _, _ -> },
        )
      }
    }

    composeRule.onNodeWithTag("IdentifierLoading").assertIsDisplayed()
  }
}
