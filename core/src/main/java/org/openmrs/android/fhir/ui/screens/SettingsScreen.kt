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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.viewmodel.SettingsUiState

object SettingsDefaults {
  val TokenDelayOptions = listOf(1, 2, 4, 5, 10)
  val PeriodicSyncDelayOptions = listOf(15, 20, 25, 30)
}

object SettingsTestTags {
  const val NetworkSwitch = "SettingsNetworkSwitch"
  const val NotificationsSwitch = "SettingsNotificationsSwitch"
  const val TokenDelayField = "SettingsTokenDelayField"
  const val PeriodicDelayField = "SettingsPeriodicDelayField"
  const val InitialSyncButton = "SettingsInitialSyncButton"
  const val FetchQuestionnairesButton = "SettingsFetchQuestionnairesButton"
  const val CancelButton = "SettingsCancelButton"
  const val SaveButton = "SettingsSaveButton"
}

/**
 * The settings screen: grouped cards (general, synchronization, forms) above a fixed Save / Cancel
 * bar. [topPadding] is the height of the app bar the content is drawn under.
 */
@Composable
fun SettingsScreen(
  uiState: SettingsUiState,
  tokenDelayOptions: List<Int>,
  periodicSyncDelayOptions: List<Int>,
  onNetworkStatusToggle: (Boolean) -> Unit,
  onNotificationsToggle: (Boolean) -> Unit,
  onTokenDelaySelected: (Int) -> Unit,
  onPeriodicSyncDelaySelected: (Int) -> Unit,
  onInitialSyncClicked: () -> Unit,
  onCancelClicked: () -> Unit,
  onSaveClicked: () -> Unit,
  topPadding: Dp = 0.dp,
  isFetchingQuestionnaires: Boolean = false,
  onFetchQuestionnairesClicked: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = topPadding + 16.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      SettingsSection(title = stringResource(R.string.settings_section_general)) {
        SettingsSwitchRow(
          label = stringResource(R.string.check_network_connectivity),
          checked = uiState.isNetworkStatusVisible,
          onCheckedChange = onNetworkStatusToggle,
          testTag = SettingsTestTags.NetworkSwitch,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSwitchRow(
          label = stringResource(R.string.enable_notifications),
          checked = uiState.isNotificationsEnabled,
          onCheckedChange = onNotificationsToggle,
          testTag = SettingsTestTags.NotificationsSwitch,
        )
      }

      SettingsSection(title = stringResource(R.string.settings_section_sync)) {
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
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsAction(
          description = stringResource(R.string.settings_initial_sync_description),
          buttonText = stringResource(R.string.initial_sync),
          icon = Icons.Default.Sync,
          onClick = onInitialSyncClicked,
          enabled = !uiState.isInitialSyncInProgress,
          inProgress = uiState.isInitialSyncInProgress,
          testTag = SettingsTestTags.InitialSyncButton,
        )
      }

      if (onFetchQuestionnairesClicked != null) {
        SettingsSection(title = stringResource(R.string.settings_section_forms)) {
          SettingsAction(
            description = stringResource(R.string.fetch_questionnaires_description),
            buttonText =
              stringResource(
                if (isFetchingQuestionnaires) {
                  R.string.fetching_questionnaires
                } else {
                  R.string.fetch_questionnaires
                },
              ),
            icon = Icons.Default.Refresh,
            onClick = onFetchQuestionnairesClicked,
            enabled = !isFetchingQuestionnaires,
            inProgress = isFetchingQuestionnaires,
            testTag = SettingsTestTags.FetchQuestionnairesButton,
          )
        }
      }
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onCancelClicked,
          modifier = Modifier.weight(1f).height(48.dp).testTag(SettingsTestTags.CancelButton),
        ) {
          Text(text = stringResource(R.string.cancel))
        }
        Button(
          onClick = onSaveClicked,
          modifier = Modifier.weight(1f).height(48.dp).testTag(SettingsTestTags.SaveButton),
        ) {
          Text(text = stringResource(R.string.save))
        }
      }
    }
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  Column {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        content()
      }
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(16.dp))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}

/** A short explanation, a button and (while it runs) a progress bar. */
@Composable
private fun SettingsAction(
  description: String,
  buttonText: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  enabled: Boolean,
  inProgress: Boolean,
  testTag: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(testTag),
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = buttonText)
    }
    if (inProgress) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
  label: String,
  selectedValue: Int,
  options: List<Int>,
  onOptionSelected: (Int) -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      readOnly = true,
      value = selectedValue.toString(),
      onValueChange = {},
      label = { Text(text = label) },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier =
        Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag(testTag),
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
    )
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(text = option.toString()) },
          onClick = {
            onOptionSelected(option)
            expanded = false
          },
        )
      }
    }
  }
}
