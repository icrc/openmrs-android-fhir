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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.CustomFormSection
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.data.remote.ServerSettings
import org.openmrs.android.fhir.ui.components.FavoriteFormsDialog
import org.openmrs.android.fhir.ui.components.FormSectionsEditorDialog
import org.openmrs.android.fhir.ui.components.IdentifierTypesDialog
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/** How many identifier types are in use, and the button that opens the popup to change them. */
@Composable
internal fun IdentifierTypesSetting(
  identifierTypes: List<IdentifierType>,
  selectedIds: Set<String>,
  isLoading: Boolean,
  onOpen: () -> Unit,
  onSaved: (Set<String>) -> Unit,
) {
  var showPicker by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(R.string.settings_identifiers_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = {
        onOpen()
        showPicker = true
      },
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag(SettingsTestTags.IdentifierTypesButton),
    ) {
      Icon(
        imageVector = Icons.Default.Tag,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text =
          if (selectedIds.isEmpty()) {
            stringResource(R.string.select_identifier_types)
          } else {
            stringResource(R.string.forms_selected_count, selectedIds.size) +
              " · " +
              stringResource(R.string.select_identifier_types)
          },
      )
    }
  }
  if (showPicker) {
    IdentifierTypesDialog(
      identifierTypes = identifierTypes,
      selectedIds = selectedIds,
      isLoading = isLoading,
      onSave = {
        showPicker = false
        onSaved(it)
      },
      onDismiss = { showPicker = false },
    )
  }
}

/** The favorites picked so far and the button that opens the picker. */
@Composable
internal fun FavoriteFormsSetting(
  options: List<FormItem>,
  selectedIds: List<String>,
  onSaved: (List<String>) -> Unit,
) {
  var showPicker by remember { mutableStateOf(false) }
  val byId = remember(options) { options.associateBy { it.questionnaireId } }
  val selected = selectedIds.mapNotNull { byId[it] }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(R.string.favorite_forms_description, DemoDataStore.MAX_FAVORITE_FORMS),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (selected.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        selected.forEach { form ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Star,
              contentDescription = null,
              tint = OpenMrsTheme.colors.warning,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = form.name,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
      }
    }
    OutlinedButton(
      onClick = { showPicker = true },
      enabled = options.isNotEmpty(),
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SettingsTestTags.FavoriteFormsButton),
    ) {
      Icon(
        imageVector = Icons.Default.Star,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text =
          stringResource(
            R.string.favorite_forms_count,
            selected.size,
            DemoDataStore.MAX_FAVORITE_FORMS,
          ) + " · " + stringResource(R.string.favorite_forms_choose),
      )
    }
  }
  if (showPicker) {
    FavoriteFormsDialog(
      forms = options,
      selectedIds = selected.map { it.questionnaireId },
      maxFavorites = DemoDataStore.MAX_FAVORITE_FORMS,
      onSave = {
        showPicker = false
        onSaved(it)
      },
      onDismiss = { showPicker = false },
    )
  }
}

/** Whose sections the Create Encounter screen uses, with the way to change or undo it. */
@Composable
internal fun FormSectionsSetting(
  options: List<FormItem>,
  custom: List<CustomFormSection>?,
  defaults: List<CustomFormSection>,
  onSaved: (List<CustomFormSection>) -> Unit,
  onReset: () -> Unit,
) {
  var showEditor by remember { mutableStateOf(false) }
  var confirmReset by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(R.string.form_sections_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text =
        if (custom != null) {
          stringResource(R.string.form_sections_custom_status, custom.size)
        } else {
          stringResource(R.string.form_sections_default_status)
        },
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.testTag(SettingsTestTags.FormSectionsStatus),
    )
    OutlinedButton(
      onClick = { showEditor = true },
      enabled = options.isNotEmpty(),
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SettingsTestTags.FormSectionsButton),
    ) {
      Icon(
        imageVector = Icons.Default.Edit,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = stringResource(R.string.form_sections_customize))
    }
    if (custom != null) {
      TextButton(
        onClick = { confirmReset = true },
        modifier = Modifier.testTag(SettingsTestTags.FormSectionsReset),
      ) {
        Text(text = stringResource(R.string.form_sections_reset))
      }
    }
  }
  if (showEditor) {
    FormSectionsEditorDialog(
      forms = options,
      initial = custom ?: defaults,
      onSave = {
        showEditor = false
        onSaved(it)
      },
      onDismiss = { showEditor = false },
    )
  }
  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text(text = stringResource(R.string.form_sections_reset)) },
      text = { Text(text = stringResource(R.string.form_sections_reset_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmReset = false
            onReset()
          },
        ) {
          Text(text = stringResource(R.string.form_sections_reset))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmReset = false }) {
          Text(text = stringResource(R.string.cancel))
        }
      },
    )
  }
}

/**
 * Whether the regular Logout also deletes the data of the device. Changing it asks first, and the
 * question says what the new setting means (and, when turning deletion on, what is waiting to be
 * uploaded and would be lost with it).
 */
