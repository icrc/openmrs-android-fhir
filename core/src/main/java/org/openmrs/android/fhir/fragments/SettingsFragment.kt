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
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.screens.SettingsDefaults
import org.openmrs.android.fhir.ui.screens.SettingsScreen
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SettingsEvent
import org.openmrs.android.fhir.viewmodel.SettingsViewModel

class SettingsFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private val settingsViewModel by viewModels<SettingsViewModel> { viewModelFactory }

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
      val context = LocalContext.current
      val density = LocalDensity.current
      val actionBarHeightPx = remember { resolveActionBarHeightPx(context) }
      val actionBarHeightDp = with(density) { actionBarHeightPx.toDp() }
      val uiState = settingsViewModel.uiState.collectAsStateWithLifecycle().value
      MaterialTheme {
        SettingsScreen(
          uiState = uiState,
          tokenDelayOptions = SettingsDefaults.TokenDelayOptions,
          periodicSyncDelayOptions = SettingsDefaults.PeriodicSyncDelayOptions,
          onNetworkStatusToggle = settingsViewModel::onNetworkStatusToggle,
          onNotificationsToggle = settingsViewModel::onNotificationsToggle,
          onTokenDelaySelected = settingsViewModel::onTokenDelaySelected,
          onPeriodicSyncDelaySelected = settingsViewModel::onPeriodicSyncDelaySelected,
          onInitialSyncClicked = settingsViewModel::onInitialSyncClicked,
          onCancelClicked = settingsViewModel::onCancelClicked,
          onSaveClicked = settingsViewModel::onSaveClicked,
          topPadding = actionBarHeightDp,
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
        settingsViewModel.events.collect { event -> handleSettingsEvent(event) }
      }
    }
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
    (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
      title = getString(R.string.settings)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun navigateUp() {
    NavHostFragment.findNavController(this).navigateUp()
  }
}

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

private fun resolveActionBarHeightPx(context: android.content.Context): Int {
  val typedValue = TypedValue()
  val resolved =
    context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true) ||
      context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
  return if (resolved) {
    TypedValue.complexToDimensionPixelSize(
      typedValue.data,
      context.resources.displayMetrics,
    )
  } else {
    0
  }
}
