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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.extensions.rememberActionBarContentPadding
import org.openmrs.android.fhir.ui.components.AppSearchField
import org.openmrs.android.fhir.ui.components.IdentifierTypeListItemRow
import org.openmrs.android.fhir.ui.components.LocationListItemRow
import org.openmrs.android.fhir.ui.components.SelectPatientListItemRow
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.LocationViewModel
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

/**
 * Choose where the user works. The same screen is used right after login (full screen, with a title
 * and a **Next** button) and later from the side menu or the patient list ("change location", under
 * the app bar). Favorites are pinned on top, the rest follows alphabetically; the current location
 * is marked and scrolled into view.
 */
@OptIn(ExperimentalFoundationApi::class)
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
  val listState = rememberLazyListState()
  var scrolledToSelection by rememberSaveable { mutableStateOf(false) }

  // Bring the current location into view once, the first time the list is there.
  LaunchedEffect(selectedLocationId, favoriteLocations.size, locations.size) {
    if (scrolledToSelection || selectedLocationId == null || query.isNotEmpty()) {
      return@LaunchedEffect
    }
    if (favoriteLocations.isEmpty() && locations.isEmpty()) return@LaunchedEffect
    val inFavorites = favoriteLocations.indexOfFirst { it.resourceId == selectedLocationId }
    val inAll = locations.indexOfFirst { it.resourceId == selectedLocationId }
    val index =
      when {
        inFavorites >= 0 -> 1 + inFavorites
        inAll >= 0 ->
          (if (favoriteLocations.isEmpty()) 0 else 1 + favoriteLocations.size) + 1 + inAll
        else -> -1
      }
    if (index > 3) listState.scrollToItem(index - 1)
    scrolledToSelection = true
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("LocationSelectionScreen"),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
      if (showTitle) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 4.dp)) {
          Text(
            text = stringResource(id = R.string.select_location),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("LocationTitle"),
          )
          Text(
            text = stringResource(id = R.string.select_location_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
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
      } else if (!isLoading && favoriteLocations.isEmpty() && locations.isEmpty()) {
        Text(
          text = stringResource(id = R.string.locations_no_match, query),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("LocationNoMatch"),
        )
      }

      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("LocationList"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
      ) {
        if (favoriteLocations.isNotEmpty()) {
          stickyHeader(key = "favorites-header", contentType = "header") {
            LocationSectionHeader(
              title = stringResource(id = R.string.locations_favorites),
              count = favoriteLocations.size,
              starred = true,
              modifier = Modifier.testTag("FavoriteLocationList"),
            )
          }
          items(favoriteLocations, key = { "fav-${it.resourceId}" }, contentType = { "row" }) {
            location ->
            LocationListItemRow(
              name = location.name,
              isFavorite = true,
              isSelected = location.resourceId == selectedLocationId,
              onFavoriteClick = { onFavoriteToggle(location, true) },
              onClick = { onLocationClick(location) },
              modifier = Modifier.animateItem(),
            )
          }
        }

        if (locations.isNotEmpty()) {
          stickyHeader(key = "all-header", contentType = "header") {
            LocationSectionHeader(
              title = stringResource(id = R.string.locations_all),
              count = locations.size,
              starred = false,
            )
          }
          items(locations, key = { it.resourceId }, contentType = { "row" }) { location ->
            LocationListItemRow(
              name = location.name,
              isFavorite = false,
              isSelected = location.resourceId == selectedLocationId,
              onFavoriteClick = { onFavoriteToggle(location, false) },
              onClick = { onLocationClick(location) },
              modifier = Modifier.animateItem(),
            )
          }
        }
      }

      if (showActionButton) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
          Button(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp)
                .testTag("LocationActionButton"),
            shape = RoundedCornerShape(16.dp),
            onClick = onActionClick,
          ) {
            Text(
              text = stringResource(id = R.string.next),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }

    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxSize().testTag("LocationLoading"),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = OpenMrsTheme.colors.progress)
      }
    }
  }
}

