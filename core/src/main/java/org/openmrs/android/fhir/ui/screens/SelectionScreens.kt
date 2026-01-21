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

import android.util.TypedValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.ui.components.IdentifierTypeListItemRow
import org.openmrs.android.fhir.ui.components.LocationListItemRow
import org.openmrs.android.fhir.ui.components.SelectPatientListItemRow
import org.openmrs.android.fhir.viewmodel.LocationViewModel
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

@Composable
fun LocationSelectionScreen(
  query: String,
  onQueryChange: (String) -> Unit,
  favoriteLocations: List<LocationViewModel.LocationItem>,
  locations: List<LocationViewModel.LocationItem>,
  selectedLocationId: String?,
  showTitle: Boolean,
  showActionButton: Boolean,
  showEmptyState: Boolean,
  isLoading: Boolean,
  onLocationClick: (LocationViewModel.LocationItem) -> Unit,
  onFavoriteToggle: (LocationViewModel.LocationItem, Boolean) -> Unit,
  onActionClick: () -> Unit,
  topPadding: Dp = selectionScreenContentPadding().calculateTopPadding(),
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier.fillMaxSize().background(Color(0xFFE8F0FE)).testTag("LocationSelectionScreen"),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = colorResource(id = R.color.white),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        if (showTitle) {
          Text(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            text = stringResource(id = R.string.select_location),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          )
        }

        SelectionSearchField(
          value = query,
          onValueChange = onQueryChange,
          hintResId = R.string.search_a_location,
          modifier = Modifier.testTag("LocationSearchField"),
        )

        if (showEmptyState && !isLoading) {
          SelectionEmptyState(
            iconResId = R.drawable.ic_location,
            messageResId = R.string.no_locations_available,
            modifier = Modifier.testTag("LocationEmptyState"),
          )
        }

        if (favoriteLocations.isNotEmpty()) {
          LazyColumn(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(max = 180.dp)
                .padding(horizontal = 12.dp)
                .testTag("FavoriteLocationList"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(favoriteLocations, key = { it.resourceId }) { location ->
              LocationListItemRow(
                name = location.name,
                isFavorite = true,
                isSelected = location.resourceId == selectedLocationId,
                onFavoriteClick = { onFavoriteToggle(location, true) },
                onClick = { onLocationClick(location) },
              )
            }
          }
        }

        if (favoriteLocations.isNotEmpty() || locations.isNotEmpty()) {
          Divider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            color = colorResource(id = R.color.light_grey),
          )
        }

        LazyColumn(
          modifier =
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).testTag("LocationList"),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          contentPadding = PaddingValues(bottom = 12.dp),
        ) {
          items(locations, key = { it.resourceId }) { location ->
            LocationListItemRow(
              name = location.name,
              isFavorite = false,
              isSelected = location.resourceId == selectedLocationId,
              onFavoriteClick = { onFavoriteToggle(location, false) },
              onClick = { onLocationClick(location) },
            )
          }
        }

        if (showActionButton) {
          Button(
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("LocationActionButton"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.dashboard_cardview_textcolor),
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(4.dp),
            onClick = onActionClick,
          ) {
            Text(text = stringResource(id = R.string.next), fontSize = 12.sp)
          }
        }
      }
    }

    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxSize().testTag("LocationLoading"),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = Color(0xFF4285F4))
      }
    }
  }
}

@Composable
fun IdentifierSelectionScreen(
  query: String,
  onQueryChange: (String) -> Unit,
  identifierTypes: List<IdentifierType>,
  selectedIdentifierIds: Set<String>,
  isLoading: Boolean,
  onIdentifierToggle: (IdentifierType, Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val contentPadding = selectionScreenContentPadding()
  Box(
    modifier = modifier.fillMaxSize().testTag("IdentifierSelectionScreen"),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
      SelectionSearchField(
        value = query,
        onValueChange = onQueryChange,
        hintResId = R.string.search_identifier_types,
        modifier = Modifier.testTag("IdentifierSearchField"),
      )

      LazyColumn(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .background(colorResource(id = R.color.white))
            .padding(horizontal = 12.dp)
            .testTag("IdentifierList"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
      ) {
        items(identifierTypes, key = { it.uuid }) { identifierType ->
          IdentifierTypeListItemRow(
            name = identifierType.display ?: "",
            isRequired = identifierType.required,
            isSelected = selectedIdentifierIds.contains(identifierType.uuid),
            onClick =
              if (identifierType.required) {
                null
              } else {
                {
                  onIdentifierToggle(
                    identifierType,
                    selectedIdentifierIds.contains(identifierType.uuid)
                  )
                }
              },
          )
        }
      }
    }

    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxSize().testTag("IdentifierLoading"),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = Color(0xFF4285F4))
      }
    }
  }
}

