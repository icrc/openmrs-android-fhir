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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

@Immutable
sealed class SyncStatusUiState {
  data class Syncing(val completed: Int, val total: Int) : SyncStatusUiState()

  data class Success(val message: String) : SyncStatusUiState()

  data class Error(val message: String) : SyncStatusUiState()

  data object Idle : SyncStatusUiState()
}

@Composable
fun SyncStatusLayout(
  state: SyncStatusUiState,
  modifier: Modifier = Modifier,
) {
  if (state is SyncStatusUiState.Idle) return

  val (headerColor, iconColor, iconRes, label, progress, percentLabel, progressColor) =
    when (state) {
      is SyncStatusUiState.Syncing -> {
        val progress = state.completed.coerceAtLeast(0)
        val total = state.total.takeIf { it > 0 } ?: 100
        val fraction = (progress.toFloat() / total).coerceIn(0f, 1f)
        val percent = (fraction * 100).toInt()
        Quintuple(
          OpenMrsTheme.colors.highlightContainer,
          MaterialTheme.colorScheme.onSurfaceVariant,
          R.drawable.ic_baseline_sync_24,
          stringResource(id = R.string.syncing),
          fraction,
          "$percent%",
          MaterialTheme.colorScheme.primary,
        )
      }
      is SyncStatusUiState.Success ->
        Quintuple(
          OpenMrsTheme.colors.success,
          MaterialTheme.colorScheme.surface,
          R.drawable.ic_check_circle,
          state.message,
          1f,
          stringResource(id = R.string._100),
          OpenMrsTheme.colors.success,
        )
      is SyncStatusUiState.Error ->
        Quintuple(
          MaterialTheme.colorScheme.error,
          MaterialTheme.colorScheme.surface,
          R.drawable.ic_error,
          state.message,
          1f,
          stringResource(id = R.string._0),
          MaterialTheme.colorScheme.error,
        )
      SyncStatusUiState.Idle -> throw IllegalStateException("Idle state should return early")
    }

  Card(
    modifier =
      modifier.fillMaxWidth().padding(vertical = dimensionResource(id = R.dimen.text_margin)),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation =
      CardDefaults.cardElevation(defaultElevation = dimensionResource(id = R.dimen.status_margin)),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().background(color = headerColor).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          modifier = Modifier.size(24.dp),
          painter = painterResource(id = iconRes),
          contentDescription = label,
          tint = iconColor,
        )
        Text(
          modifier = Modifier.padding(start = 5.dp),
          text = label,
          style = MaterialTheme.typography.bodyLarge,
          color = iconColor,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        text = percentLabel,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.bodyMedium,
      )
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
        progress = { progress },
        color = progressColor,
      )
    }
  }
}

/**
 * One synchronization: when it ran, how far the download and the upload got, and what went wrong.
 * Finished sessions can be deleted; long error lists are folded.
 */
