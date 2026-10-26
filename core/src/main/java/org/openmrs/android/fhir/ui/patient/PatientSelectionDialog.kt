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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.AppSearchField
import org.openmrs.android.fhir.ui.components.PatientAvatar
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

/** A search box only helps once there are enough patients to scroll through. */
private const val SEARCH_THRESHOLD = 8

/**
 * The patients of a group session, picked in a rounded sheet: how many are picked so far, a search
 * box for long lists, one "all" row with a three-state checkbox, a row per patient (avatar, age and
 * gender, identifier) that highlights when picked, and Cancel / Start (n).
 */
@Composable
fun PatientSelectionDialogContent(
  patients: List<PatientListViewModel.PatientItem>,
  selectedIds: Set<String>,
  onPatientToggle: (PatientListViewModel.PatientItem) -> Unit,
  onSelectAllToggle: (Boolean) -> Unit,
  onStartEncounter: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState =
    remember(patients, selectedIds) {
      PatientSelectionUiState(patients = patients, selectedIds = selectedIds)
    }
  var query by rememberSaveable { mutableStateOf("") }
  // Filtering is done once per change of the list or the text, not on every recomposition.
  val visible =
    remember(patients, query) {
      if (query.isBlank()) {
        patients
      } else {
        patients.filter { patient ->
          patient.name.contains(query, ignoreCase = true) ||
            patient.displayIdentifier()?.contains(query, ignoreCase = true) == true
        }
      }
    }

  Surface(
    modifier = modifier.fillMaxWidth().testTag("PatientSelectionDialog"),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(top = 24.dp)) {
      Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
          text = stringResource(R.string.select_patients_for_group_encounter),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          modifier = Modifier.padding(top = 4.dp).testTag("PatientSelectionCount"),
          text =
            stringResource(
              R.string.patients_selected_of,
              selectedIds.size,
              patients.size,
            ),
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (selectedIds.isEmpty()) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.primary
            },
        )
      }

      if (patients.size > SEARCH_THRESHOLD) {
        AppSearchField(
          value = query,
          onValueChange = { query = it },
          placeholder = stringResource(R.string.search_patients_hint),
          modifier = Modifier.padding(top = 8.dp).testTag("PatientSelectionSearch"),
        )
      }

      SelectAllRow(uiState = uiState, onSelectAllToggle = onSelectAllToggle)

      if (patients.isEmpty()) {
        Text(
          modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("PatientSelectionEmpty"),
          text = stringResource(R.string.no_patients_to_select),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("PatientSelectionList"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        items(visible, key = { it.resourceId }, contentType = { "patient" }) { patient ->
          SelectablePatientRow(
            patient = patient,
            isSelected = patient.resourceId in selectedIds,
            onToggle = { onPatientToggle(patient) },
            modifier = Modifier.padding(horizontal = 12.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          modifier = Modifier.testTag("CancelButton"),
          onClick = onDismissRequest,
        ) {
          Text(text = stringResource(R.string.cancel))
        }
        Button(
          modifier = Modifier.testTag("StartEncounterButton"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = OpenMrsTheme.colors.actionButton,
              contentColor = OpenMrsTheme.colors.onActionButton,
            ),
          enabled = uiState.isStartEnabled,
          onClick = onStartEncounter,
        ) {
          Text(
            text =
              if (selectedIds.isEmpty()) {
                stringResource(R.string.start)
              } else {
                stringResource(R.string.start_with_count, selectedIds.size)
              },
          )
        }
      }
    }
  }
}

/** One row for everybody: a three-state checkbox (all, some, none) and how many there are. */
@Composable
private fun SelectAllRow(
  uiState: PatientSelectionUiState,
  onSelectAllToggle: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        .clickable(enabled = uiState.isSelectAllEnabled, role = Role.Checkbox) {
          onSelectAllToggle(!uiState.isSelectAllChecked)
        }
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .testTag("SelectAllRow"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TriStateCheckbox(
      modifier = Modifier.testTag("SelectAllCheckbox"),
      state =
        when {
          uiState.isSelectAllChecked -> ToggleableState.On
          uiState.selectedIds.isEmpty() -> ToggleableState.Off
          else -> ToggleableState.Indeterminate
        },
      enabled = uiState.isSelectAllEnabled,
      onClick = null,
    )
    Text(
      modifier = Modifier.weight(1f).padding(start = 8.dp),
      text = stringResource(R.string.select_all_patients),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun SelectablePatientRow(
  patient: PatientListViewModel.PatientItem,
  isSelected: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val details =
    listOfNotNull(formatAgeGender(patient).ifBlank { null }, patient.displayIdentifier())
      .joinToString(" · ")
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
          if (isSelected) {
            OpenMrsTheme.colors.highlightContainer
          } else {
            androidx.compose.ui.graphics.Color.Transparent
          },
        )
        .clickable(role = Role.Checkbox) { onToggle() }
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag("PatientRow_${patient.resourceId}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PatientAvatar(name = patient.name, size = 40.dp)
    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
      Text(
        text = patient.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (details.isNotEmpty()) {
        Text(
          text = details,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Checkbox(
      modifier = Modifier.testTag("PatientCheckbox_${patient.resourceId}"),
      checked = isSelected,
      onCheckedChange = null,
    )
  }
}

internal data class PatientSelectionUiState(
  val patients: List<PatientListViewModel.PatientItem> = emptyList(),
  val selectedIds: Set<String> = emptySet(),
) {
  val isSelectAllEnabled: Boolean
    get() = patients.isNotEmpty()

  val isSelectAllChecked: Boolean
    get() = patients.isNotEmpty() && patients.all { selectedIds.contains(it.resourceId) }

  val isStartEnabled: Boolean
    get() = selectedIds.isNotEmpty()
}
