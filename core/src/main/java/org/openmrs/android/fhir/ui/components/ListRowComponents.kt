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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hl7.fhir.r4.model.Identifier
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus

@Composable
fun PatientPropertyRow(
  header: String,
  value: String,
  showSyncIcon: Boolean,
  modifier: Modifier = Modifier,
  onPrimaryClick: (() -> Unit)? = null,
) {
  Surface(color = colorResource(id = R.color.white)) {
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
          tint = colorResource(id = R.color.moderate_risk),
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

@Composable
fun PatientDetailsHeaderRow(title: String, modifier: Modifier = Modifier) {
  Text(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
        .testTag("PatientDetailsHeader"),
    text = title,
    color = colorResource(id = R.color.black),
    fontSize = 20.sp,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
fun PatientDetailsOverviewHeader(
  name: String,
  identifiers: List<Identifier>,
  modifier: Modifier = Modifier,
) {
  val visibleIdentifiers =
    identifiers.filterNot { identifier ->
      identifier.type?.text?.equals("unsynced", ignoreCase = true) == true
    }
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(1.dp)
        .padding(bottom = 12.dp)
        .testTag("PatientDetailsOverviewHeader"),
    color = colorResource(id = R.color.white),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
        modifier = Modifier.size(120.dp),
        painter = painterResource(id = R.drawable.ic_user),
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFF1F2F4)),
      )
      Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
      ) {
        Text(
          modifier = Modifier.testTag("PatientDetailsName"),
          text = name,
          fontSize = 26.sp,
          fontFamily = FontFamily(Font(R.font.inter_semibold)),
        )
        Spacer(modifier = Modifier.height(4.dp))
        visibleIdentifiers.forEach { identifier ->
          val typeText = identifier.type?.text.orEmpty()
          val valueText = identifier.value.orEmpty()
          val label =
            if (typeText.isNotBlank()) {
              "$typeText: $valueText"
            } else {
              valueText
            }
          if (label.isNotBlank()) {
            Text(
              modifier = Modifier.testTag("PatientDetailsIdentifier"),
              text = label,
              fontSize = 16.sp,
              fontFamily = FontFamily(Font(R.font.inter)),
            )
          }
        }
      }
    }
  }
}

@Composable
fun PatientUnsyncedCard(modifier: Modifier = Modifier) {
  val backgroundColor = Color(0xFFfef3e6)
  val accentColor = Color(0xFFb16e1c)
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag("PatientUnsyncedCard"),
    colors = CardDefaults.cardColors(containerColor = backgroundColor),
    border = BorderStroke(4.dp, Color(0xFFFFDCB3)),
    shape = RoundedCornerShape(dimensionResource(id = R.dimen.cardView_radius_corner)),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(all = dimensionResource(id = R.dimen.new_patient_padding))
          .background(backgroundColor),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(id = android.R.drawable.ic_dialog_alert),
        contentDescription = stringResource(id = R.string.task_image),
        tint = accentColor,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = stringResource(id = R.string.patient_unsynced_info),
        color = accentColor,
        fontSize = 14.sp,
      )
    }
  }
}

@Composable
fun VisitListItemRow(
  encounterType: String,
  encounterDate: String,
  onDateClick: (() -> Unit)? = null,
) {
  Surface(color = colorResource(id = R.color.white)) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(
            start = dimensionResource(id = R.dimen.text_margin),
            end = 8.dp,
            top = 8.dp,
            bottom = 8.dp,
          )
          .testTag("VisitListItemRow"),
    ) {
      Text(
        modifier = Modifier.testTag("VisitType"),
        text = encounterType,
        fontSize = 16.sp,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        modifier =
          Modifier.testTag("VisitDate").let { base ->
            onDateClick?.let { base.clickable(onClick = it) } ?: base
          },
        text = encounterDate,
        fontSize = 14.sp,
        color = Color.DarkGray,
      )
    }
  }
}

@Composable
fun EncounterListItemRow(
  modifier: Modifier = Modifier,
  encounterType: String,
  encounterDate: String,
  showSyncIcon: Boolean,
  onTitleClick: (() -> Unit)? = null,
) {
  Surface(color = colorResource(id = R.color.white)) {
    Column(
      modifier = modifier.fillMaxWidth().padding(8.dp).testTag("EncounterListItemRow"),
    ) {
      Text(
        modifier = Modifier.testTag("EncounterDate").padding(start = 20.dp),
        text = encounterDate,
        fontSize = 14.sp,
        color = Color.DarkGray,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp),
      ) {
        if (showSyncIcon) {
          Icon(
            modifier = Modifier.size(20.dp).testTag("EncounterSyncIcon"),
            painter = painterResource(id = R.drawable.ic_baseline_sync_24),
            contentDescription = stringResource(id = R.string.description_status),
            tint = colorResource(id = R.color.moderate_risk),
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
          modifier =
            Modifier.testTag("EncounterType").let { base ->
              onTitleClick?.let { base.clickable(onClick = it) } ?: base
            },
          text = encounterType,
          fontSize = 16.sp,
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
        .background(Color(0xFFF5F5F5))
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
        .background(Color(0xFFEEEEEE))
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

@Composable
fun SelectPatientListItemRow(
  text: String,
  checked: Boolean,
  onToggle: () -> Unit,
) {
  val backgroundColor =
    if (checked) {
      colorResource(id = R.color.selected_item_background)
    } else {
      colorResource(id = R.color.default_item_background)
    }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(backgroundColor, shape = RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .testTag("SelectPatientRow"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("SelectPatientText"),
      text = text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.bodyLarge,
    )
    Checkbox(
      modifier = Modifier.testTag("SelectPatientCheckbox"),
      checked = checked,
      onCheckedChange = { onToggle() },
    )
  }
}

@Composable
fun PatientSelectableRow(
  name: String,
  checked: Boolean,
  onToggle: () -> Unit,
) {
  Surface(color = colorResource(id = R.color.white)) {
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
      SyncStatus.ONGOING -> colorResource(id = R.color.orange)
      SyncStatus.COMPLETED -> colorResource(id = R.color.tertiary_green_80)
      SyncStatus.COMPLETED_WITH_ERRORS -> colorResource(id = R.color.error_red_40)
    }

  Card(
    modifier =
      modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).testTag("SyncSessionRow"),
    colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.white)),
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
          color = colorResource(id = R.color.error_red_40),
        ) {
          IconButton(modifier = Modifier.testTag("DeleteSyncSession"), onClick = onDelete) {
            Icon(
              painter = painterResource(id = R.drawable.ic_delete),
              contentDescription = stringResource(id = R.string.delete),
              tint = colorResource(id = R.color.white),
            )
          }
        }
      }
    }
  }
}
