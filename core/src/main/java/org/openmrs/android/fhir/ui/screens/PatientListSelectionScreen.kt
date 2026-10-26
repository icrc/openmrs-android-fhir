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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.SelectPatientListItemRow
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

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
  isRefreshing: Boolean = false,
  loadFailed: Boolean = false,
  onRetry: (() -> Unit)? = null,
  /** With nothing ticked the action reads "Continue without a list" instead of "Submit". */
  continueWithoutSelection: Boolean = false,
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

      val noLocation = locationName.isNullOrBlank()
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
              text =
                if (noLocation) {
                  stringResource(id = R.string.location_banner_none)
                } else {
                  locationName.orEmpty()
                },
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              // Not a warning: choosing no location is allowed. A quiet grey that follows the
              // theme.
              color =
                if (noLocation) {
                  MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
              fontStyle = if (noLocation) FontStyle.Italic else FontStyle.Normal,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
            TextButton(
              modifier = Modifier.testTag("PatientListChangeLocation"),
              onClick = onChangeLocationClick,
            ) {
              Text(
                text =
                  stringResource(
                    id = if (noLocation) R.string.choose_location else R.string.change_location,
                  ),
              )
            }
          }
        }
      }

      val nothingToShow = showEmptyState && !isLoading
      if (!nothingToShow) {
        SelectionSearchField(
          value = query,
          onValueChange = onQueryChange,
          hintResId = R.string.search_patient_lists,
          modifier =
            Modifier.testTag("PatientListSearchField").tutorialTarget(TutorialTargets.LISTS_SEARCH),
        )
      }

      // Asking the server while there is something to read: a thin line, the lists stay.
      if (isRefreshing && !isLoading && !showEmptyState) {
        LinearProgressIndicator(
          modifier =
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("PatientListRefreshing"),
          color = OpenMrsTheme.colors.progress,
        )
      }
      if (loadFailed && !showEmptyState && !isLoading) {
        PatientListsNotice(
          text = stringResource(id = R.string.patient_lists_failed_cached),
          onRetry = onRetry,
        )
      }

      when {
        isLoading -> PatientListsSkeleton()
        nothingToShow ->
          PatientListsEmptyState(
            failed = loadFailed,
            isRefreshing = isRefreshing,
            locationName = locationName,
            onRetry = onRetry,
            // Takes the room above the action button, which therefore stays at the bottom.
            modifier = Modifier.weight(1f),
          )
        else -> {
          if (patientLists.isEmpty()) {
            Text(
              text = stringResource(id = R.string.patient_lists_no_match, query),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("PatientListNoMatch"),
            )
          }
          LazyColumn(
            modifier =
              Modifier.fillMaxWidth()
                .weight(1f)
                .testTag("PatientListList")
                .tutorialTarget(TutorialTargets.LISTS_LIST),
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
                subtitle =
                  patientList.patientCount?.let {
                    stringResource(id = R.string.patient_list_patient_count, it)
                  },
                checked = checked,
                onToggle = { onPatientListToggle(patientList, checked) },
                modifier = Modifier.animateItem(),
              )
            }
          }
        }
      }

      if (showActionButton) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
          Button(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 52.dp)
                .testTag("PatientListActionButton"),
            shape = RoundedCornerShape(16.dp),
            onClick = onActionClick,
          ) {
            Text(
              text =
                stringResource(
                  id =
                    if (continueWithoutSelection && selectedPatientListIds.isEmpty()) {
                      R.string.patient_lists_continue_without
                    } else {
                      R.string.submit
                    },
                ),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

/** Placeholders shaped like the rows, pulsing softly, until the first lists are known. */
@Composable
private fun PatientListsSkeleton(modifier: Modifier = Modifier) {
  val pulse by
    rememberInfiniteTransition(label = "patient-lists-skeleton")
      .animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
      )
  val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f * pulse)
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("PatientListLoading"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
          .size(width = 96.dp, height = 12.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(block),
    )
    repeat(6) { index ->
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(block))
          Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Box(
              modifier =
                Modifier.fillMaxWidth(if (index % 2 == 0) 0.62f else 0.45f)
                  .height(14.dp)
                  .clip(RoundedCornerShape(7.dp))
                  .background(block),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier =
                Modifier.fillMaxWidth(0.25f)
                  .height(10.dp)
                  .clip(RoundedCornerShape(5.dp))
                  .background(block),
            )
          }
          Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(block))
        }
      }
    }
  }
}

/**
 * Nothing to pick: either the server has no lists for the location (say so, name the location,
 * offer another location or a new look) or it could not be reached (say that, offer to try again).
 */
@Composable
private fun PatientListsEmptyState(
  failed: Boolean,
  isRefreshing: Boolean,
  locationName: String?,
  onRetry: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 32.dp)
        .padding(top = 48.dp)
        .testTag("PatientListEmptyState"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        Modifier.size(88.dp).clip(CircleShape).background(OpenMrsTheme.colors.highlightContainer),
      contentAlignment = Alignment.Center,
    ) {
      if (failed) {
        androidx.compose.material3.Icon(
          modifier = Modifier.size(44.dp),
          imageVector = Icons.Outlined.CloudOff,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      } else {
        androidx.compose.material3.Icon(
          modifier = Modifier.size(44.dp),
          painter = painterResource(id = R.drawable.cloud_sync_24px),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
    Text(
      modifier = Modifier.padding(top = 20.dp),
      text =
        stringResource(
          id =
            if (failed) R.string.patient_lists_failed_title else R.string.patient_lists_empty_title,
        ),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      modifier = Modifier.padding(top = 8.dp),
      text =
        when {
          failed -> stringResource(id = R.string.patient_lists_failed_message)
          !locationName.isNullOrBlank() ->
            stringResource(id = R.string.patient_lists_empty_for_location, locationName)
          else -> stringResource(id = R.string.patient_lists_empty_no_location)
        },
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Without a location there is nothing to look again for: the location is chosen above.
    if (onRetry != null && !locationName.isNullOrBlank()) {
      Button(
        modifier = Modifier.padding(top = 24.dp).testTag("PatientListRetry"),
        shape = RoundedCornerShape(14.dp),
        enabled = !isRefreshing,
        onClick = onRetry,
      ) {
        if (isRefreshing) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = stringResource(id = R.string.patient_lists_try_again))
      }
    }
  }
}

/** A line above the lists when the server could not be reached and the saved lists are shown. */
@Composable
private fun PatientListsNotice(text: String, onRetry: (() -> Unit)?) {
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .testTag("PatientListNotice"),
    shape = RoundedCornerShape(12.dp),
    color = OpenMrsTheme.colors.warning.copy(alpha = 0.14f),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        modifier = Modifier.weight(1f),
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (onRetry != null) {
        TextButton(onClick = onRetry) {
          Text(text = stringResource(id = R.string.patient_lists_try_again))
        }
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
