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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.QuestionnaireUpdater
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.extensions.ThemeMode
import org.openmrs.android.fhir.extensions.ThemeModeStore
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.screens.SettingsDefaults
import org.openmrs.android.fhir.ui.screens.SettingsScreen
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.FavoriteFormsViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.QuestionnaireFetchViewModel
import org.openmrs.android.fhir.viewmodel.SettingsEvent
import org.openmrs.android.fhir.viewmodel.SettingsViewModel

class SettingsFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private val settingsViewModel by viewModels<SettingsViewModel> { viewModelFactory }
  private val questionnaireFetchViewModel by
    viewModels<QuestionnaireFetchViewModel> { viewModelFactory }
  private val favoriteFormsViewModel by viewModels<FavoriteFormsViewModel> { viewModelFactory }
  private val themeSelection = mutableStateOf(ThemeMode.SYSTEM)

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
        offlineLoginEnabled.value =
          when {
            BiometricUtils.getOfflineAuthMethod(context) != null -> true
            BiometricUtils.hasOfflineLoginDecision(context) -> false
            else -> null
          }
      }
      val uiState = settingsViewModel.uiState.collectAsStateWithLifecycle().value
      val isFetchingQuestionnaires by
        questionnaireFetchViewModel.isFetching.collectAsStateWithLifecycle()
      val favoriteForms by favoriteFormsViewModel.forms.collectAsStateWithLifecycle()
      val favoriteIds by favoriteFormsViewModel.favoriteIds.collectAsStateWithLifecycle()
      val customSections by favoriteFormsViewModel.customSections.collectAsStateWithLifecycle()
      val defaultSections by favoriteFormsViewModel.defaultSections.collectAsStateWithLifecycle()
      OpenMrsTheme {
        SettingsScreen(
          uiState = uiState,
          favoriteFormOptions = favoriteForms,
          favoriteFormIds = favoriteIds,
          onFavoriteFormsSaved = favoriteFormsViewModel::setFavorites,
          // Identifier types come from the server: not for someone working as a guest.
          onSelectIdentifierTypesClicked =
            if (GuestSession.isActive(context)) {
              null
            } else {
              {
                NavHostFragment.findNavController(this@SettingsFragment)
                  .navigate(R.id.identifierFragment)
              }
            },
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
          selectedLanguage = AppLanguage.current(context),
          onLanguageSelected = { AppLanguage.apply(context, it) },
          onInitialSyncClicked = {
            if (GuestSession.isActive(context)) {
              (activity as? MainActivity)?.onSyncPress()
            } else {
              settingsViewModel.onInitialSyncClicked()
            }
          },
          onCancelClicked = settingsViewModel::onCancelClicked,
          onSaveClicked = {
            settingsViewModel.onSaveClicked()
            ThemeModeStore.set(context, themeSelection.value)
          },
          topPadding = appBarHeight,
          isFetchingQuestionnaires = isFetchingQuestionnaires,
          onFetchQuestionnairesClicked =
            if (GuestSession.isActive(context)) {
              null
            } else {
              questionnaireFetchViewModel::fetchQuestionnaires
            },
          themeMode = themeSelection.value,
          onThemeModeSelected = { themeSelection.value = it },
          offlineLoginEnabled = offlineLoginEnabled.value,
          onResetOfflineLoginClicked = ::confirmResetOfflineLogin,
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

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        navigateUp()
        true
      }
      else -> false
    }
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

  private fun confirmResetOfflineLogin() {
    AlertDialog.Builder(requireContext())
      .setTitle(R.string.reset_offline_login_confirm_title)
      .setMessage(R.string.reset_offline_login_confirm_message)
      .setPositiveButton(R.string.reset_offline_login) { _, _ ->
        BiometricUtils.resetOfflineLogin(requireContext())
        offlineLoginEnabled.value = null
        Toast.makeText(requireContext(), R.string.offline_login_reset_done, Toast.LENGTH_LONG)
          .show()
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun showQuestionnaireOutcome(outcome: QuestionnaireUpdater.Outcome) {
    val message =
      when (outcome) {
        is QuestionnaireUpdater.Outcome.Added ->
          getString(R.string.questionnaires_added, outcome.added)
        is QuestionnaireUpdater.Outcome.UpToDate -> getString(R.string.questionnaires_up_to_date)
        is QuestionnaireUpdater.Outcome.Failed -> getString(R.string.questionnaires_fetch_failed)
      }
    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
  }

  private fun handleSettingsEvent(event: SettingsEvent) {
    val action = resolveSettingsEvent(event)
    action.messageResId?.let { messageResId ->
      Toast.makeText(requireContext(), getString(messageResId), Toast.LENGTH_SHORT).show()
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
    SettingsEvent.SettingsSaved ->
      SettingsEventAction(
        messageResId = R.string.settings_saved,
        navigateUp = true,
      )
    SettingsEvent.SettingsDiscarded ->
      SettingsEventAction(
        messageResId = R.string.settings_discarded,
        navigateUp = true,
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
