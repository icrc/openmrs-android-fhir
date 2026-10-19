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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsEstimate
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsOption
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsRange

object DiagnosticsDialogTestTags {
  const val Dialog = "DiagnosticsDialog"
  const val Send = "DiagnosticsSend"
  const val Download = "DiagnosticsDownload"
  const val Cancel = "DiagnosticsCancel"

  fun option(option: DiagnosticsOption) = "DiagnosticsOption_${option.name}"
}

private data class OptionText(val title: Int, val description: Int)

private fun DiagnosticsOption.text() =
  when (this) {
    DiagnosticsOption.APP_LOGS -> OptionText(R.string.diag_logs, R.string.diag_logs_description)
    DiagnosticsOption.AUDIT_TRAIL ->
      OptionText(R.string.diag_audit, R.string.diag_audit_description)
    DiagnosticsOption.SYNC_INFO -> OptionText(R.string.diag_sync, R.string.diag_sync_description)
    DiagnosticsOption.UNSYNCED_RESOURCES ->
      OptionText(R.string.diag_unsynced, R.string.diag_unsynced_description)
    DiagnosticsOption.DEVICE_INFO ->
      OptionText(R.string.diag_device, R.string.diag_device_description)
  }

/**
 * What to put in the report for support. Everything is ticked at first; while the report is being
 * prepared ([busy]) the choices are locked and a progress bar shows.
 */
@Composable
fun DiagnosticsDialog(
  busy: Boolean,
  estimate: DiagnosticsEstimate?,
  onSend: (Set<DiagnosticsOption>, DiagnosticsRange) -> Unit,
  onDismiss: () -> Unit,
  /** Saves the report as a file instead of sending it; no button when `null`. */
  onDownload: ((Set<DiagnosticsOption>, DiagnosticsRange) -> Unit)? = null,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var range by rememberSaveable { mutableStateOf(DiagnosticsRange.EVERYTHING) }
  var selected by rememberSaveable {
    mutableStateOf(DiagnosticsOption.entries.map { it.name }.toSet())
  }
  val chosen = DiagnosticsOption.entries.filter { it.name in selected }.toSet()
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    modifier = Modifier.testTag(DiagnosticsDialogTestTags.Dialog),
    title = { Text(text = stringResource(R.string.send_diagnostics)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.diag_message),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        DiagnosticsOption.entries.forEach { option ->
          val text = option.text()
          val checked = option.name in selected
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth()
                .clickable(enabled = !busy, role = Role.Checkbox) {
                  selected = if (checked) selected - option.name else selected + option.name
                }
                .testTag(DiagnosticsDialogTestTags.option(option)),
          ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = !busy)
            Column(modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
              Text(text = stringResource(text.title), style = MaterialTheme.typography.bodyLarge)
              Text(
                text = stringResource(text.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              size(option, estimate, context)?.let {
                Text(
                  text = it,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
        Text(
          text = stringResource(R.string.diag_range_title),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(top = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
              DiagnosticsRange.EVERYTHING to R.string.diag_range_all,
              DiagnosticsRange.LAST_24_HOURS to R.string.diag_range_24h,
              DiagnosticsRange.LAST_7_DAYS to R.string.diag_range_7d,
            )
            .forEach { (value, label) ->
              FilterChip(
                selected = range == value,
                onClick = { range = value },
                enabled = !busy,
                label = { Text(text = stringResource(label)) },
              )
            }
        }
        Text(
          text = stringResource(R.string.diag_range_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) {
          Text(
            text = stringResource(R.string.diag_preparing),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
          )
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
      }
    },
    confirmButton = {
      Row {
        if (onDownload != null) {
          TextButton(
            onClick = { onDownload(chosen, range) },
            enabled = !busy && chosen.isNotEmpty(),
            modifier = Modifier.testTag(DiagnosticsDialogTestTags.Download),
          ) {
            Text(text = stringResource(R.string.diag_download))
          }
        }
        TextButton(
          onClick = { onSend(chosen, range) },
          enabled = !busy && chosen.isNotEmpty(),
          modifier = Modifier.testTag(DiagnosticsDialogTestTags.Send),
        ) {
          Text(text = stringResource(R.string.diag_send))
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !busy,
        modifier = Modifier.testTag(DiagnosticsDialogTestTags.Cancel),
      ) {
        Text(text = stringResource(R.string.cancel))
      }
    },
  )
}

/** What a part holds, in a few words; `null` while the counts are still being taken. */
private fun size(
  option: DiagnosticsOption,
  estimate: DiagnosticsEstimate?,
  context: android.content.Context,
): String? {
  estimate ?: return null
  return when (option) {
    DiagnosticsOption.APP_LOGS ->
      context.getString(
        R.string.diag_est_size,
        android.text.format.Formatter.formatShortFileSize(context, estimate.logsBytes),
      )
    DiagnosticsOption.AUDIT_TRAIL ->
      context.getString(R.string.diag_est_entries, estimate.auditEntries)
    DiagnosticsOption.SYNC_INFO ->
      context.getString(R.string.diag_est_sessions, estimate.syncSessions)
    DiagnosticsOption.UNSYNCED_RESOURCES ->
      context.getString(R.string.diag_est_resources, estimate.unsyncedResources)
    DiagnosticsOption.DEVICE_INFO -> null
  }
}
