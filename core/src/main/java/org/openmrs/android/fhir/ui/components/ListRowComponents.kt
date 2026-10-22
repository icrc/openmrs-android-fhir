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

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
fun PatientDetailsHeaderRow(
  title: String,
  modifier: Modifier = Modifier,
  count: Int? = null,
  actionText: String? = null,
  onActionClick: () -> Unit = {},
) {
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
    if (actionText != null) {
      Spacer(modifier = Modifier.weight(1f))
      TextButton(
        onClick = onActionClick,
        modifier = Modifier.testTag("PatientDetailsHeaderAction"),
      ) {
        Text(text = actionText)
      }
    }
  }
}

/** One line of the patient's profile: a small icon, a caption and the value. */
data class PatientDetailLine(
  val icon: ImageVector,
  val label: String,
  val value: String,
  /**
   * Tapping the value does this (call the number, write to the address); not tappable if `null`.
   */
  val onClick: (() -> Unit)? = null,
)

/**
 * A line of the profile; a number or an address that can be acted on looks and acts like a link.
 */
@Composable
private fun PatientDetailLineRow(line: PatientDetailLine) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (line.onClick != null) {
            Modifier.clickable(role = Role.Button, onClick = line.onClick)
          } else {
            Modifier
          },
        )
        .testTag("PatientDetailLine"),
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
      Text(
        text = line.value,
        style = MaterialTheme.typography.bodyLarge,
        color =
          if (line.onClick != null) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurface
          },
        textDecoration = if (line.onClick != null) TextDecoration.Underline else null,
      )
    }
  }
}

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
  /** More facts, shown under "Show all details". */
  extraDetails: List<PatientDetailLine> = emptyList(),
  onEditClick: (() -> Unit)? = null,
  /** Something about the patient has not been sent to the server yet. */
  unsynced: Boolean = false,
) {
  var showAll by rememberSaveable { mutableStateOf(false) }
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

      if (unsynced) {
        PatientUnsyncedCard(modifier = Modifier.padding(top = 16.dp))
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
          details.forEach { line -> PatientDetailLineRow(line) }
        }
      }

      if (extraDetails.isNotEmpty()) {
        if (showAll) {
          if (details.isEmpty()) {
            HorizontalDivider(
              modifier = Modifier.padding(vertical = 16.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
          } else {
            Spacer(modifier = Modifier.height(12.dp))
          }
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            extraDetails.forEach { line -> PatientDetailLineRow(line) }
          }
        }
        TextButton(
          onClick = { showAll = !showAll },
          modifier = Modifier.padding(top = 8.dp).testTag("PatientShowAllDetails"),
        ) {
          Icon(
            imageVector = if (showAll) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text =
              stringResource(
                if (showAll) R.string.patient_details_hide else R.string.patient_details_show_all,
              ),
          )
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

/**
 * Tells the user the patient has changes that were not uploaded yet. A soft strip for the inside of
 * the patient's card, under the name.
 */
@Composable
fun PatientUnsyncedCard(modifier: Modifier = Modifier) {
  val warning = OpenMrsTheme.colors.warning
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(warning.copy(alpha = 0.14f))
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .testTag("PatientUnsyncedCard"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(32.dp).clip(CircleShape).background(warning),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_baseline_sync_24),
        contentDescription = stringResource(id = R.string.task_image),
        // Dark on the yellow badge, in both themes.
        tint = androidx.compose.ui.graphics.Color(0xFF3A2C00),
        modifier = Modifier.size(18.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = stringResource(id = R.string.patient_unsynced_info),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/**
 * A visit: its type, period and whether it is open. An open visit offers **End visit** through
 * [onDateClick], an ended one **Reopen** through [onReopenClick].
 */
@Composable
fun VisitListItemRow(
  encounterType: String,
  encounterDate: String,
  modifier: Modifier = Modifier,
  onDateClick: (() -> Unit)? = null,
  statusText: String? = null,
  isActive: Boolean = false,
  showSyncIcon: Boolean = false,
  onReopenClick: (() -> Unit)? = null,
  /** The card itself: opens the visit to change when it started and ended. */
  onClick: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("VisitListItemRow"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        if (isActive) 2.dp else 1.dp,
        if (isActive) OpenMrsTheme.colors.success else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Row(
      modifier =
        Modifier.let { base -> onClick?.let { base.clickable(onClick = it) } ?: base }
          .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            modifier = Modifier.weight(1f, fill = false).testTag("VisitType"),
            text = encounterType,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (statusText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              modifier =
                Modifier.clip(RoundedCornerShape(50))
                  .background(
                    if (isActive) {
                      OpenMrsTheme.colors.success.copy(alpha = 0.16f)
                    } else {
                      MaterialTheme.colorScheme.surfaceVariant
                    },
                  )
                  .padding(horizontal = 8.dp, vertical = 2.dp)
                  .testTag("VisitStatus"),
              text = statusText,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color =
                if (isActive) {
                  OpenMrsTheme.colors.success
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }
        }
        Text(
          modifier = Modifier.testTag("VisitDate"),
          text = encounterDate,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (showSyncIcon) {
        Icon(
          modifier = Modifier.size(20.dp).testTag("VisitSyncIcon"),
          painter = painterResource(id = R.drawable.ic_baseline_sync_24),
          contentDescription = stringResource(id = R.string.description_status),
          tint = OpenMrsTheme.colors.warning,
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      if (onClick != null) {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = stringResource(id = R.string.visit_edit_title),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp).testTag("VisitEditIcon"),
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      if (onDateClick != null) {
        TextButton(onClick = onDateClick, modifier = Modifier.testTag("VisitEndButton")) {
          Text(text = stringResource(id = R.string.end_visit))
        }
      }
      if (onReopenClick != null) {
        TextButton(onClick = onReopenClick, modifier = Modifier.testTag("VisitReopenButton")) {
          Text(text = stringResource(id = R.string.reopen_visit))
        }
      }
    }
  }
}

/**
 * An encounter: type, date and a sync marker. The card opens it to look at ([onTitleClick]); the
 * pencil ([onEditClick]) edits it right away.
 */
@Composable
fun EncounterListItemRow(
  modifier: Modifier = Modifier,
  encounterType: String,
  encounterDate: String,
  showSyncIcon: Boolean,
  onTitleClick: (() -> Unit)? = null,
  onEditClick: (() -> Unit)? = null,
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
          .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
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
      if (onEditClick != null) {
        IconButton(onClick = onEditClick, modifier = Modifier.testTag("EncounterEditButton")) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(id = R.string.edit_encounter),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      } else if (onTitleClick != null) {
        Icon(
          imageVector = Icons.Filled.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(end = 8.dp).size(22.dp),
        )
      }
    }
  }
}

/** Chevron that turns when the row it belongs to is open. */
@Composable
private fun ExpandChevron(
  expanded: Boolean,
  visible: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
) {
  val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
  IconButton(
    modifier = modifier.size(32.dp).testTag(tag).alpha(if (visible) 1f else 0f),
    enabled = visible,
    onClick = onClick,
  ) {
    Icon(
      modifier = Modifier.rotate(rotation),
      painter = painterResource(id = R.drawable.ic_expand),
      contentDescription = stringResource(id = R.string.expand),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Download (to share the data) and delete (or a check once uploaded) for one pending resource. */
@Composable
private fun PendingActions(
  isSynced: Boolean,
  onDownload: () -> Unit,
  onDelete: () -> Unit,
  downloadTag: String,
  deleteTag: String,
  downloadDescription: String,
  deleteDescription: String,
) {
  IconButton(
    modifier = Modifier.size(40.dp).testTag(downloadTag),
    onClick = onDownload,
  ) {
    Icon(
      painter = painterResource(id = R.drawable.ic_download),
      contentDescription = downloadDescription,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  IconButton(
    modifier = Modifier.size(40.dp).testTag(deleteTag),
    enabled = !isSynced,
    onClick = onDelete,
  ) {
    Icon(
      painter =
        painterResource(
          id = if (isSynced) R.drawable.ic_check_decagram_green else R.drawable.ic_delete,
        ),
      contentDescription = deleteDescription,
      tint = if (isSynced) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

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
