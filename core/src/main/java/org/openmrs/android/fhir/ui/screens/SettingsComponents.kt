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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget

/** What is typed in the search box and how many sections were shown for it (see the screen). */
internal class SettingsSearch(val query: String) {
  var shown = 0
}

internal val LocalSettingsSearch = compositionLocalOf { SettingsSearch("") }

@Composable
internal fun SettingsSection(
  title: String,
  tutorialKey: String? = null,
  /** More of what the section is about, so a search finds it by more than its title. */
  searchText: String = "",
  content: @Composable () -> Unit,
) {
  val search = LocalSettingsSearch.current
  if (
    search.query.isNotBlank() &&
      !title.contains(search.query.trim(), ignoreCase = true) &&
      !searchText.contains(search.query.trim(), ignoreCase = true)
  ) {
    return
  }
  search.shown++
  Column(
    modifier = if (tutorialKey != null) Modifier.tutorialTarget(tutorialKey) else Modifier,
  ) {
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
internal fun SettingsSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
  description: String? = null,
  danger: Boolean = false,
  enabled: Boolean = true,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (description != null) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(modifier = Modifier.width(16.dp))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors =
        if (danger) {
          SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.error,
            checkedThumbColor = MaterialTheme.colorScheme.onError,
            checkedBorderColor = MaterialTheme.colorScheme.error,
          )
        } else {
          SwitchDefaults.colors()
        },
      modifier = Modifier.testTag(testTag),
    )
  }
}

/** A card for what can lose data: the error color around it and a warning in its title. */
@Composable
internal fun DangerCard(title: String, content: @Composable () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp),
        )
        Text(
          modifier = Modifier.padding(start = 8.dp),
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.error,
        )
      }
      content()
    }
  }
}

/** A short explanation, a button and (while it runs) a progress bar. */
/** A line saying why an action is off when the server cannot be reached. */
@Composable
internal fun offlineNote(serverReachable: Boolean): String =
  if (serverReachable) "" else "\n" + stringResource(R.string.sync_unavailable_offline)

@Composable
internal fun SettingsAction(
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
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(testTag),
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
internal fun SettingsDropdown(
  label: String,
  selectedValue: Int,
  options: List<Int>,
  onOptionSelected: (Int) -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  var expanded by remember { mutableStateOf(false) }

  // The label is above the field, not in its border: a long label (or a large font) wraps there
  // instead of breaking the outline.
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
    )
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { if (enabled) expanded = !expanded },
      modifier = Modifier.fillMaxWidth(),
    ) {
      OutlinedTextField(
        readOnly = true,
        enabled = enabled,
        value = selectedValue.toString(),
        onValueChange = {},
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
}
