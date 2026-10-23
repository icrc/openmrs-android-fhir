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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.Period
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.AppSearchField
import org.openmrs.android.fhir.ui.components.LastEncounterLabel
import org.openmrs.android.fhir.ui.components.PatientListContainerScreen
import org.openmrs.android.fhir.ui.components.PatientListItemRow
import org.openmrs.android.fhir.ui.screens.UnidentifiedNoteDialog
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.PatientListUiState
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import org.openmrs.android.fhir.viewmodel.PatientSort

object PatientListScreenTestTags {
  const val Screen = "PatientListScreen"
  const val SearchField = "PatientListSearchField"
  const val EmptyState = "PatientListEmptyState"
  const val EmptyStateMessage = "PatientListEmptyStateMessage"
  const val Fab = "PatientListFab"
}

@Composable
fun PatientListScreen(
  uiState: PatientListUiState,
  onQueryChange: (String) -> Unit,
  onRefresh: () -> Unit,
  onPatientClick: (PatientListViewModel.PatientItem) -> Unit,
  onFabClick: () -> Unit,
  modifier: Modifier = Modifier,
  onServerPatientClick: (PatientListViewModel.PatientItem) -> Unit = {},
  onSortChange: (PatientSort) -> Unit = {},
  onShowMore: () -> Unit = {},
  /** Records for a patient who is not on the device: asked with a note, identified later. */
  onIdentifyLater: ((note: String) -> Unit)? = null,
) {
  var askNote by rememberSaveable { mutableStateOf(false) }
  val topInset = rememberAppBarHeight()
  val surfaceColor = MaterialTheme.colorScheme.background
  Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = surfaceColor,
      // The app bar is measured above (topInset); the system bars are not added a second time.
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      floatingActionButton = {
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (onIdentifyLater != null) {
            ExtendedFloatingActionButton(
              modifier =
                Modifier.testTag("IdentifyLaterFab")
                  .tutorialTarget(TutorialTargets.PATIENT_IDENTIFY_LATER),
              onClick = { askNote = true },
              icon = { Icon(imageVector = Icons.Default.PersonSearch, contentDescription = null) },
              text = { Text(text = stringResource(id = R.string.identify_later_action)) },
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
          ExtendedFloatingActionButton(
            modifier =
              Modifier.testTag(PatientListScreenTestTags.Fab)
                .tutorialTarget(TutorialTargets.PATIENT_REGISTER),
            onClick = onFabClick,
            icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            text = { Text(text = stringResource(id = R.string.register_patient)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          )
        }
      },
    ) { paddingValues ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .background(surfaceColor)
            .padding(paddingValues)
            // The search box and the cards bring their own side margins.
            .padding(top = topInset + 4.dp)
            .testTag(PatientListScreenTestTags.Screen),
      ) {
        PatientSearchField(
          query = uiState.query,
          onQueryChange = onQueryChange,
        )
        PatientSortRow(selected = uiState.sort, onSelected = onSortChange)
        PatientListContainerScreen(
          patients = uiState.patients,
          isLoading = uiState.isLoading,
          isRefreshing = uiState.isRefreshing,
          onRefresh = onRefresh,
          modifier =
            Modifier.weight(1f)
              .background(surfaceColor)
              .tutorialTarget(TutorialTargets.PATIENT_LIST),
          // Room under the last card for the floating button.
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          emptyContent = { PatientListEmptyState() },
          extraRows =
            run {
              val server = serverRows(uiState, onServerPatientClick)
              val footer = showMoreRow(uiState.hasMore, onShowMore)
              if (server == null && footer == null) {
                null
              } else {
                val rows: LazyListScope.() -> Unit = {
                  footer?.invoke(this)
                  server?.invoke(this)
                }
                rows
              }
            },
        ) { patient ->
          PatientListItemRow(
            name = patient.name,
            ageGenderLabel =
              if (patient.unidentified) {
                stringResource(R.string.identify_later_chip)
              } else {
                formatAgeGender(patient)
              },
            // A patient to identify later is never uploaded: no sync marker.
            isSynced = patient.isSynced == true || patient.unidentified,
            identifier = if (patient.unidentified) null else patient.displayIdentifier(),
            lastEncounter = patient.lastEncounter.toLabel(),
            onClick = { onPatientClick(patient) },
          )
        }
      }
    }
  }
  if (askNote && onIdentifyLater != null) {
    UnidentifiedNoteDialog(
      title = stringResource(R.string.identify_note_title),
      initial = uiState.query,
      confirmLabel = stringResource(R.string.identify_note_create),
      onConfirm = {
        askNote = false
        onIdentifyLater(it)
      },
      onDismiss = { askNote = false },
    )
  }
}

/**
 * "On the server": what the live search found that is not already listed above. Null (no section)
 * unless live mode is looking something up.
 */
