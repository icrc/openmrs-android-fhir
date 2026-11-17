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
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.FormUpdater
import org.openmrs.android.fhir.data.remote.isServerReachable
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.FormUpdatesScreen
import org.openmrs.android.fhir.viewmodel.FormUpdatesViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

/** Which forms the server has changed, and the buttons that take them (Settings > Forms). */
class FormUpdatesFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<FormUpdatesViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: android.view.ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    return composeView {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      val activityState by mainActivityViewModel.uiState.collectAsStateWithLifecycle()
      FormUpdatesScreen(
        state = state,
        canCheck = !GuestSession.isActive(requireContext()),
        serverReachable = activityState.connectivityState.isServerReachable(),
        onCheck = viewModel::check,
        onTakeAll = viewModel::takeAll,
        onTake = viewModel::take,
        onUseApp = viewModel::useAppVersion,
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.title_form_updates)
      setDisplayHomeAsUpEnabled(true)
    }
    mainActivityViewModel.setDrawerEnabled(false)
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.outcomes.collect { outcome ->
        AppMessage.show(requireContext(), messageOf(outcome), kindOf(outcome))
      }
    }
  }

  private fun messageOf(outcome: FormUpdater.Outcome): String =
    when (outcome) {
      is FormUpdater.Outcome.Checked ->
        if (outcome.updates + outcome.new == 0) {
          getString(R.string.forms_updates_all_current)
        } else {
          getString(R.string.forms_msg_found, outcome.updates, outcome.new)
        }
      is FormUpdater.Outcome.Taken ->
        resources.getQuantityString(R.plurals.forms_msg_taken, outcome.count, outcome.count)
      is FormUpdater.Outcome.BackToApp -> getString(R.string.forms_msg_back_to_app, outcome.title)
      is FormUpdater.Outcome.Failed -> getString(R.string.questionnaires_fetch_failed)
    }

  private fun kindOf(outcome: FormUpdater.Outcome): MessageKind =
    if (outcome is FormUpdater.Outcome.Failed) MessageKind.Error else MessageKind.Success

  override fun onOptionsItemSelected(item: MenuItem): Boolean =
    when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this).navigateUp()
        true
      }
      else -> false
    }
}
