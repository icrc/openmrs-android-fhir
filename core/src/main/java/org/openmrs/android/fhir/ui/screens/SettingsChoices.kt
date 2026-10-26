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
package org.openmrs.android.fhir.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.sync.SyncMode
import org.openmrs.android.fhir.extensions.ColorVisionMode
import org.openmrs.android.fhir.extensions.ThemeMode

/** What the app lock is set to; shown once the offline login is on. */
data class AppLockUi(
  val delaySeconds: Int,
  val lockOnScreenOff: Boolean,
  val hideInRecents: Boolean,
  val delayOptions: List<Int>,
)

/** How long the app may be out of sight before it asks again, and what else locks or hides it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AppLockOptions(
  state: AppLockUi,
  onDelaySelected: (Int) -> Unit,
  onLockOnScreenOffChange: (Boolean) -> Unit,
  onHideInRecentsChange: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(R.string.app_lock_delay),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(R.string.app_lock_delay_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        state.delayOptions.forEach { seconds ->
          FilterChip(
            selected = state.delaySeconds == seconds,
            onClick = { onDelaySelected(seconds) },
            label = {
              Text(
                text =
                  when (seconds) {
                    0 -> stringResource(R.string.app_lock_delay_immediately)
                    in 1..59 -> stringResource(R.string.app_lock_delay_seconds, seconds)
                    else -> stringResource(R.string.app_lock_delay_minutes, seconds / 60)
                  },
              )
            },
            modifier = Modifier.testTag("SettingsLockDelay_$seconds"),
          )
        }
      }
    }
    SettingsSwitchRow(
      label = stringResource(R.string.app_lock_screen_off),
      description = stringResource(R.string.app_lock_screen_off_description),
      checked = state.lockOnScreenOff,
      onCheckedChange = onLockOnScreenOffChange,
      testTag = SettingsTestTags.LockOnScreenOffSwitch,
    )
    SettingsSwitchRow(
      label = stringResource(R.string.app_lock_hide_recents),
      description = stringResource(R.string.app_lock_hide_recents_description),
      checked = state.hideInRecents,
      onCheckedChange = onHideInRecentsChange,
      testTag = SettingsTestTags.HideInRecentsSwitch,
    )
  }
}

/** What the automatic sync does: upload only, or upload and download. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AutoSyncModeChoice(
  selected: SyncMode,
  enabled: Boolean,
  onSelected: (SyncMode) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = stringResource(R.string.settings_auto_sync_mode),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(R.string.settings_auto_sync_mode_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      listOf(
          SyncMode.BOTH to R.string.sync_mode_both,
          SyncMode.UPLOAD_ONLY to R.string.sync_mode_upload_only,
        )
        .forEach { (mode, label) ->
          FilterChip(
            selected = selected == mode,
            onClick = { onSelected(mode) },
            enabled = enabled,
            label = { Text(text = stringResource(label)) },
            modifier = Modifier.testTag("SettingsAutoSyncMode_${mode.value}"),
          )
        }
    }
  }
}

/** System / Light / Dark as three chips; the choice is applied at once. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ThemeModeChoice(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    listOf(
        Triple(ThemeMode.SYSTEM, R.string.theme_system, SettingsTestTags.ThemeSystem),
        Triple(ThemeMode.LIGHT, R.string.theme_light, SettingsTestTags.ThemeLight),
        Triple(ThemeMode.DARK, R.string.theme_dark, SettingsTestTags.ThemeDark),
      )
      .forEach { (mode, label, tag) ->
        FilterChip(
          selected = selected == mode,
          onClick = { onSelected(mode) },
          label = { Text(text = stringResource(label)) },
          modifier = Modifier.testTag(tag),
        )
      }
  }
}

/**
 * Standard or color-blind palette. Each chip shows the three status colors (done / warning /
 * problem) of its palette, so the difference can be judged before saving.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ColorVisionChoice(selected: ColorVisionMode, onSelected: (ColorVisionMode) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = stringResource(R.string.color_vision_title),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(R.string.color_vision_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      listOf(
          Triple(
            ColorVisionMode.STANDARD,
            R.string.color_vision_standard,
            listOf(0xFF198038, 0xFFF1C21B, 0xFFDA1E28),
          ),
          Triple(
            ColorVisionMode.COLOR_BLIND,
            R.string.color_vision_color_blind,
            listOf(0xFF0072B2, 0xFFE69F00, 0xFFB34700),
          ),
        )
        .forEach { (mode, label, swatches) ->
          FilterChip(
            selected = selected == mode,
            onClick = { onSelected(mode) },
            label = { Text(text = stringResource(label)) },
            leadingIcon = {
              Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                swatches.forEach {
                  Box(
                    modifier =
                      Modifier.size(12.dp)
                        .background(androidx.compose.ui.graphics.Color(it), CircleShape),
                  )
                }
              }
            },
            modifier =
              Modifier.testTag(
                if (mode == ColorVisionMode.STANDARD) {
                  SettingsTestTags.ColorVisionStandard
                } else {
                  SettingsTestTags.ColorVisionColorBlind
                },
              ),
          )
        }
    }
  }
}
