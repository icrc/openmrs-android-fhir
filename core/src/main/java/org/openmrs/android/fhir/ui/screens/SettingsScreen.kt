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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
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
  const val CancelButton = "SettingsCancelButton"
  const val SaveButton = "SettingsSaveButton"
}

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
  topPadding: androidx.compose.ui.unit.Dp = 0.dp,
  modifier: Modifier = Modifier,
) {
  val startMargin = 12.dp
  val sectionSpacing = dimensionResource(R.dimen.search_patient_cardview_margin_top)
  val switchPadding = dimensionResource(R.dimen.patient_count_start_margin)
  val bottomPadding = 16.dp
  val topContentPadding = topPadding + 24.dp
  val greyButtonColor = colorResource(R.color.dashboard_cardview_textcolor)
  val greyButtonContent = colorResource(R.color.white)
  val greySwitchThumbChecked = colorResource(R.color.dashboard_cardview_textcolor)
  val greySwitchThumbUnchecked = colorResource(R.color.default_item_border)
  val greySwitchTrackChecked = colorResource(R.color.default_item_border)
  val greySwitchTrackUnchecked = colorResource(R.color.light_grey)

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(
            start = startMargin,
            end = startMargin,
            bottom = bottomPadding + 56.dp,
            top = topContentPadding,
          )
          .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
      SettingsSwitchRow(
        label = stringResource(R.string.check_network_connectivity),
        checked = uiState.isNetworkStatusVisible,
        onCheckedChange = onNetworkStatusToggle,
        testTag = SettingsTestTags.NetworkSwitch,
        switchPadding = switchPadding,
        thumbColorChecked = greySwitchThumbChecked,
        thumbColorUnchecked = greySwitchThumbUnchecked,
        trackColorChecked = greySwitchTrackChecked,
        uncheckedTrackColor = greySwitchTrackUnchecked,
      )
      SettingsSwitchRow(
        label = stringResource(R.string.enable_notifications),
        checked = uiState.isNotificationsEnabled,
        onCheckedChange = onNotificationsToggle,
        testTag = SettingsTestTags.NotificationsSwitch,
        switchPadding = switchPadding,
        thumbColorChecked = greySwitchThumbChecked,
        thumbColorUnchecked = greySwitchThumbUnchecked,
        trackColorChecked = greySwitchTrackChecked,
        uncheckedTrackColor = greySwitchTrackUnchecked,
      )

      Spacer(modifier = Modifier.height(sectionSpacing))
      SettingsDropdown(
        label = stringResource(R.string.token_check_delay_in_minutes),
        fieldLabel = stringResource(R.string.token_check_delay),
        selectedValue = uiState.tokenCheckDelayMinutes,
        options = tokenDelayOptions,
        onOptionSelected = onTokenDelaySelected,
        testTag = SettingsTestTags.TokenDelayField,
      )

      Spacer(modifier = Modifier.height(sectionSpacing))
      SettingsDropdown(
        label = stringResource(R.string.periodic_sync_delay_in_minutes),
        fieldLabel = stringResource(R.string.periodic_sync_delay),
        selectedValue = uiState.periodicSyncDelayMinutes,
        options = periodicSyncDelayOptions,
        onOptionSelected = onPeriodicSyncDelaySelected,
        testTag = SettingsTestTags.PeriodicDelayField,
      )

      Spacer(modifier = Modifier.height(sectionSpacing))
      Button(
        onClick = onInitialSyncClicked,
        enabled = !uiState.isInitialSyncInProgress,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = greyButtonColor,
            contentColor = greyButtonContent,
          ),
        modifier = Modifier.testTag(SettingsTestTags.InitialSyncButton),
      ) {
        Text(text = stringResource(R.string.initial_sync))
      }

      Spacer(modifier = Modifier.weight(1f))
    }

    Row(
      modifier =
        Modifier.fillMaxWidth()
          .align(Alignment.BottomCenter)
          .padding(start = startMargin, end = startMargin, bottom = bottomPadding),
      horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
        onClick = onCancelClicked,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = greyButtonColor,
            contentColor = greyButtonContent,
          ),
        modifier = Modifier.testTag(SettingsTestTags.CancelButton),
      ) {
        Text(text = stringResource(R.string.cancel))
      }
      Button(
        onClick = onSaveClicked,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = greyButtonColor,
            contentColor = greyButtonContent,
          ),
        modifier = Modifier.testTag(SettingsTestTags.SaveButton),
      ) {
        Text(text = stringResource(R.string.save))
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
  switchPadding: androidx.compose.ui.unit.Dp,
  thumbColorChecked: androidx.compose.ui.graphics.Color,
  thumbColorUnchecked: androidx.compose.ui.graphics.Color,
  trackColorChecked: androidx.compose.ui.graphics.Color,
  uncheckedTrackColor: androidx.compose.ui.graphics.Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(modifier = Modifier.width(switchPadding))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = thumbColorChecked,
          uncheckedThumbColor = thumbColorUnchecked,
          checkedTrackColor = trackColorChecked,
          uncheckedTrackColor = uncheckedTrackColor,
        ),
      modifier = Modifier.testTag(testTag),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
  label: String,
  fieldLabel: String,
  selectedValue: Int,
  options: List<Int>,
  onOptionSelected: (Int) -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val displayValue = selectedValue.toString()

  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(modifier = Modifier.height(4.dp))
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = !expanded },
      modifier = Modifier.fillMaxWidth(),
    ) {
      OutlinedTextField(
        readOnly = true,
        value = displayValue,
        onValueChange = {},
        label = { Text(text = fieldLabel) },
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
}
