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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.CustomFormSection
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.data.remote.ServerSettings
import org.openmrs.android.fhir.data.sync.SyncMode
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.ColorVisionMode
import org.openmrs.android.fhir.extensions.ThemeMode
import org.openmrs.android.fhir.ui.components.FavoriteFormsDialog
import org.openmrs.android.fhir.ui.components.FormSectionsEditorDialog
import org.openmrs.android.fhir.ui.components.IdentifierTypesDialog
import org.openmrs.android.fhir.ui.components.LanguagePicker
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.SettingsUiState

object SettingsDefaults {
  val TokenDelayOptions = listOf(1, 2, 4, 5, 10)
  val PeriodicSyncDelayOptions = listOf(5, 10, 15, 20, 30, 60)
}

object SettingsTestTags {
  const val NetworkSwitch = "SettingsNetworkSwitch"
  const val ConnectionTestButton = "SettingsConnectionTestButton"
  const val NotificationsSwitch = "SettingsNotificationsSwitch"
  const val TokenDelayField = "SettingsTokenDelayField"
  const val PeriodicDelayField = "SettingsPeriodicDelayField"
  const val AutoSyncSwitch = "SettingsAutoSyncSwitch"
  const val LiveModeSwitch = "SettingsLiveModeSwitch"
  const val KeepLivePatientsSwitch = "SettingsKeepLivePatientsSwitch"
  const val AutomaticVisitsSwitch = "SettingsAutomaticVisitsSwitch"
  const val ShareMyDataSwitch = "SettingsShareMyDataSwitch"
  const val ChangeServerButton = "SettingsChangeServerButton"
  const val RememberLocationSwitch = "SettingsRememberLocationSwitch"
  const val FavoriteFormsButton = "SettingsFavoriteFormsButton"
  const val IdentifierTypesButton = "SettingsIdentifierTypesButton"
  const val FormSectionsButton = "SettingsFormSectionsButton"
  const val FormSectionsStatus = "SettingsFormSectionsStatus"
  const val FormSectionsReset = "SettingsFormSectionsReset"
  const val InitialSyncButton = "SettingsInitialSyncButton"
  const val FetchQuestionnairesButton = "SettingsFetchQuestionnairesButton"
  const val ResetOfflineLoginButton = "SettingsResetOfflineLoginButton"
  const val AutoSyncUnmeteredSwitch = "SettingsAutoSyncUnmeteredSwitch"
  const val AutoSyncBatterySwitch = "SettingsAutoSyncBatterySwitch"
  const val LockOnScreenOffSwitch = "SettingsLockOnScreenOffSwitch"
  const val HideInRecentsSwitch = "SettingsHideInRecentsSwitch"
  const val ThemeSystem = "SettingsThemeSystem"
  const val ThemeLight = "SettingsThemeLight"
  const val ThemeDark = "SettingsThemeDark"
  const val LogoutDeleteButton = "SettingsLogoutDeleteButton"
  const val ClearDataSwitch = "SettingsClearDataOnLogoutSwitch"
  const val ClearDataConfirm = "SettingsClearDataOnLogoutConfirm"
  const val LogoutDeleteConfirm = "SettingsLogoutDeleteConfirm"
  const val LogoutDeleteUnsynced = "SettingsLogoutDeleteUnsynced"
  const val EmbeddedFormsSwitch = "SettingsEmbeddedFormsSwitch"
  const val ColorVisionStandard = "SettingsColorVisionStandard"
  const val ColorVisionColorBlind = "SettingsColorVisionColorBlind"
  const val BackButton = "SettingsBackButton"
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
  /** Every setting is kept the moment it changes: there is only a way back. */
  onBackClicked: () -> Unit,
  topPadding: Dp = 0.dp,
  isFetchingQuestionnaires: Boolean = false,
  onFetchQuestionnairesClicked: (() -> Unit)? = null,
  embeddedFormsOnly: Boolean = true,
  fetchQuestionnairesAvailable: Boolean = true,
  /** The server can be reached: the actions that talk to it are available. */
  serverReachable: Boolean = true,
  onEmbeddedFormsOnlyChange: ((Boolean) -> Unit)? = null,
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  onThemeModeSelected: ((ThemeMode) -> Unit)? = null,
  colorVision: ColorVisionMode = ColorVisionMode.STANDARD,
  onColorVisionSelected: ((ColorVisionMode) -> Unit)? = null,
  offlineLoginEnabled: Boolean? = null,
  onResetOfflineLoginClicked: (() -> Unit)? = null,
  /** The offline login is on but the device has no fingerprint, face or screen lock any more. */
  offlineLoginUnavailable: Boolean = false,
  appLock: AppLockUi? = null,
  onLockDelaySelected: (Int) -> Unit = {},
  onLockOnScreenOffChange: (Boolean) -> Unit = {},
  onHideInRecentsChange: (Boolean) -> Unit = {},
  unsyncedCount: Int = 0,
  clearDataOnLogout: Boolean = true,
  onClearDataOnLogoutChange: ((Boolean) -> Unit)? = null,
  autoSyncUnmeteredOnly: Boolean = false,
  autoSyncSkipLowBattery: Boolean = true,
  onAutoSyncUnmeteredOnlyChange: (Boolean) -> Unit = {},
  onAutoSyncSkipLowBatteryChange: (Boolean) -> Unit = {},
  onLogoutAndDeleteRequested: (() -> Unit)? = null,
  onLogoutAndDeleteConfirmed: (() -> Unit)? = null,
  onAutoSyncToggle: (Boolean) -> Unit = {},
  onAutoSyncModeSelected: (SyncMode) -> Unit = {},
  onLiveModeToggle: (Boolean) -> Unit = {},
  onKeepLivePatientsToggle: (Boolean) -> Unit = {},
  onAutomaticVisitsToggle: (Boolean) -> Unit = {},
  onShareMyDataToggle: (Boolean) -> Unit = {},
  onRememberLocationToggle: (Boolean) -> Unit = {},
  /** Called with the new server (`null`: the build's default) once the user confirmed. */
  onServerChanged: ((String?) -> Unit)? = null,
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
          label = stringResource(R.string.enable_notifications),
          checked = uiState.isNotificationsEnabled,
          onCheckedChange = onNotificationsToggle,
          testTag = SettingsTestTags.NotificationsSwitch,
        )
      }

      if (selectedLanguage != null && onLanguageSelected != null) {
        SettingsSection(
          title = stringResource(R.string.settings_language),
          tutorialKey = TutorialTargets.SETTINGS_LANGUAGE,
        ) {
          LanguagePicker(selected = selectedLanguage, onSelected = onLanguageSelected)
        }
      }

      if (onThemeModeSelected != null) {
        SettingsSection(
          title = stringResource(R.string.settings_section_appearance),
          tutorialKey = TutorialTargets.SETTINGS_APPEARANCE,
        ) {
          ThemeModeChoice(selected = themeMode, onSelected = onThemeModeSelected)
          if (onColorVisionSelected != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ColorVisionChoice(selected = colorVision, onSelected = onColorVisionSelected)
          }
        }
      }

      SettingsSection(
        title = stringResource(R.string.settings_section_sync),
        tutorialKey = TutorialTargets.SETTINGS_SYNC,
      ) {
        SettingsSwitchRow(
          label = stringResource(R.string.check_network_connectivity),
          checked = uiState.isNetworkStatusVisible,
          onCheckedChange = onNetworkStatusToggle,
          testTag = SettingsTestTags.NetworkSwitch,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ConnectionTest(onServerChanged)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSwitchRow(
          label = stringResource(R.string.settings_auto_sync),
          checked = uiState.isAutoSyncEnabled,
          onCheckedChange = onAutoSyncToggle,
          testTag = SettingsTestTags.AutoSyncSwitch,
        )
        AutoSyncModeChoice(
          selected = uiState.autoSyncMode,
          enabled = uiState.isAutoSyncEnabled,
          onSelected = onAutoSyncModeSelected,
        )
        SettingsSwitchRow(
          label = stringResource(R.string.settings_auto_sync_unmetered),
          description = stringResource(R.string.settings_auto_sync_unmetered_description),
          checked = autoSyncUnmeteredOnly,
          onCheckedChange = onAutoSyncUnmeteredOnlyChange,
          testTag = SettingsTestTags.AutoSyncUnmeteredSwitch,
          enabled = uiState.isAutoSyncEnabled,
        )
        SettingsSwitchRow(
          label = stringResource(R.string.settings_auto_sync_battery),
          description = stringResource(R.string.settings_auto_sync_battery_description),
          checked = autoSyncSkipLowBattery,
          onCheckedChange = onAutoSyncSkipLowBatteryChange,
          testTag = SettingsTestTags.AutoSyncBatterySwitch,
          enabled = uiState.isAutoSyncEnabled,
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
          description =
            stringResource(R.string.settings_initial_sync_description) +
              offlineNote(serverReachable),
          buttonText = stringResource(R.string.initial_sync),
          icon = Icons.Default.Sync,
          onClick = onInitialSyncClicked,
          enabled = !uiState.isInitialSyncInProgress && serverReachable,
          inProgress = uiState.isInitialSyncInProgress,
          testTag = SettingsTestTags.InitialSyncButton,
        )
      }

      SettingsSection(
        title = stringResource(R.string.settings_section_live),
        tutorialKey = TutorialTargets.SETTINGS_LIVE,
      ) {
        SettingsSwitchRow(
          label = stringResource(R.string.settings_live_mode),
          description = stringResource(R.string.settings_live_mode_description),
          checked = uiState.isLiveModeEnabled,
          onCheckedChange = onLiveModeToggle,
          testTag = SettingsTestTags.LiveModeSwitch,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSwitchRow(
          label = stringResource(R.string.settings_live_keep_patients),
          description = stringResource(R.string.settings_live_keep_patients_description),
          checked = uiState.keepLivePatients,
          onCheckedChange = onKeepLivePatientsToggle,
          testTag = SettingsTestTags.KeepLivePatientsSwitch,
          enabled = uiState.isLiveModeEnabled,
        )
      }

      SettingsSection(title = stringResource(R.string.settings_section_visits)) {
        SettingsSwitchRow(
          label = stringResource(R.string.settings_automatic_visits),
          description = stringResource(R.string.settings_automatic_visits_description),
          checked = uiState.automaticVisits,
          onCheckedChange = onAutomaticVisitsToggle,
          testTag = SettingsTestTags.AutomaticVisitsSwitch,
        )
      }

      // Only a signed-in user has data of their own to share (a guest has none to hide).
      uiState.signedInUser?.let { user ->
        SettingsSection(
          title = stringResource(R.string.settings_section_shared_device),
          tutorialKey = TutorialTargets.SETTINGS_SHARED,
        ) {
          SettingsSwitchRow(
            label = stringResource(R.string.settings_share_my_data),
            description = stringResource(R.string.settings_share_my_data_description, user),
            checked = uiState.shareMyData,
            onCheckedChange = onShareMyDataToggle,
            testTag = SettingsTestTags.ShareMyDataSwitch,
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          SettingsSwitchRow(
            label = stringResource(R.string.settings_remember_location),
            description = stringResource(R.string.settings_remember_location_description),
            checked = uiState.rememberLocation,
            onCheckedChange = onRememberLocationToggle,
            testTag = SettingsTestTags.RememberLocationSwitch,
          )
        }
      }

      if (onResetOfflineLoginClicked != null) {
        SettingsSection(
          title = stringResource(R.string.settings_section_security),
          tutorialKey = TutorialTargets.SETTINGS_SECURITY,
        ) {
          SettingsAction(
            description =
              stringResource(
                when {
                  offlineLoginEnabled == true && offlineLoginUnavailable ->
                    R.string.offline_login_unavailable_status
                  offlineLoginEnabled == true -> R.string.offline_login_enabled_status
                  offlineLoginEnabled == false -> R.string.offline_login_disabled_status
                  else -> R.string.offline_login_not_set_status
                },
              ),
            buttonText = stringResource(R.string.reset_offline_login),
            icon = Icons.Default.Refresh,
            onClick = onResetOfflineLoginClicked,
            enabled = offlineLoginEnabled != null,
            inProgress = false,
            testTag = SettingsTestTags.ResetOfflineLoginButton,
          )
          if (offlineLoginEnabled == true && appLock != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AppLockOptions(
              state = appLock,
              onDelaySelected = onLockDelaySelected,
              onLockOnScreenOffChange = onLockOnScreenOffChange,
              onHideInRecentsChange = onHideInRecentsChange,
            )
          }
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

      val showSections =
        favoriteFormOptions != null && onFormSectionsSaved != null && onFormSectionsReset != null
      val showFetch = onFetchQuestionnairesClicked != null && !embeddedFormsOnly
      val showFavorites = favoriteFormOptions != null && onFavoriteFormsSaved != null
      if (onEmbeddedFormsOnlyChange != null || showSections || showFetch || showFavorites) {
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
              description =
                stringResource(
                  if (fetchQuestionnairesAvailable) {
                    R.string.fetch_questionnaires_description
                  } else {
                    R.string.fetch_questionnaires_sign_in
                  },
                ) +
                  offlineNote(
                    serverReachable && fetchQuestionnairesAvailable ||
                      !fetchQuestionnairesAvailable,
                  ),
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
              enabled =
                fetchQuestionnairesAvailable && serverReachable && !isFetchingQuestionnaires,
              inProgress = isFetchingQuestionnaires,
              testTag = SettingsTestTags.FetchQuestionnairesButton,
            )
          }
          if (showFavorites && favoriteFormOptions != null && onFavoriteFormsSaved != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
              text = stringResource(R.string.favorite_forms),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            FavoriteFormsSetting(
              options = favoriteFormOptions,
              selectedIds = favoriteFormIds,
              onSaved = onFavoriteFormsSaved,
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

      if (onClearDataOnLogoutChange != null) {
        ClearDataOnLogoutSetting(
          checked = clearDataOnLogout,
          unsyncedCount = unsyncedCount,
          onRequested = { onLogoutAndDeleteRequested?.invoke() },
          onConfirmed = onClearDataOnLogoutChange,
        )
      }

      if (onLogoutAndDeleteConfirmed != null) {
        DangerZone(
          unsyncedCount = unsyncedCount,
          onRequested = { onLogoutAndDeleteRequested?.invoke() },
          onConfirmed = onLogoutAndDeleteConfirmed,
        )
      }
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onBackClicked,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag(SettingsTestTags.BackButton),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.settings_back))
        }
      }
    }
  }
}