@Composable
fun PatientListSelectionScreen(
  query: String,
  onQueryChange: (String) -> Unit,
  patientLists: List<SelectPatientListViewModel.SelectPatientListItem>,
  selectedPatientListIds: Set<String>,
  showTitle: Boolean,
  showActionButton: Boolean,
  showEmptyState: Boolean,
  isLoading: Boolean,
  onPatientListToggle: (SelectPatientListViewModel.SelectPatientListItem, Boolean) -> Unit,
  onActionClick: () -> Unit,
  topPadding: Dp = selectionScreenContentPadding().calculateTopPadding(),
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().testTag("PatientListSelectionScreen"),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
      if (showTitle) {
        Text(
          modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
          text = stringResource(id = R.string.select_patient_list),
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
      }

      SelectionSearchField(
        value = query,
        onValueChange = onQueryChange,
        hintResId = R.string.search_patient_lists,
        modifier = Modifier.testTag("PatientListSearchField"),
      )

      if (showEmptyState && !isLoading) {
        SelectionEmptyState(
          iconResId = R.drawable.cloud_sync_24px,
          messageResId = R.string.no_patient_list_available,
          modifier = Modifier.testTag("PatientListEmptyState"),
        )
      }

      LazyColumn(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .background(colorResource(id = R.color.white))
            .padding(horizontal = 12.dp)
            .testTag("PatientListList"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
      ) {
        items(patientLists, key = { it.resourceId }) { patientList ->
          SelectPatientListItemRow(
            text = patientList.name,
            checked = selectedPatientListIds.contains(patientList.resourceId),
            onToggle = {
              onPatientListToggle(
                patientList,
                selectedPatientListIds.contains(patientList.resourceId)
              )
            },
          )
        }
      }

      if (showActionButton) {
        Button(
          modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("PatientListActionButton"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colorResource(id = R.color.dashboard_cardview_textcolor),
              contentColor = Color.White,
            ),
          shape = RoundedCornerShape(4.dp),
          onClick = onActionClick,
        ) {
          Text(text = stringResource(id = R.string.submit), fontSize = 12.sp)
        }
      }
    }

    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxSize().testTag("PatientListLoading"),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = Color(0xFF4285F4))
      }
    }
  }
}

@Composable
private fun SelectionSearchField(
  value: String,
  onValueChange: (String) -> Unit,
  hintResId: Int,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
    placeholder = {
      Text(
        text = stringResource(id = hintResId),
        style = MaterialTheme.typography.bodyMedium,
        color = colorResource(id = R.color.outline_neutral_variant_60),
      )
    },
    trailingIcon = {
      androidx.compose.material3.Icon(
        painter = painterResource(id = R.drawable.ic_home_search),
        contentDescription = null,
        tint = colorResource(id = R.color.outline_neutral_variant_60),
      )
    },
    shape = RoundedCornerShape(8.dp),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colorResource(id = R.color.outline_neutral_variant_60),
        unfocusedBorderColor = colorResource(id = R.color.outline_neutral_variant_60),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
      ),
    singleLine = true,
  )
}

@Composable
internal fun selectionScreenContentPadding(): PaddingValues {
  val context = LocalContext.current
  val density = LocalDensity.current
  val actionBarHeightPx = remember { resolveActionBarHeightPx(context) }
  val actionBarHeightDp = with(density) { actionBarHeightPx.toDp() }
  val topInset =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + actionBarHeightDp + 8.dp
  return PaddingValues(
    start = 16.dp,
    end = 16.dp,
    bottom = 16.dp,
    top = 16.dp + topInset,
  )
}

private fun resolveActionBarHeightPx(context: android.content.Context): Int {
  val typedValue = TypedValue()
  val resolved =
    context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true) ||
      context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
  return if (resolved) {
    TypedValue.complexToDimensionPixelSize(
      typedValue.data,
      context.resources.displayMetrics,
    )
  } else {
    0
  }
}

@Composable
private fun SelectionEmptyState(
  iconResId: Int,
  messageResId: Int,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(top = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    androidx.compose.material3.Icon(
      modifier = Modifier.size(80.dp),
      painter = painterResource(id = iconResId),
      contentDescription = null,
      tint = colorResource(id = R.color.dashboard_cardview_textcolor),
    )
    Text(
      modifier = Modifier.padding(top = 8.dp),
      text = stringResource(id = messageResId),
      fontSize = 16.sp,
      fontStyle = FontStyle.Italic,
      color = colorResource(id = R.color.black),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(8.dp))
  }
}
