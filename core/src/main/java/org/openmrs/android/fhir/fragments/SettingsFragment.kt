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
package org.openmrs.android.fhir.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.QuestionnaireUpdater
import org.openmrs.android.fhir.data.remote.isServerReachable
import org.openmrs.android.fhir.data.transfer.DataTransfer
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.extensions.ClearDataOnLogoutStore
import org.openmrs.android.fhir.extensions.ColorVisionMode
import org.openmrs.android.fhir.extensions.ColorVisionStore
import org.openmrs.android.fhir.extensions.EmbeddedFormsStore
import org.openmrs.android.fhir.extensions.ThemeMode
import org.openmrs.android.fhir.extensions.ThemeModeStore
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.SettingsDefaults
import org.openmrs.android.fhir.ui.screens.SettingsScreen
import org.openmrs.android.fhir.ui.screens.ShareSettingsDialog
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.FavoriteFormsViewModel
import org.openmrs.android.fhir.viewmodel.IdentifierSelectionViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.QuestionnaireFetchViewModel
import org.openmrs.android.fhir.viewmodel.SettingsEvent
import org.openmrs.android.fhir.viewmodel.SettingsViewModel

class SettingsFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var dataTransfer: DataTransfer
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private val settingsViewModel by viewModels<SettingsViewModel> { viewModelFactory }
  private val questionnaireFetchViewModel by
    viewModels<QuestionnaireFetchViewModel> { viewModelFactory }
  private val favoriteFormsViewModel by viewModels<FavoriteFormsViewModel> { viewModelFactory }
  private val identifierSelectionViewModel by
    viewModels<IdentifierSelectionViewModel> { viewModelFactory }
  private val themeSelection = mutableStateOf(ThemeMode.SYSTEM)
  private val colorVisionSelection = mutableStateOf(ColorVisionMode.STANDARD)
  private val unsyncedCount = mutableStateOf(0)
  private val showShare = mutableStateOf(false)
  private val clearDataOnLogout = mutableStateOf(true)
  private val embeddedFormsSelection = mutableStateOf(true)

  /** true: enabled, false: declined, null: not answered yet. */
  private val offlineLoginEnabled = mutableStateOf<Boolean?>(null)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val composeView = ComposeView(requireContext())
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
    )
    composeView.setContent {
      val context = androidx.compose.ui.platform.LocalContext.current
      val appBarHeight = rememberAppBarHeight()
      LaunchedEffect(Unit) {
        themeSelection.value = ThemeModeStore.get(context)
        colorVisionSelection.value = ColorVisionStore.get(context)
        embeddedFormsSelection.value = EmbeddedFormsStore.get(context)
        clearDataOnLogout.value = ClearDataOnLogoutStore.get(context)
        offlineLoginEnabled.value =
          when {
            BiometricUtils.getOfflineAuthMethod(context) != null -> true
            BiometricUtils.hasOfflineLoginDecision(context) -> false
            else -> null
          }
      }
      val uiState = settingsViewModel.uiState.collectAsStateWithLifecycle().value
      val activityState by mainActivityViewModel.uiState.collectAsStateWithLifecycle()
      val isFetchingQuestionnaires by
        questionnaireFetchViewModel.isFetching.collectAsStateWithLifecycle()
      val identifierState by identifierSelectionViewModel.uiState.collectAsStateWithLifecycle()
      val favoriteForms by favoriteFormsViewModel.forms.collectAsStateWithLifecycle()
      val favoriteIds by favoriteFormsViewModel.favoriteIds.collectAsStateWithLifecycle()
      val customSections by favoriteFormsViewModel.customSections.collectAsStateWithLifecycle()
      val defaultSections by favoriteFormsViewModel.defaultSections.collectAsStateWithLifecycle()
      OpenMrsTheme {
        if (showShare.value) {
          ShareSettingsDialog(
            onDismiss = { showShare.value = false },
            onApplyServer = { url -> (activity as? MainActivity)?.changeServer(url) },
            // Every value on this page is read again.
            onApplied = { activity?.recreate() },
          )
        }
        SettingsScreen(
          uiState = uiState,
          favoriteFormOptions = favoriteForms,
          favoriteFormIds = favoriteIds,
          onFavoriteFormsSaved = favoriteFormsViewModel::setFavorites,
          // Identifier types come from the server: not for someone working as a guest.
          identifierTypes =
            if (GuestSession.isActive(context)) null else identifierState.identifierTypes,
          selectedIdentifierIds = identifierState.selectedIdentifierIds,
          isLoadingIdentifierTypes = identifierState.isLoading,
          onIdentifierTypesRequested = identifierSelectionViewModel::loadIdentifiers,
          onIdentifierTypesSaved = identifierSelectionViewModel::saveSelection,
          customFormSections = customSections,
          defaultFormSections = defaultSections,
          onFormSectionsSaved = favoriteFormsViewModel::setCustomSections,
          onFormSectionsReset = favoriteFormsViewModel::resetSections,
          tokenDelayOptions = SettingsDefaults.TokenDelayOptions,
          periodicSyncDelayOptions = SettingsDefaults.PeriodicSyncDelayOptions,
          onNetworkStatusToggle = settingsViewModel::onNetworkStatusToggle,
          onNotificationsToggle = settingsViewModel::onNotificationsToggle,
          onTokenDelaySelected = settingsViewModel::onTokenDelaySelected,
          onPeriodicSyncDelaySelected = settingsViewModel::onPeriodicSyncDelaySelected,
          onAutoSyncToggle = settingsViewModel::onAutoSyncToggle,
          onAutoSyncModeSelected = settingsViewModel::onAutoSyncModeSelected,
          onLiveModeToggle = settingsViewModel::onLiveModeToggle,
          onKeepLivePatientsToggle = settingsViewModel::onKeepLivePatientsToggle,
          onAutomaticVisitsToggle = settingsViewModel::onAutomaticVisitsToggle,
          onShareMyDataToggle = settingsViewModel::onShareMyDataToggle,
          onRememberLocationToggle = settingsViewModel::onRememberLocationToggle,
          onServerChanged = { url -> (activity as? MainActivity)?.changeServer(url) },
          selectedLanguage = AppLanguage.current(context),
          onLanguageSelected = { AppLanguage.apply(context, it) },
          onInitialSyncClicked = {
            if (GuestSession.isActive(context)) {
              (activity as? MainActivity)?.onSyncPress()
            } else {
              settingsViewModel.onInitialSyncClicked()
            }
          },
          onBackClicked = ::navigateUp,
          topPadding = appBarHeight,
          isFetchingQuestionnaires = isFetchingQuestionnaires,
          fetchQuestionnairesAvailable = !GuestSession.isActive(context),
          serverReachable = activityState.connectivityState.isServerReachable(),
          onFetchQuestionnairesClicked = {
            // Asking the server for forms means the server's forms are wanted.
            EmbeddedFormsStore.set(context, false)
            questionnaireFetchViewModel.fetchQuestionnaires()
          },
          themeMode = themeSelection.value,
          onThemeModeSelected = { mode ->
            // Seen at once: the screens are recreated in the new theme, not only after Save.
            if (ThemeModeStore.get(context) != mode) {
              AuditLog.record(
                AuditAction.SETTINGS_CHANGED,
                AuditOutcome.SUCCESS,
                "changed" to listOf("theme=${mode.value}"),
              )
              themeSelection.value = mode
              ThemeModeStore.set(context, mode)
            }
          },
          colorVision = colorVisionSelection.value,
          onColorVisionSelected = { mode ->
            // Kept and applied at once, like every setting.
            if (ColorVisionStore.get(context) != mode) {
              colorVisionSelection.value = mode
              ColorVisionStore.set(context, mode)
              AuditLog.record(
                AuditAction.SETTINGS_CHANGED,
                AuditOutcome.SUCCESS,
                "changed" to listOf("color_vision=${mode.value}"),
              )
              // The palette is read when a screen is built.
              activity?.recreate()
            }
          },
          embeddedFormsOnly = embeddedFormsSelection.value,
          onEmbeddedFormsOnlyChange = { only ->
            embeddedFormsSelection.value = only
            EmbeddedFormsStore.set(context, only)
            AuditLog.record(
              AuditAction.SETTINGS_CHANGED,
              AuditOutcome.SUCCESS,
              "changed" to listOf("embedded_forms_only=$only"),
            )
          },
          offlineLoginEnabled = offlineLoginEnabled.value,
          onResetOfflineLoginClicked = ::confirmResetOfflineLogin,
          unsyncedCount = unsyncedCount.value,
          clearDataOnLogout = clearDataOnLogout.value,
          onClearDataOnLogoutChange = { clear ->
            // Asked and confirmed already: it is saved at once, like the wipe button's.
            ClearDataOnLogoutStore.set(context, clear)
            clearDataOnLogout.value = clear
            AuditLog.record(
              AuditAction.SETTINGS_CHANGED,
              AuditOutcome.SUCCESS,
              "changed" to listOf("clear_data_on_logout=$clear"),
            )
          },
          onLogoutAndDeleteRequested = ::countUnsyncedChanges,
          onLogoutAndDeleteConfirmed = { (activity as? MainActivity)?.logoutAndDeleteAllData() },
        )
      }
    }
    return composeView
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setHasOptionsMenu(true)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    setUpActionBar()
    favoriteFormsViewModel.load()
    mainActivityViewModel.setDrawerEnabled(false)
    observeSettingsEvents()
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.settings_options_menu, menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        navigateUp()
        true
      }
      R.id.menu_settings_reset -> {
        confirmResetToDefaults()
        true
      }
      R.id.menu_settings_share -> {
        showShare.value = true
        true
      }
      else -> false
    }
  }

  private fun confirmResetToDefaults() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.settings_reset_title)
      .setMessage(R.string.settings_reset_message)
      .setPositiveButton(R.string.settings_reset_confirm) { _, _ -> resetToDefaults() }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  /**
   * The appearance, language, sync, notification and forms settings go back to what the app starts
   * with, and are saved at once. Data, the session, the location, the identifier types and the
   * offline login are not settings and stay as they are.
   */
  private fun resetToDefaults() {
    val context = requireContext()
    val recreate = ColorVisionStore.get(context) != ColorVisionMode.STANDARD
    settingsViewModel.onResetToDefaults()
    favoriteFormsViewModel.setFavorites(emptyList())
    favoriteFormsViewModel.resetSections()
    EmbeddedFormsStore.set(context, true)
    embeddedFormsSelection.value = true
    ClearDataOnLogoutStore.set(context, true)
    clearDataOnLogout.value = true
    ColorVisionStore.set(context, ColorVisionMode.STANDARD)
    colorVisionSelection.value = ColorVisionMode.STANDARD
    themeSelection.value = ThemeMode.SYSTEM
    AuditLog.record(
      AuditAction.SETTINGS_CHANGED,
      AuditOutcome.SUCCESS,
      "changed" to listOf("reset"),
    )
    ThemeModeStore.set(context, ThemeMode.SYSTEM)
    AppLanguage.resetToDevice()
    // The palette is read when a screen is built.
    if (recreate) activity?.recreate()
  }

  private fun observeSettingsEvents() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { settingsViewModel.events.collect { event -> handleSettingsEvent(event) } }
        launch {
          questionnaireFetchViewModel.outcomes.collect { outcome ->
            showQuestionnaireOutcome(outcome)
          }
        }
      }
    }
  }

  /** What would be lost: the changes this device never uploaded. */
  private fun countUnsyncedChanges() {
    viewLifecycleOwner.lifecycleScope.launch {
      unsyncedCount.value =
        try {
          dataTransfer.pendingCount()
        } catch (e: Exception) {
          0
        }
    }
  }

  private fun confirmResetOfflineLogin() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.reset_offline_login_confirm_title)
      .setMessage(R.string.reset_offline_login_confirm_message)
      .setPositiveButton(R.string.reset_offline_login) { _, _ ->
        BiometricUtils.resetOfflineLogin(requireContext())
        offlineLoginEnabled.value = null
        AppMessage.show(
          requireContext(),
          R.string.offline_login_reset_done,
          MessageKind.Success,
          long = true,
        )
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun showQuestionnaireOutcome(outcome: QuestionnaireUpdater.Outcome) {
    val message =
      when (outcome) {
        is QuestionnaireUpdater.Outcome.Added ->
          if (outcome.updated == 0) {
            getString(R.string.questionnaires_added, outcome.added)
          } else {
            getString(R.string.questionnaires_changes, outcome.added, outcome.updated)
          }
        is QuestionnaireUpdater.Outcome.UpToDate -> getString(R.string.questionnaires_up_to_date)
        is QuestionnaireUpdater.Outcome.Failed -> getString(R.string.questionnaires_fetch_failed)
      }
    AppMessage.show(requireContext(), message, MessageKind.Info, long = true)
  }

  private fun handleSettingsEvent(event: SettingsEvent) {
    val action = resolveSettingsEvent(event)
    action.messageResId?.let { messageResId ->
      AppMessage.show(requireContext(), getString(messageResId), MessageKind.Info)
    }
    if (action.navigateUp) {
      navigateUp()
    }
  }

  private fun setUpActionBar() {
    // After the language is switched the screen is recreated and the app bar is composed a moment
    // after the fragment: wait for it instead of leaving the app name as the title.
    viewLifecycleOwner.lifecycleScope.launch {
      repeat(ACTION_BAR_WAIT_ATTEMPTS) {
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        if (actionBar != null) {
          actionBar.title = getString(R.string.settings)
          actionBar.setDisplayHomeAsUpEnabled(true)
          return@launch
        }
        delay(ACTION_BAR_WAIT_MILLIS)
      }
    }
  }

  private fun navigateUp() {
    NavHostFragment.findNavController(this).navigateUp()
  }
}

private const val ACTION_BAR_WAIT_ATTEMPTS = 60
private const val ACTION_BAR_WAIT_MILLIS = 50L

internal data class SettingsEventAction(
  val messageResId: Int?,
  val navigateUp: Boolean,
)

internal fun resolveSettingsEvent(event: SettingsEvent): SettingsEventAction {
  return when (event) {
    SettingsEvent.SettingsReset ->
      SettingsEventAction(
        messageResId = R.string.settings_reset_done,
        navigateUp = false,
      )
    SettingsEvent.InitialSyncStarted ->
      SettingsEventAction(
        messageResId = R.string.initial_sync_started,
        navigateUp = false,
      )
    SettingsEvent.InitialSyncCompleted ->
      SettingsEventAction(
        messageResId = R.string.initial_sync_completed,
        navigateUp = false,
      )
    SettingsEvent.InitialSyncFailed ->
      SettingsEventAction(
        messageResId = R.string.initial_sync_failed,
        navigateUp = false,
      )
  }
}
