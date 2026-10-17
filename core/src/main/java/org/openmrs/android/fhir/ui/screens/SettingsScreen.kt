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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
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
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.CustomFormSection
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.ColorVisionMode
import org.openmrs.android.fhir.extensions.ThemeMode
import org.openmrs.android.fhir.ui.components.FavoriteFormsDialog
import org.openmrs.android.fhir.ui.components.FormSectionsEditorDialog
import org.openmrs.android.fhir.ui.components.IdentifierTypesDialog
import org.openmrs.android.fhir.ui.components.LanguagePicker
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.SettingsUiState

object SettingsDefaults {
  val TokenDelayOptions = listOf(1, 2, 4, 5, 10)
  val PeriodicSyncDelayOptions = listOf(5, 10, 15, 20, 30, 60)
}

object SettingsTestTags {
  const val NetworkSwitch = "SettingsNetworkSwitch"
  const val NotificationsSwitch = "SettingsNotificationsSwitch"
  const val TokenDelayField = "SettingsTokenDelayField"
  const val PeriodicDelayField = "SettingsPeriodicDelayField"
  const val AutoSyncSwitch = "SettingsAutoSyncSwitch"
  const val FavoriteFormsButton = "SettingsFavoriteFormsButton"
  const val IdentifierTypesButton = "SettingsIdentifierTypesButton"
  const val FormSectionsButton = "SettingsFormSectionsButton"
  const val FormSectionsStatus = "SettingsFormSectionsStatus"
  const val FormSectionsReset = "SettingsFormSectionsReset"
  const val InitialSyncButton = "SettingsInitialSyncButton"
  const val FetchQuestionnairesButton = "SettingsFetchQuestionnairesButton"
  const val ResetOfflineLoginButton = "SettingsResetOfflineLoginButton"
  const val ThemeSystem = "SettingsThemeSystem"
  const val ThemeLight = "SettingsThemeLight"
  const val ThemeDark = "SettingsThemeDark"
  const val EmbeddedFormsSwitch = "SettingsEmbeddedFormsSwitch"
  const val ColorVisionStandard = "SettingsColorVisionStandard"
  const val ColorVisionColorBlind = "SettingsColorVisionColorBlind"
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
  embeddedFormsOnly: Boolean = true,
  onEmbeddedFormsOnlyChange: ((Boolean) -> Unit)? = null,
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  onThemeModeSelected: ((ThemeMode) -> Unit)? = null,
  colorVision: ColorVisionMode = ColorVisionMode.STANDARD,
  onColorVisionSelected: ((ColorVisionMode) -> Unit)? = null,
  offlineLoginEnabled: Boolean? = null,
  onResetOfflineLoginClicked: (() -> Unit)? = null,
  onAutoSyncToggle: (Boolean) -> Unit = {},
  favoriteFormOptions: List<FormItem>? = null,
  favoriteFormIds: List<String> = emptyList(),
  onFavoriteFormsSaved: ((List<String>) -> Unit)? = null,
  identifierTypes: List<IdentifierType>? = null,
  selectedIdentifierIds: Set<String> = emptySet(),
  isLoadingIdentifierTypes: Boolean = false,
  onIdentifierTypesRequested: () -> Unit = {},
  onIdentifierTypesSaved: ((Set<String>) -> Unit)? = null,
  customFormSections: List<CustomFormSection>? = null,
  defaultFormSections: List<CustomFormSection> = emptyList(),
  onFormSectionsSaved: ((List<CustomFormSection>) -> Unit)? = null,
  onFormSectionsReset: (() -> Unit)? = null,
  selectedLanguage: AppLanguage? = null,
  onLanguageSelected: ((AppLanguage) -> Unit)? = null,
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

      if (selectedLanguage != null && onLanguageSelected != null) {
        SettingsSection(title = stringResource(R.string.settings_language)) {
          LanguagePicker(selected = selectedLanguage, onSelected = onLanguageSelected)
        }
      }