@Composable
fun SyncSessionCard(
  session: SyncSession,
  onDeleteSession: (SyncSession) -> Unit,
  modifier: Modifier = Modifier,
) {
  val (statusColor, statusLabel, statusIcon) =
    when (session.status) {
      SyncStatus.ONGOING ->
        Triple(OpenMrsTheme.colors.warning, R.string.in_progress, R.drawable.ic_baseline_sync_24)
      SyncStatus.COMPLETED ->
        Triple(
          OpenMrsTheme.colors.success,
          R.string.sync_status_completed,
          R.drawable.ic_check_circle,
        )
      SyncStatus.COMPLETED_WITH_ERRORS ->
        Triple(
          MaterialTheme.colorScheme.error,
          R.string.sync_status_completed_errors,
          R.drawable.ic_error,
        )
    }

  Surface(
    modifier = modifier.fillMaxWidth().testTag("SyncSessionCard"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 16.dp, end = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = session.startTime,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(id = statusIcon),
              contentDescription = null,
              tint = statusColor,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = statusLabel),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = statusColor,
            )
          }
        }
        if (session.status != SyncStatus.ONGOING) {
          IconButton(
            onClick = { onDeleteSession(session) },
            modifier = Modifier.testTag("SyncSessionDelete"),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_delete),
              contentDescription = stringResource(id = R.string.delete),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Column(
        modifier = Modifier.padding(top = 12.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SyncProgressLine(
          label = stringResource(id = R.string.sync_downloaded),
          done = session.downloadedPatients,
          total = session.totalPatientsToDownload,
          inProgress = session.status == SyncStatus.ONGOING,
          color = statusColor,
        )
        SyncProgressLine(
          label = stringResource(id = R.string.sync_uploaded),
          done = session.uploadedPatients,
          total = session.totalPatientsToUpload,
          inProgress = session.status == SyncStatus.ONGOING,
          color = statusColor,
        )
      }

      // The status above already says "in progress"; the time only matters once it has finished.
      session.completionTime?.let { completedAt ->
        Text(
          modifier = Modifier.padding(top = 12.dp),
          text = stringResource(id = R.string.completed_at_with_value, completedAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (session.status == SyncStatus.COMPLETED_WITH_ERRORS && session.errors.isNotEmpty()) {
        SyncErrors(errors = session.errors, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
      }
    }
  }
}

/**
 * "Downloaded 12 / 30" with a bar showing how far it got. While the sync is running and has passed
 * the total it was given (that total only counts the patients, not their encounters and
 * observations), only the amount so far is shown, with a bar that is not filled.
 */
@Composable
private fun SyncProgressLine(
  label: String,
  done: Int,
  total: Int,
  inProgress: Boolean,
  color: Color,
) {
  val totalUnknown = inProgress && done > 0 && done >= total
  val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 1f
  Column {
    Row {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = if (totalUnknown) "$done" else "$done / $total",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    if (totalUnknown) {
      LinearProgressIndicator(
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape),
      )
    } else {
      LinearProgressIndicator(
        progress = { fraction },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape),
      )
    }
  }
}

/** The errors of a session; only the first few are shown until the user asks for all of them. */
@Composable
private fun SyncErrors(errors: List<String>, modifier: Modifier = Modifier) {
  var expanded by remember { mutableStateOf(false) }
  val visible = if (expanded) errors else errors.take(ERRORS_FOLDED)
  Surface(
    modifier = modifier.fillMaxWidth().testTag("SyncSessionErrors"),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.errorContainer,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = stringResource(id = R.string.errors_with_count, errors.size),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
      visible.forEach { error ->
        Text(
          modifier = Modifier.padding(top = 4.dp),
          text = "• $error",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
      if (errors.size > ERRORS_FOLDED) {
        TextButton(onClick = { expanded = !expanded }) {
          Text(
            text =
              if (expanded) {
                stringResource(id = R.string.sync_errors_show_less)
              } else {
                stringResource(id = R.string.sync_errors_show_all, errors.size)
              },
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
        }
      }
    }
  }
}

private const val ERRORS_FOLDED = 3

/**
 * The synchronization history, newest first, with a shortcut to clear what has finished. Clearing
 * asks first, as it cannot be undone. The screen around it provides the padding.
 */
@Composable
fun SyncHistoryList(
  syncSessions: List<SyncSession>,
  onDeleteSession: (SyncSession) -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var confirmClear by remember { mutableStateOf(false) }
  val hasFinished = syncSessions.any { it.status != SyncStatus.ONGOING }

  Box(modifier = modifier.fillMaxSize().testTag("SyncInfoScreen")) {
    if (syncSessions.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          modifier = Modifier.size(72.dp),
          painter = painterResource(id = R.drawable.ic_baseline_sync_24),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          modifier = Modifier.padding(top = 8.dp),
          text = stringResource(id = R.string.no_sync_info_available),
          style = MaterialTheme.typography.bodyLarge,
          fontStyle = FontStyle.Italic,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("SyncSessionList"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
      ) {
        item(key = "history-actions", contentType = "actions") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(
              onClick = { confirmClear = true },
              enabled = hasFinished,
              modifier = Modifier.testTag("ClearSyncData"),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = stringResource(id = R.string.clear_sync_data))
            }
          }
        }
        items(syncSessions, key = { it.id }, contentType = { "session" }) { session ->
          SyncSessionCard(
            session = session,
            onDeleteSession = onDeleteSession,
            modifier = Modifier.animateItem(),
          )
        }
      }
    }
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text(text = stringResource(id = R.string.clear_sync_data)) },
      text = { Text(text = stringResource(id = R.string.clear_sync_data_confirm)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmClear = false
            onClearAll()
          },
        ) {
          Text(text = stringResource(id = R.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmClear = false }) {
          Text(text = stringResource(id = R.string.cancel))
        }
      },
    )
  }
}

private data class Quintuple(
  val headerColor: Color,
  val iconColor: Color,
  @DrawableRes val iconRes: Int,
  val label: String,
  val progress: Float,
  val percentLabel: String,
  val progressColor: Color,
)
