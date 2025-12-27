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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

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

  val buttonColors =
    ButtonDefaults.buttonColors(
      containerColor = colorResource(id = R.color.dashboard_cardview_textcolor),
      disabledContainerColor =
        colorResource(id = R.color.dashboard_cardview_textcolor).copy(alpha = 0.38f),
      contentColor = Color.White,
      disabledContentColor = Color.White.copy(alpha = 0.38f),
    )

  Surface(modifier = modifier.fillMaxWidth().testTag("PatientSelectionDialog")) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 420.dp, max = 640.dp)
          .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
      Text(
        modifier = Modifier.padding(bottom = 12.dp),
        text = stringResource(R.string.select_patients_for_group_encounter),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )

      Divider(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(enabled = uiState.isSelectAllEnabled) {
              onSelectAllToggle(!uiState.isSelectAllChecked)
            }
            .padding(vertical = 8.dp)
            .testTag("SelectAllRow"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          modifier = Modifier.testTag("SelectAllCheckbox"),
          checked = uiState.isSelectAllChecked,
          enabled = uiState.isSelectAllEnabled,
          onCheckedChange = { onSelectAllToggle(it) },
        )
        Text(
          text = stringResource(R.string.select_all_patients),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      LazyColumn(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 300.dp, max = 460.dp)
            .weight(1f, fill = true)
            .testTag("PatientSelectionList"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(patients) { patient ->
          val isSelected = selectedIds.contains(patient.resourceId)
          SelectablePatientRow(
            patient = patient,
            isSelected = isSelected,
            onToggle = { onPatientToggle(patient) },
          )
        }
      }

      Spacer(modifier = Modifier.size(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
          modifier = Modifier.weight(1f).testTag("CancelButton"),
          colors = buttonColors,
          shape = RoundedCornerShape(4.dp),
          onClick = onDismissRequest,
        ) {
          Text(
            text = stringResource(R.string.cancel),
            fontSize = 12.sp,
          )
        }

        Button(
          modifier = Modifier.weight(1f).testTag("StartEncounterButton"),
          colors = buttonColors,
          shape = RoundedCornerShape(4.dp),
          enabled = uiState.isStartEnabled,
          onClick = onStartEncounter,
        ) {
          Text(
            text = stringResource(R.string.start),
            fontSize = 12.sp,
          )
        }
      }
    }
  }
}

@Composable
fun SelectablePatientRow(
  patient: PatientListViewModel.PatientItem,
  isSelected: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable { onToggle() }
        .padding(vertical = 12.dp)
        .testTag("PatientRow_${patient.resourceId}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      modifier = Modifier.testTag("PatientCheckbox_${patient.resourceId}"),
      checked = isSelected,
      onCheckedChange = { onToggle() },
    )
    Text(
      modifier = Modifier.padding(start = 12.dp),
      text = patient.name,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
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