      if (onThemeModeSelected != null) {
        SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
          ThemeModeChoice(selected = themeMode, onSelected = onThemeModeSelected)
          if (onColorVisionSelected != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ColorVisionChoice(selected = colorVision, onSelected = onColorVisionSelected)
          }
        }
      }

      SettingsSection(title = stringResource(R.string.settings_section_sync)) {
        SettingsSwitchRow(
          label = stringResource(R.string.settings_auto_sync),
          checked = uiState.isAutoSyncEnabled,
          onCheckedChange = onAutoSyncToggle,
          testTag = SettingsTestTags.AutoSyncSwitch,
        )
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

      if (onResetOfflineLoginClicked != null) {
        SettingsSection(title = stringResource(R.string.settings_section_security)) {
          SettingsAction(
            description =
              stringResource(
                when (offlineLoginEnabled) {
                  true -> R.string.offline_login_enabled_status
                  false -> R.string.offline_login_disabled_status
                  null -> R.string.offline_login_not_set_status
                },
              ),
            buttonText = stringResource(R.string.reset_offline_login),
            icon = Icons.Default.Refresh,
            onClick = onResetOfflineLoginClicked,
            enabled = offlineLoginEnabled != null,
            inProgress = false,
            testTag = SettingsTestTags.ResetOfflineLoginButton,
          )
        }
      }

      if (identifierTypes != null && onIdentifierTypesSaved != null) {
        SettingsSection(title = stringResource(R.string.settings_section_identifiers)) {
          IdentifierTypesSetting(
            identifierTypes = identifierTypes,
            selectedIds = selectedIdentifierIds,
            isLoading = isLoadingIdentifierTypes,
            onOpen = onIdentifierTypesRequested,
            onSaved = onIdentifierTypesSaved,
          )
        }
      }

      if (favoriteFormOptions != null && onFavoriteFormsSaved != null) {
        SettingsSection(title = stringResource(R.string.favorite_forms)) {
          FavoriteFormsSetting(
            options = favoriteFormOptions,
            selectedIds = favoriteFormIds,
            onSaved = onFavoriteFormsSaved,
          )
        }
      }

      val showSections =
        favoriteFormOptions != null && onFormSectionsSaved != null && onFormSectionsReset != null
      val showFetch = onFetchQuestionnairesClicked != null && !embeddedFormsOnly
      if (onEmbeddedFormsOnlyChange != null || showSections || showFetch) {
        SettingsSection(title = stringResource(R.string.settings_section_forms)) {
          if (onEmbeddedFormsOnlyChange != null) {
            SettingsSwitchRow(
              label = stringResource(R.string.embedded_forms_only),
              description = stringResource(R.string.embedded_forms_only_description),
              checked = embeddedFormsOnly,
              onCheckedChange = onEmbeddedFormsOnlyChange,
              testTag = SettingsTestTags.EmbeddedFormsSwitch,
            )
          }
          if (showFetch && onFetchQuestionnairesClicked != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
          if (showSections) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
              text = stringResource(R.string.form_sections_title),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            FormSectionsSetting(
              options = favoriteFormOptions.orEmpty(),
              custom = customFormSections,
              defaults = defaultFormSections,
              onSaved = onFormSectionsSaved!!,
              onReset = onFormSectionsReset!!,
            )
          }
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
  description: String? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
      modifier = Modifier.testTag(testTag),
    )
  }
}

/** System / Light / Dark as three chips; the choice is applied when the settings are saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeChoice(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorVisionChoice(selected: ColorVisionMode, onSelected: (ColorVisionMode) -> Unit) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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

/** How many identifier types are in use, and the button that opens the popup to change them. */
@Composable
private fun IdentifierTypesSetting(
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
        Modifier.fillMaxWidth().height(48.dp).testTag(SettingsTestTags.IdentifierTypesButton),
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
private fun FavoriteFormsSetting(
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
        Modifier.fillMaxWidth().height(48.dp).testTag(SettingsTestTags.FavoriteFormsButton),
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
private fun FormSectionsSetting(
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
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(SettingsTestTags.FormSectionsButton),
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
  enabled: Boolean = true,
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { if (enabled) expanded = !expanded },
    modifier = modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      readOnly = true,
      enabled = enabled,
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
