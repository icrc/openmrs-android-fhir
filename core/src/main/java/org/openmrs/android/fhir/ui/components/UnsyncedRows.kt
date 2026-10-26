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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/** An encounter waiting to be uploaded; indented under its patient. */
@Composable
fun UnsyncedEncounterRow(
  title: String,
  hasObservations: Boolean,
  onToggleExpand: () -> Unit,
  onDownload: () -> Unit,
  onDelete: () -> Unit,
  isSynced: Boolean,
  modifier: Modifier = Modifier,
  isExpanded: Boolean = false,
) {
  Surface(
    modifier = modifier.fillMaxWidth().padding(start = 20.dp).testTag("UnsyncedEncounterRow"),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ExpandChevron(
        expanded = isExpanded,
        visible = hasObservations,
        onClick = onToggleExpand,
        tag = "EncounterExpand",
      )
      Text(
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("EncounterTitle"),
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      PendingActions(
        isSynced = isSynced,
        onDownload = onDownload,
        onDelete = onDelete,
        downloadTag = "DownloadEncounter",
        deleteTag = "DeleteEncounter",
        downloadDescription = stringResource(id = R.string.download_encounter),
        deleteDescription = stringResource(id = R.string.delete_encounter),
      )
    }
  }
}

/** A patient with changes to upload: the top level of the pending list. */
@Composable
fun UnsyncedPatientRow(
  name: String,
  onToggleExpand: () -> Unit,
  onDownload: () -> Unit,
  onDelete: () -> Unit,
  showExpand: Boolean,
  isSynced: Boolean,
  modifier: Modifier = Modifier,
  isExpanded: Boolean = false,
) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("UnsyncedPatientRow"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(CircleShape)
            .background(
              if (isSynced) {
                OpenMrsTheme.colors.highlightContainer
              } else {
                OpenMrsTheme.colors.warning.copy(alpha = 0.2f)
              },
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp),
        )
      }
      Text(
        modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("PatientName"),
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      PendingActions(
        isSynced = isSynced,
        onDownload = onDownload,
        onDelete = onDelete,
        downloadTag = "DownloadPatient",
        deleteTag = "DeletePatient",
        downloadDescription = stringResource(id = R.string.download_patient),
        deleteDescription = stringResource(id = R.string.delete_patient),
      )
      ExpandChevron(
        expanded = isExpanded,
        visible = showExpand,
        onClick = onToggleExpand,
        tag = "PatientExpand",
        modifier = Modifier.padding(end = 4.dp),
      )
    }
  }
}

/** An observation waiting to be uploaded; the deepest level, indented under its encounter. */
@Composable
fun UnsyncedObservationRow(
  title: String,
  onDownload: () -> Unit,
  onDelete: () -> Unit,
  isSynced: Boolean,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth().padding(start = 40.dp).testTag("UnsyncedObservationRow"),
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        modifier = Modifier.weight(1f).padding(end = 8.dp).testTag("ObservationTitle"),
        text = title,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      PendingActions(
        isSynced = isSynced,
        onDownload = onDownload,
        onDelete = onDelete,
        downloadTag = "DownloadObservation",
        deleteTag = "DeleteObservation",
        downloadDescription = stringResource(id = R.string.download_observation),
        deleteDescription = stringResource(id = R.string.delete_observation),
      )
    }
  }
}
