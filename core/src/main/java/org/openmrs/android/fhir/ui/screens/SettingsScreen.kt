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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.CustomFormSection
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.data.sync.SyncMode
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.ColorVisionMode
import org.openmrs.android.fhir.extensions.ThemeMode
import org.openmrs.android.fhir.ui.components.LanguagePicker
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
  const val SearchField = "SettingsSearchField"
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
  var query by rememberSaveable { mutableStateOf("") }
  val search = SettingsSearch(query)
  Column(modifier = modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = topPadding + 16.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier =
          Modifier.fillMaxWidth()
            .testTag(SettingsTestTags.SearchField)
            .tutorialTarget(TutorialTargets.SETTINGS_SEARCH),
        placeholder = { Text(text = stringResource(R.string.settings_search_hint)) },
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
      )
      CompositionLocalProvider(LocalSettingsSearch provides search) {
        SettingsSection(
          title = stringResource(R.string.settings_section_general),
          searchText = stringResource(R.string.enable_notifications),
        ) {
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
            searchText = stringResource(R.string.help_settings_language_body),
          ) {
            LanguagePicker(selected = selectedLanguage, onSelected = onLanguageSelected)
          }
        }

        if (onThemeModeSelected != null) {
          SettingsSection(
            title = stringResource(R.string.settings_section_appearance),
            tutorialKey = TutorialTargets.SETTINGS_APPEARANCE,
            searchText = stringResource(R.string.help_settings_appearance_body),
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
          searchText =
            stringResource(R.string.help_settings_sync_body) +
              " " +
              stringResource(R.string.check_network_connectivity) +
              " " +
              stringResource(R.string.settings_auto_sync_unmetered) +
              " " +
              stringResource(R.string.settings_auto_sync_battery),
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
          searchText = stringResource(R.string.help_settings_live_body),
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

        SettingsSection(
          title = stringResource(R.string.settings_section_visits),
          searchText =
            stringResource(R.string.settings_automatic_visits) +
              " " +
              stringResource(R.string.settings_automatic_visits_description),
        ) {
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
            searchText = stringResource(R.string.help_settings_shared_body),
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
            searchText =
              stringResource(R.string.help_settings_security_body) +
                " " +
                stringResource(R.string.app_lock_delay) +
                " " +
                stringResource(R.string.app_lock_screen_off) +
                " " +
                stringResource(R.string.app_lock_hide_recents),
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
          SettingsSection(
            title = stringResource(R.string.settings_section_forms),
            searchText =
              stringResource(R.string.embedded_forms_only) +
                " " +
                stringResource(R.string.favorite_forms) +
                " " +
                stringResource(R.string.form_sections_title),
          ) {
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
                onSaved = onFormSectionsSaved,
                onReset = onFormSectionsReset,
              )
            }
          }
        }

        if (query.isNotBlank() && search.shown == 0) {
          Text(
            text = stringResource(R.string.settings_search_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            textAlign = TextAlign.Center,
          )
        }

        if (onClearDataOnLogoutChange != null && query.isBlank()) {
          ClearDataOnLogoutSetting(
            checked = clearDataOnLogout,
            unsyncedCount = unsyncedCount,
            onRequested = { onLogoutAndDeleteRequested?.invoke() },
            onConfirmed = onClearDataOnLogoutChange,
          )
        }

        if (onLogoutAndDeleteConfirmed != null && query.isBlank()) {
          DangerZone(
            unsyncedCount = unsyncedCount,
            onRequested = { onLogoutAndDeleteRequested?.invoke() },
            onConfirmed = onLogoutAndDeleteConfirmed,
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
          onClick = onBackClicked,
          modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SettingsTestTags.BackButton),
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
