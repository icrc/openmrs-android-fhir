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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R

object AutoSyncDelayTestTags {
  const val Dialog = "AutoSyncDelayDialog"
  const val TurnOff = "AutoSyncTurnOff"

  fun option(minutes: Int) = "AutoSyncDelay_$minutes"
}

private val DelayOptions =
  listOf(
    5 to R.string.auto_sync_delay_5_minutes,
    30 to R.string.auto_sync_delay_30_minutes,
    60 to R.string.auto_sync_delay_1_hour,
    120 to R.string.auto_sync_delay_2_hours,
  )

/**
 * Postpones the next automatic sync by 5 minutes, 30 minutes, 1 hour or 2 hours, or turns the
 * automatic sync off altogether (the same switch as in Settings).
 */
@Composable
fun AutoSyncDelayDialog(
  onDelay: (minutes: Int) -> Unit,
  onTurnOff: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag(AutoSyncDelayTestTags.Dialog),
    shape = RoundedCornerShape(28.dp),
    title = { Text(text = stringResource(R.string.auto_sync_delay_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DelayOptions.forEach { (minutes, label) ->
          DelayRow(
            text = stringResource(label),
            onClick = { onDelay(minutes) },
            modifier = Modifier.testTag(AutoSyncDelayTestTags.option(minutes)),
          )
        }
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 8.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
        DelayRow(
          text = stringResource(R.string.auto_sync_turn_off),
          hint = stringResource(R.string.auto_sync_turn_off_hint),
          color = MaterialTheme.colorScheme.error,
          onClick = onTurnOff,
          modifier = Modifier.testTag(AutoSyncDelayTestTags.TurnOff),
        )
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun DelayRow(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  hint: String? = null,
  color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color)
      if (hint != null) {
        Text(
          text = hint,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