@Composable
internal fun ClearDataOnLogoutSetting(
  checked: Boolean,
  unsyncedCount: Int,
  onRequested: () -> Unit,
  onConfirmed: (Boolean) -> Unit,
) {
  var target by remember { mutableStateOf<Boolean?>(null) }
  DangerCard(title = stringResource(R.string.logout_section_title)) {
    SettingsSwitchRow(
      label = stringResource(R.string.clear_on_logout_title),
      description = stringResource(R.string.clear_on_logout_description),
      checked = checked,
      onCheckedChange = {
        onRequested()
        target = it
      },
      testTag = SettingsTestTags.ClearDataSwitch,
      danger = true,
    )
  }
  target?.let { turnOn ->
    AlertDialog(
      onDismissRequest = { target = null },
      shape = RoundedCornerShape(28.dp),
      icon = {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
      },
      title = {
        Text(
          text =
            stringResource(
              if (turnOn) R.string.clear_on_logout_on_title else R.string.clear_on_logout_off_title,
            ),
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text =
              stringResource(
                if (turnOn) {
                  R.string.clear_on_logout_on_message
                } else {
                  R.string.clear_on_logout_off_message
                },
              ),
          )
          if (turnOn && unsyncedCount > 0) {
            Text(
              text =
                pluralStringResource(R.plurals.logout_pending_lost, unsyncedCount, unsyncedCount),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            target = null
            onConfirmed(turnOn)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag(SettingsTestTags.ClearDataConfirm),
        ) {
          Text(
            text =
              stringResource(
                if (turnOn) {
                  R.string.clear_on_logout_on_button
                } else {
                  R.string.clear_on_logout_off_button
                },
              ),
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { target = null }) { Text(text = stringResource(R.string.cancel)) }
      },
    )
  }
}

/**
 * The last thing on the page: leave and wipe the device. It looks different on purpose (the error
 * color, a warning icon) and asks once more, spelling out what is lost, and how many changes were
 * never uploaded.
 */
@Composable
internal fun DangerZone(unsyncedCount: Int, onRequested: () -> Unit, onConfirmed: () -> Unit) {
  var confirming by remember { mutableStateOf(false) }
  DangerCard(title = stringResource(R.string.logout_delete_title)) {
    Text(
      text = stringResource(R.string.logout_delete_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      onClick = {
        onRequested()
        confirming = true
      },
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError,
        ),
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SettingsTestTags.LogoutDeleteButton),
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_logout),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = stringResource(R.string.logout_delete_button))
    }
  }
  if (confirming) {
    AlertDialog(
      onDismissRequest = { confirming = false },
      shape = RoundedCornerShape(28.dp),
      icon = {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
      },
      title = { Text(text = stringResource(R.string.logout_delete_confirm_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(text = stringResource(R.string.logout_delete_confirm_message))
          if (unsyncedCount > 0) {
            Text(
              modifier = Modifier.testTag(SettingsTestTags.LogoutDeleteUnsynced),
              text =
                pluralStringResource(
                  R.plurals.logout_delete_unsynced,
                  unsyncedCount,
                  unsyncedCount,
                ),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            confirming = false
            onConfirmed()
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag(SettingsTestTags.LogoutDeleteConfirm),
        ) {
          Text(text = stringResource(R.string.logout_delete_confirm_button))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirming = false }) {
          Text(text = stringResource(R.string.cancel))
        }
      },
    )
  }
}

/**
 * "Can this device reach the server?": the address in use and a button that asks it, with the same
 * check and the same answers as the login page's server dialog.
 */
@Composable
internal fun ConnectionTest(onServerChanged: ((String?) -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val server = remember { ServerSettings.activeUrl(context) }
  var state by remember { mutableStateOf<TestState>(TestState.Idle) }
  var choosingServer by remember { mutableStateOf(false) }
  var confirmServer by remember { mutableStateOf<String?>(null) }
  var confirming by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(R.string.settings_connection_description, server),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = {
        state = TestState.Running
        scope.launch { state = TestState.Done(ServerSettings.testConnection(server)) }
      },
      enabled = state != TestState.Running,
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag(SettingsTestTags.ConnectionTestButton),
    ) {
      Icon(
        imageVector = Icons.Default.Sync,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = stringResource(R.string.test_connection))
    }
    TestStatus(state)
    if (onServerChanged != null) {
      OutlinedButton(
        onClick = { choosingServer = true },
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(SettingsTestTags.ChangeServerButton),
      ) {
        Icon(
          imageVector = Icons.Default.Dns,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.settings_change_server))
      }
    }
  }
  if (choosingServer && onServerChanged != null) {
    ServerSettingsDialog(
      onDismiss = { choosingServer = false },
      showLanguage = false,
      onSave = { url ->
        choosingServer = false
        confirming = true
        confirmServer = url
      },
    )
  }
  if (confirming && onServerChanged != null) {
    // `null` is the build's default server.
    val target = confirmServer ?: ServerSettings.defaultUrl(context)
    AlertDialog(
      onDismissRequest = { confirming = false },
      title = { Text(text = stringResource(R.string.server_change_title)) },
      text = { Text(text = stringResource(R.string.server_change_message, target)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirming = false
            onServerChanged(confirmServer)
          },
          modifier = Modifier.testTag("SettingsChangeServerConfirm"),
        ) {
          Text(text = stringResource(R.string.server_change_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirming = false }) {
          Text(text = stringResource(R.string.cancel))
        }
      },
    )
  }
}
