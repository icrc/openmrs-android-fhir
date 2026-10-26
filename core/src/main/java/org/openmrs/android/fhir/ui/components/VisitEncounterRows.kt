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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

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
        Modifier.let { base ->
            onClick?.let { base.clickable(role = Role.Button, onClick = it) } ?: base
          }
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
          .let { base ->
            onTitleClick?.let { base.clickable(role = Role.Button, onClick = it) } ?: base
          }
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
internal fun ExpandChevron(
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
internal fun PendingActions(
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
