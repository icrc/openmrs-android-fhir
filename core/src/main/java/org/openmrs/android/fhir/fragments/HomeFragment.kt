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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.screens.HomeScreen
import org.openmrs.android.fhir.viewmodel.HomeEvent
import org.openmrs.android.fhir.viewmodel.HomeViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

class HomeFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private val homeViewModel by viewModels<HomeViewModel> { viewModelFactory }

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
      MaterialTheme {
        HomeScreen(
          onNewPatientClicked = homeViewModel::onNewPatientClicked,
          onPatientListClicked = homeViewModel::onPatientListClicked,
          onCustomPatientListClicked = homeViewModel::onCustomPatientListClicked,
          onGroupEncounterClicked = homeViewModel::onGroupEncounterClicked,
          onSyncInfoClicked = homeViewModel::onSyncInfoClicked,
          onUnsyncedResourcesClicked = homeViewModel::onUnsyncedResourcesClicked,
        )
      }
    }
    return composeView
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = resources.getString(R.string.app_name)
      setDisplayHomeAsUpEnabled(true)
    }
    setHasOptionsMenu(true)
    mainActivityViewModel.setDrawerEnabled(true)
    observeHomeEvents()
  }

  @Deprecated("Deprecated in Java")
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        mainActivityViewModel.requestOpenDrawer()
        true
      }
      else -> false
    }
  }

  private fun observeHomeEvents() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        homeViewModel.events.collect { event -> handleHomeEvent(event) }
      }
    }
  }

  private fun handleHomeEvent(event: HomeEvent) {
    when (event) {
      HomeEvent.NavigateToAddPatient ->
        findNavController()
          .navigate(HomeFragmentDirections.actionHomeFragmentToAddPatientFragment())
      HomeEvent.NavigateToPatientList ->
        findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToPatientList())
      HomeEvent.NavigateToCustomPatientList ->
        findNavController()
          .navigate(HomeFragmentDirections.actionHomeFragmentToSelectPatientListFragment(false))
      HomeEvent.NavigateToGroupEncounter ->
        findNavController()
          .navigate(
            HomeFragmentDirections.actionHomeFragmentToCreateEncounterFragment(
              patientId = "",
              isGroupEncounter = true,
            ),
          )
      HomeEvent.NavigateToSyncInfo ->
        findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToSyncInfoFragment())
      HomeEvent.NavigateToUnsyncedResources ->
        findNavController()
          .navigate(HomeFragmentDirections.actionHomeFragmentToUnsyncedResourcesFragment())
      is HomeEvent.ShowMessage ->
        Toast.makeText(requireContext(), getString(event.messageResId), Toast.LENGTH_LONG).show()
    }
  }
}
