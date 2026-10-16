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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hl7.fhir.r4.model.Identifier
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

@Composable
fun PatientPropertyRow(
  header: String,
  value: String,
  showSyncIcon: Boolean,
  modifier: Modifier = Modifier,
  onPrimaryClick: (() -> Unit)? = null,
) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Row(
      modifier =
        modifier
          .fillMaxWidth()
          .padding(
            start = dimensionResource(id = R.dimen.text_margin),
            end = 8.dp,
            top = 8.dp,
            bottom = 8.dp,
          )
          .testTag("PatientPropertyRow"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showSyncIcon) {
        Icon(
          modifier = Modifier.size(20.dp).padding(end = 8.dp).testTag("SyncStatusIcon"),
          painter = painterResource(id = R.drawable.ic_baseline_sync_24),
          contentDescription = stringResource(id = R.string.description_status),
          tint = OpenMrsTheme.colors.warning,
        )
      } else {
        Spacer(modifier = Modifier.width(28.dp))
      }

      Column(
        modifier =
          Modifier.weight(1f).let { base ->
            onPrimaryClick?.let { base.clickable(onClick = it) } ?: base
          },
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          modifier = Modifier.testTag("PropertyLabel"),
          text = header,
          color = Color.DarkGray,
          fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          modifier = Modifier.testTag("PropertyValue"),
          text = value,
          fontSize = 16.sp,
          fontWeight = FontWeight.Normal,
        )
      }
    }
  }
}

/** Section title with an optional count, e.g. "ENCOUNTERS 4". */
@Composable
fun PatientDetailsHeaderRow(title: String, modifier: Modifier = Modifier, count: Int? = null) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
        .testTag("PatientDetailsHeader"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
    )
    if (count != null) {
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** One line of the patient's profile: a small icon, a caption and the value. */
data class PatientDetailLine(val icon: ImageVector, val label: String, val value: String)

/**
 * The patient's profile card: initials avatar, name, age and gender, the identifiers as chips and
 * (when given) the contact details and an edit button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatientDetailsOverviewHeader(
  name: String,
  identifiers: List<Identifier>,
  modifier: Modifier = Modifier,
  ageGender: String = "",
  details: List<PatientDetailLine> = emptyList(),
  onEditClick: (() -> Unit)? = null,
) {
  val visibleIdentifiers =
    identifiers
      .filterNot { identifier ->
        identifier.type?.text?.equals("unsynced", ignoreCase = true) == true
      }
      .mapNotNull { identifier ->
        val typeText = identifier.type?.text.orEmpty()
        val valueText = identifier.value.orEmpty()
        when {
          valueText.isBlank() -> null
          typeText.isNotBlank() -> "$typeText: $valueText"
          else -> valueText
        }
      }
  Surface(
    modifier = modifier.fillMaxWidth().testTag("PatientDetailsOverviewHeader"),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = initialsOf(name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            modifier = Modifier.testTag("PatientDetailsName"),
            text = name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (ageGender.isNotBlank()) {
            Text(
              modifier = Modifier.testTag("PatientDetailsAgeGender"),
              text = ageGender,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (visibleIdentifiers.isNotEmpty()) {
        FlowRow(
          modifier = Modifier.padding(top = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          visibleIdentifiers.forEach { label ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = OpenMrsTheme.colors.highlightContainer,
            ) {
              Text(
                modifier =
                  Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("PatientDetailsIdentifier"),
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      if (details.isNotEmpty()) {
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 16.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          details.forEach { line ->
            Row(
              modifier = Modifier.testTag("PatientDetailLine"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = line.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(14.dp))
              Column {
                Text(
                  text = line.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = line.value, style = MaterialTheme.typography.bodyLarge)
              }
            }
          }
        }
      }

      if (onEditClick != null) {
        FilledTonalButton(
          onClick = onEditClick,
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("PatientEditButton"),
        ) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(id = R.string.edit_patient))
        }
      }
    }
  }
}

private fun initialsOf(name: String): String =
  name
    .split(' ', '-')
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }

/** Tells the user the patient has changes that were not uploaded yet. */
@Composable
fun PatientUnsyncedCard(modifier: Modifier = Modifier) {
  val warning = OpenMrsTheme.colors.warning
  Surface(
    modifier = modifier.fillMaxWidth().testTag("PatientUnsyncedCard"),
    shape = RoundedCornerShape(16.dp),
    color = warning.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, warning.copy(alpha = 0.4f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_baseline_sync_24),
        contentDescription = stringResource(id = R.string.task_image),
        tint = warning,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = stringResource(id = R.string.patient_unsynced_info),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/** A visit: its type and period; an open visit offers **End visit** through [onDateClick]. */
@Composable
fun VisitListItemRow(
  encounterType: String,
  encounterDate: String,
  modifier: Modifier = Modifier,
  onDateClick: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("VisitListItemRow"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp).clip(CircleShape).background(OpenMrsTheme.colors.highlightContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.CalendarMonth,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
          modifier = Modifier.testTag("VisitType"),
          text = encounterType,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )
        Text(
          modifier = Modifier.testTag("VisitDate"),
          text = encounterDate,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (onDateClick != null) {
        TextButton(onClick = onDateClick, modifier = Modifier.testTag("VisitEndButton")) {
          Text(text = stringResource(id = R.string.end_visit))
        }
      }
    }
  }
}

/** An encounter: type, date and a sync marker; the whole card opens it for editing. */
@Composable
fun EncounterListItemRow(
  modifier: Modifier = Modifier,
  encounterType: String,
  encounterDate: String,
  showSyncIcon: Boolean,
  onTitleClick: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("EncounterListItemRow"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .let { base -> onTitleClick?.let { base.clickable(onClick = it) } ?: base }
          .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp).clip(CircleShape).background(OpenMrsTheme.colors.highlightContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
          modifier = Modifier.testTag("EncounterType"),
          text = encounterType,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          modifier = Modifier.testTag("EncounterDate"),
          text = encounterDate,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (showSyncIcon) {
        Icon(
          modifier = Modifier.size(20.dp).testTag("EncounterSyncIcon"),
          painter = painterResource(id = R.drawable.ic_baseline_sync_24),
          contentDescription = stringResource(id = R.string.description_status),
          tint = OpenMrsTheme.colors.warning,
        )
        Spacer(modifier = Modifier.width(8.dp))
      }
      if (onTitleClick != null) {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = stringResource(id = R.string.edit_encounter),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@Composable
fun UnsyncedEncounterRow(
  title: String,
  hasObservations: Boolean,
  onToggleExpand: () -> Unit,
  onDownload: () -> Unit,
  onDelete: () -> Unit,
  isSynced: Boolean,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(OpenMrsTheme.colors.neutralContainer)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag("UnsyncedEncounterRow"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      modifier =
        Modifier.size(24.dp)
          .padding(start = 24.dp)
          .testTag("EncounterExpand")
          .alpha(if (hasObservations) 1f else 0f),
      enabled = hasObservations,
      onClick = onToggleExpand,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_expand),
        contentDescription = stringResource(id = R.string.expand),
      )
    }
    Text(
      modifier = Modifier.weight(1f).padding(start = 8.dp).testTag("EncounterTitle"),
      text = title,
      fontSize = 14.sp,
    )
    IconButton(
      modifier = Modifier.size(36.dp).testTag("DownloadEncounter"),
      onClick = onDownload,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_download),
        contentDescription = stringResource(id = R.string.download_encounter),
      )
    }
    IconButton(
      modifier = Modifier.size(36.dp).testTag("DeleteEncounter"),
      enabled = !isSynced,
      onClick = onDelete,
    ) {
      Icon(
        painter =
          painterResource(
            id = if (isSynced) R.drawable.ic_check_decagram_green else R.drawable.ic_delete,
          ),
        contentDescription = stringResource(id = R.string.delete_encounter),
      )
    }
  }
}

