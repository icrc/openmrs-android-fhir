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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.viewmodel.SettingsUiState

data class CompatibilitySummary(val supported: Int, val unsupported: Int)

/**
 * The settings for administrators and support, kept apart so the page stays simple for everyone
 * else: the server and its connection test, how often the sign-in token is checked, the sync
 * interval, and the server compatibility checks. Closed until opened, and open while a search is
 * looking through it.
 */
@Composable
internal fun AdvancedSettingsSection(
  uiState: SettingsUiState,
  tokenDelayOptions: List<Int>,
  periodicSyncDelayOptions: List<Int>,
  onTokenDelaySelected: (Int) -> Unit,
  onPeriodicSyncDelaySelected: (Int) -> Unit,
  onServerChanged: ((String?) -> Unit)?,
  compatibility: CompatibilitySummary?,
  onOpenCompatibility: (() -> Unit)?,
  onOpenFormUpdates: (() -> Unit)?,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  val searching = LocalSettingsSearch.current.query.isNotBlank()
  SettingsSection(
    title = stringResource(R.string.settings_section_advanced),
    tutorialKey = TutorialTargets.SETTINGS_ADVANCED,
    searchText =
      stringResource(R.string.settings_advanced_description) +
        " " +
        stringResource(R.string.token_check_delay_in_minutes) +
        " " +
        stringResource(R.string.periodic_sync_delay_in_minutes) +
        " " +
        stringResource(R.string.settings_compat_title) +
        " " +
        stringResource(R.string.title_form_updates) +
        " " +
        stringResource(R.string.fetch_questionnaires),
  ) {
    val shown = open || searching
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 48.dp)
          .clickable(enabled = !searching) { open = !open }
          .testTag(SettingsTestTags.AdvancedToggle),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        modifier = Modifier.weight(1f),
        text = stringResource(R.string.settings_advanced_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Icon(
        imageVector = if (shown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription =
          stringResource(
            if (shown) R.string.settings_advanced_hide else R.string.settings_advanced_show,
          ),
      )
    }
    if (shown) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      ConnectionTest(onServerChanged)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      SettingsDropdown(
        label = stringResource(R.string.token_check_delay_in_minutes),
        selectedValue = uiState.tokenCheckDelayMinutes,
        options = tokenDelayOptions,
        onOptionSelected = onTokenDelaySelected,
        testTag = SettingsTestTags.TokenDelayField,
      )
      SettingsDropdown(
        label = stringResource(R.string.periodic_sync_delay_in_minutes),
        selectedValue = uiState.periodicSyncDelayMinutes,
        options = periodicSyncDelayOptions,
        onOptionSelected = onPeriodicSyncDelaySelected,
        testTag = SettingsTestTags.PeriodicDelayField,
        enabled = uiState.isAutoSyncEnabled,
      )
      if (onOpenFormUpdates != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.title_form_updates),
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = stringResource(R.string.fetch_questionnaires_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = onOpenFormUpdates,
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(top = 4.dp)
                .testTag(SettingsTestTags.FetchQuestionnairesButton),
          ) {
            Text(text = stringResource(R.string.fetch_questionnaires))
          }
        }
      }
      if (onOpenCompatibility != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.settings_compat_title),
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text =
              if (compatibility == null) {
                stringResource(R.string.settings_compat_unchecked)
              } else {
                stringResource(
                  R.string.settings_compat_summary,
                  compatibility.supported,
                  compatibility.unsupported,
                )
              },
            style = MaterialTheme.typography.bodyMedium,
            color =
              if ((compatibility?.unsupported ?: 0) > 0) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
          OutlinedButton(
            onClick = onOpenCompatibility,
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(top = 4.dp)
                .testTag(SettingsTestTags.CompatibilityButton),
          ) {
            Text(text = stringResource(R.string.settings_compat_open))
          }
        }
      }
    }
  }
}