/** Pinned label above a group of locations; opaque so rows scroll cleanly underneath it. */
@Composable
private fun LocationSectionHeader(
  title: String,
  count: Int,
  starred: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (starred) {
      androidx.compose.material3.Icon(
        painter = painterResource(id = R.drawable.ic_star_filled),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
    }
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = count.toString(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            .background(MaterialTheme.colorScheme.surface)
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
                    selectedIdentifierIds.contains(identifierType.uuid),
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
        CircularProgressIndicator(color = OpenMrsTheme.colors.progress)
      }
    }
  }
}

/**
 * Choose which patient lists are downloaded. Used right after the location step (full screen, title
 * and **Submit**) and later from the home screen (under the app bar). The location the lists belong
 * to is shown with a shortcut to change it; the selection count is always visible.
 */
@OptIn(ExperimentalFoundationApi::class)
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
  locationName: String? = null,
  onChangeLocationClick: (() -> Unit)? = null,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("PatientListSelectionScreen"),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
      if (showTitle) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 4.dp)) {
          Text(
            text = stringResource(id = R.string.select_patient_list),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("PatientListTitle"),
          )
          Text(
            text = stringResource(id = R.string.select_patient_list_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
      }

      if (onChangeLocationClick != null) {
        Surface(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          shape = RoundedCornerShape(16.dp),
          color = OpenMrsTheme.colors.highlightContainer,
        ) {
          Row(
            modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            androidx.compose.material3.Icon(
              painter = painterResource(id = R.drawable.ic_location),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Text(
              modifier =
                Modifier.weight(1f).padding(horizontal = 10.dp).testTag("PatientListLocationName"),
              text = locationName.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            TextButton(
              modifier = Modifier.testTag("PatientListChangeLocation"),
              onClick = onChangeLocationClick,
            ) {
              Text(text = stringResource(id = R.string.change_location))
            }
          }
        }
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
      } else if (!isLoading && patientLists.isEmpty()) {
        Text(
          text = stringResource(id = R.string.patient_lists_no_match, query),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("PatientListNoMatch"),
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("PatientListList"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
      ) {
        if (patientLists.isNotEmpty()) {
          stickyHeader(key = "lists-header", contentType = "header") {
            PatientListSectionHeader(
              total = patientLists.size,
              selected = selectedPatientListIds.size,
            )
          }
        }
        items(patientLists, key = { it.resourceId }, contentType = { "row" }) { patientList ->
          val checked = selectedPatientListIds.contains(patientList.resourceId)
          SelectPatientListItemRow(
            text = patientList.name,
            checked = checked,
            onToggle = { onPatientListToggle(patientList, checked) },
            modifier = Modifier.animateItem(),
          )
        }
      }

      if (showActionButton) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
          Button(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp)
                .testTag("PatientListActionButton"),
            shape = RoundedCornerShape(16.dp),
            onClick = onActionClick,
          ) {
            Text(
              text = stringResource(id = R.string.submit),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }

    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxSize().testTag("PatientListLoading"),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = OpenMrsTheme.colors.progress)
      }
    }
  }
}

/** Pinned label above the lists: how many there are and how many are ticked. */
@Composable
private fun PatientListSectionHeader(total: Int, selected: Int, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 4.dp, vertical = 8.dp)
        .testTag("PatientListHeader"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(id = R.string.patient_lists_header).uppercase(),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = total.toString(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.weight(1f))
    if (selected > 0) {
      Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
        Text(
          text = stringResource(id = R.string.patient_lists_selected_count, selected),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimary,
          modifier =
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
              .testTag("PatientListSelectedCount"),
        )
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
  AppSearchField(
    value = value,
    onValueChange = onValueChange,
    placeholder = stringResource(id = hintResId),
    modifier = modifier,
  )
}

@Composable
internal fun selectionScreenContentPadding(): PaddingValues =
  rememberActionBarContentPadding(extraTop = 8.dp)

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
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      modifier = Modifier.padding(top = 8.dp),
      text = stringResource(id = messageResId),
      fontSize = 16.sp,
      fontStyle = FontStyle.Italic,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(8.dp))
  }
}