@Composable
fun UnsyncedPatientRow(
  name: String,
  onToggleExpand: () -> Unit,
  onDownload: () -> Unit,
  onDelete: () -> Unit,
  showExpand: Boolean,
  isSynced: Boolean,
) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 2.dp, vertical = 4.dp)
        .testTag("UnsyncedPatientRow"),
    shape = RoundedCornerShape(8.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        modifier = Modifier.size(24.dp).testTag("PatientExpand").alpha(if (showExpand) 1f else 0f),
        enabled = showExpand,
        onClick = onToggleExpand,
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_expand),
          contentDescription = stringResource(id = R.string.expand),
        )
      }
      Text(
        modifier = Modifier.weight(1f).padding(start = 8.dp).testTag("PatientName"),
        text = name,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
      )
      IconButton(
        modifier = Modifier.size(40.dp).testTag("DownloadPatient"),
        onClick = onDownload,
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_download),
          contentDescription = stringResource(id = R.string.download_patient),
        )
      }
      IconButton(
        modifier = Modifier.size(40.dp).testTag("DeletePatient"),
        enabled = !isSynced,
        onClick = onDelete,
      ) {
        Icon(
          painter =
            painterResource(
              id = if (isSynced) R.drawable.ic_check_decagram_green else R.drawable.ic_delete,
            ),
          contentDescription = stringResource(id = R.string.delete_patient),
        )
      }
    }
  }
}

@Composable
fun ObservationCardRow(text: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("ObservationCard"),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Text(
      modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("ObservationDetail"),
      text = text,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
fun UnsyncedObservationRow(
  title: String,
  onDownload: () -> Unit,
  onDelete: () -> Unit,
  isSynced: Boolean,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag("UnsyncedObservationRow"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      modifier = Modifier.weight(1f).padding(start = 64.dp).testTag("ObservationTitle"),
      text = title,
      fontSize = 14.sp,
    )
    IconButton(
      modifier = Modifier.size(36.dp).testTag("DownloadObservation"),
      onClick = onDownload,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_download),
        contentDescription = stringResource(id = R.string.download_observation),
      )
    }
    IconButton(
      modifier = Modifier.size(36.dp).testTag("DeleteObservation"),
      enabled = !isSynced,
      onClick = onDelete,
    ) {
      Icon(
        painter =
          painterResource(
            id = if (isSynced) R.drawable.ic_check_decagram_green else R.drawable.ic_delete_black,
          ),
        contentDescription = stringResource(id = R.string.delete_observation),
      )
    }
  }
}

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
      Text(
        modifier =
          Modifier.weight(1f)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("SelectPatientText"),
        text = text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
      )
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
          .clickable { onToggle() }
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
