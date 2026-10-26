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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/**
 * A selectable patient list: tonal group icon, name and a checkbox. The whole card toggles it; a
 * selected list gets the brand border and tint.
 */
@Composable
fun SelectPatientListItemRow(
  text: String,
  checked: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
) {
  val primary = MaterialTheme.colorScheme.primary
  Surface(
    onClick = onToggle,
    modifier = modifier.fillMaxWidth().testTag("SelectPatientRow"),
    shape = RoundedCornerShape(16.dp),
    color =
      if (checked) {
        OpenMrsTheme.colors.highlightContainer
      } else {
        MaterialTheme.colorScheme.surface
      },
    border =
      BorderStroke(
        width = if (checked) 2.dp else 1.dp,
        color = if (checked) primary else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) primary else OpenMrsTheme.colors.highlightContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Groups,
          contentDescription = null,
          tint = if (checked) MaterialTheme.colorScheme.onPrimary else primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
          modifier = Modifier.testTag("SelectPatientText"),
          text = text,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (subtitle != null) {
          Text(
            modifier = Modifier.testTag("SelectPatientSubtitle"),
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Checkbox(
        modifier = Modifier.testTag("SelectPatientCheckbox"),
        checked = checked,
        onCheckedChange = { onToggle() },
      )
    }
  }
}

@Composable
fun PatientSelectableRow(
  name: String,
  checked: Boolean,
  onToggle: () -> Unit,
) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 0.dp, vertical = 12.dp)
          .clickable(role = Role.Checkbox) { onToggle() }
          .testTag("PatientSelectableRow"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(
        modifier = Modifier.padding(end = 12.dp).testTag("PatientSelectableCheckbox"),
        checked = checked,
        onCheckedChange = { onToggle() },
      )
      Text(
        modifier = Modifier.weight(1f).testTag("PatientSelectableName"),
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
fun SyncSessionRow(
  session: SyncSession,
  onDelete: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val statusColor =
    when (session.status) {
      SyncStatus.ONGOING -> OpenMrsTheme.colors.warning
      SyncStatus.COMPLETED -> OpenMrsTheme.colors.success
      SyncStatus.COMPLETED_WITH_ERRORS -> MaterialTheme.colorScheme.error
      SyncStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Card(
    modifier =
      modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).testTag("SyncSessionRow"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(dimensionResource(id = R.dimen.cardView_radius_corner)),
  ) {
    Row(modifier = Modifier.padding(12.dp)) {
      Box(
        modifier =
          Modifier.width(8.dp)
            .fillMaxHeight()
            .background(statusColor)
            .testTag("SyncStatusIndicator"),
      )
      Column(
        modifier = Modifier.weight(1f).padding(start = 8.dp),
      ) {
        Text(
          modifier = Modifier.testTag("SyncStartTime"),
          text = stringResource(id = R.string.start_time) + ": " + session.startTime,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          modifier = Modifier.testTag("SyncProgress"),
          text =
            stringResource(id = R.string.downloaded) +
              " ${session.downloadedPatients}/${session.totalPatientsToDownload} | " +
              stringResource(id = R.string.uploaded) +
              " ${session.uploadedPatients}/${session.totalPatientsToUpload}",
          fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val endTime =
          if (session.completionTime.isNullOrEmpty()) {
            stringResource(id = R.string.in_progress)
          } else {
            stringResource(id = R.string.completed_at) + ": " + session.completionTime
          }
        Text(modifier = Modifier.testTag("SyncEndTime"), text = endTime, fontSize = 14.sp)

        if (session.status == SyncStatus.COMPLETED_WITH_ERRORS && session.errors.isNotEmpty()) {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            modifier = Modifier.testTag("SyncErrorCount"),
            text = stringResource(id = R.string.errors) + ": ${session.errors.size}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Column(
            modifier = Modifier.testTag("SyncErrorDetails"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            session.errors.forEachIndexed { index, error ->
              Text(
                modifier = Modifier.testTag("SyncErrorDetail$index"),
                text = "- $error",
                fontSize = 12.sp,
              )
            }
          }
        }
      }
      if (session.status != SyncStatus.ONGOING && onDelete != null) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.error,
        ) {
          IconButton(modifier = Modifier.testTag("DeleteSyncSession"), onClick = onDelete) {
            Icon(
              painter = painterResource(id = R.drawable.ic_delete),
              contentDescription = stringResource(id = R.string.delete),
              tint = MaterialTheme.colorScheme.surface,
            )
          }
        }
      }
    }
  }
}