@Composable
private fun SettingsSection(
  title: String,
  tutorialKey: String? = null,
  content: @Composable () -> Unit,
) {
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
private fun SettingsSwitchRow(
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
private fun AppLockOptions(
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
private fun AutoSyncModeChoice(
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

/**
 * Whether the regular Logout also deletes the data of the device. Changing it asks first, and the
 * question says what the new setting means (and, when turning deletion on, what is waiting to be
 * uploaded and would be lost with it).
 */
@Composable
private fun ClearDataOnLogoutSetting(
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

/** A card for what can lose data: the error color around it and a warning in its title. */
@Composable
private fun DangerCard(title: String, content: @Composable () -> Unit) {
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

/**
 * The last thing on the page: leave and wipe the device. It looks different on purpose (the error
 * color, a warning icon) and asks once more, spelling out what is lost, and how many changes were
 * never uploaded.
 */
@Composable
private fun DangerZone(unsyncedCount: Int, onRequested: () -> Unit, onConfirmed: () -> Unit) {
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
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(SettingsTestTags.LogoutDeleteButton),
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
private fun ConnectionTest(onServerChanged: ((String?) -> Unit)?) {
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
        Modifier.fillMaxWidth().height(48.dp).testTag(SettingsTestTags.ConnectionTestButton),
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
          Modifier.fillMaxWidth().height(48.dp).testTag(SettingsTestTags.ChangeServerButton),
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

/** A short explanation, a button and (while it runs) a progress bar. */
/** A line saying why an action is off when the server cannot be reached. */
@Composable
private fun offlineNote(serverReachable: Boolean): String =
  if (serverReachable) "" else "\n" + stringResource(R.string.sync_unavailable_offline)

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