private fun serverRows(
  uiState: PatientListUiState,
  onClick: (PatientListViewModel.PatientItem) -> Unit,
): (LazyListScope.() -> Unit)? {
  val search = uiState.serverSearch
  if (!search.active) return null
  val onDevice = uiState.patients.mapTo(HashSet()) { it.resourceId }
  val found = search.results.filter { it.resourceId !in onDevice }
  return {
    item(key = "server-header", contentType = "server-header") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          modifier = Modifier.weight(1f, fill = false),
          text = stringResource(R.string.live_server_results_title),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        if (search.loading) {
          CircularProgressIndicator(
            modifier = Modifier.padding(start = 10.dp).size(14.dp),
            strokeWidth = 2.dp,
          )
        }
      }
    }
    items(found, key = { "server-" + it.resourceId }) { patient ->
      PatientListItemRow(
        name = patient.name,
        ageGenderLabel = formatAgeGender(patient),
        isSynced = true,
        identifier = patient.displayIdentifier(),
        onClick = { onClick(patient) },
        onServer = true,
        isOpening = search.openingId == patient.resourceId,
      )
    }
    if (!search.loading && found.isEmpty()) {
      item(key = "server-empty", contentType = "server-empty") {
        Text(
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          text =
            stringResource(
              if (search.failed) {
                R.string.live_server_search_failed
              } else {
                R.string.live_server_no_results
              },
            ),
          style = MaterialTheme.typography.bodyMedium,
          fontStyle = FontStyle.Italic,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** "Show more patients" at the end of a page that does not hold them all. */
private fun showMoreRow(hasMore: Boolean, onClick: () -> Unit): (LazyListScope.() -> Unit)? {
  if (!hasMore) return null
  return {
    item(key = "show-more", contentType = "show-more") {
      OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("PatientListShowMore"),
      ) {
        Text(text = stringResource(R.string.patients_show_more))
      }
    }
  }
}

/** The order of the list, as chips under the search box. */
@Composable
private fun PatientSortRow(selected: PatientSort, onSelected: (PatientSort) -> Unit) {
  // One row that scrolls sideways: four chips would take three lines on a narrow screen.
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .tutorialTarget(TutorialTargets.PATIENT_SORT),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    listOf(
        PatientSort.NAME_ASC to R.string.patient_sort_name_asc,
        PatientSort.NAME_DESC to R.string.patient_sort_name_desc,
        PatientSort.AGE_YOUNGEST to R.string.patient_sort_age_youngest,
        PatientSort.AGE_OLDEST to R.string.patient_sort_age_oldest,
      )
      .forEach { (sort, label) ->
        FilterChip(
          selected = selected == sort,
          onClick = { onSelected(sort) },
          label = { Text(text = stringResource(label)) },
          modifier = Modifier.testTag("PatientSort_${sort.name}"),
        )
      }
  }
}

@Composable
private fun PatientSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  AppSearchField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = stringResource(id = R.string.query_hint_patient_search),
    modifier =
      modifier
        .testTag(PatientListScreenTestTags.SearchField)
        .tutorialTarget(TutorialTargets.PATIENT_SEARCH),
  )
}

@Composable
private fun PatientListEmptyState(
  modifier: Modifier = Modifier,
) {
  Box(
    // Room under the middle for the floating buttons, so the message stays clear of them.
    modifier =
      modifier
        .fillMaxSize()
        .padding(start = 32.dp, end = 32.dp, bottom = 120.dp)
        .testTag(PatientListScreenTestTags.EmptyState),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier =
          Modifier.size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          modifier = Modifier.size(44.dp),
          painter = painterResource(id = R.drawable.ic_home_new_patient),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Text(
        modifier =
          Modifier.padding(top = 20.dp).testTag(PatientListScreenTestTags.EmptyStateMessage),
        text =
          stringResource(
            id = R.string.no_patients_available_register_a_new_one_using_the_button_below,
          ),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun PatientListViewModel.LastEncounter?.toLabel(): LastEncounterLabel =
  when {
    this == null -> LastEncounterLabel.Loading
    !exists -> LastEncounterLabel.None
    else -> LastEncounterLabel.Value(name, date)
  }

/** First real identifier of the patient; the "unsynced" marker used while syncing is skipped. */
internal fun PatientListViewModel.PatientItem.displayIdentifier(): String? =
  identifiers.map { it.value }.firstOrNull { !it.isNullOrBlank() && it != "unsynced" }

/** "34 y, F" style label. Safe for patients with no birth date and/or no gender. */
@Composable
internal fun formatAgeGender(patientItem: PatientListViewModel.PatientItem): String {
  val age =
    patientItem.dob?.let { dob ->
      val period = Period.between(dob, LocalDate.now())
      when {
        period.years > 0 -> stringResource(R.string.age_years_short, period.years)
        period.months > 0 -> stringResource(R.string.age_months_short, period.months)
        else -> stringResource(R.string.age_days_short, period.days.coerceAtLeast(0))
      }
    }
  val gender = patientItem.gender.firstOrNull()?.uppercase()
  return listOfNotNull(age, gender).joinToString(", ")
}
