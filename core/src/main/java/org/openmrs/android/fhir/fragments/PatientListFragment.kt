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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.patient.PatientListScreen
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class PatientListFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val patientListViewModel by viewModels<PatientListViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private lateinit var composeView: ComposeView

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    composeView = ComposeView(requireContext())
    return composeView
  }

  private var resumedBefore = false

  override fun onResume() {
    super.onResume()
    // Back from registering a patient (or opening one): the list shows what changed.
    if (resumedBefore) patientListViewModel.reload()
    resumedBefore = true
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = resources.getString(R.string.title_patient_list)
      setDisplayHomeAsUpEnabled(true)
    }
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
    )
    composeView.setContent {
      OpenMrsTheme {
        val uiState by patientListViewModel.uiState.collectAsState()
        val serverMessage = uiState.serverSearch.messageRes
        LaunchedEffect(serverMessage) {
          if (serverMessage != null) {
            AppMessage.show(context, serverMessage, MessageKind.Error, long = true)
            patientListViewModel.clearServerMessage()
          }
        }
        PatientListScreen(
          uiState = uiState,
          onQueryChange = {
            patientListViewModel.searchPatientsByName(it.trim())
            patientListViewModel.searchServer(it)
          },
          onRefresh = { patientListViewModel.refreshPatients() },
          onPatientClick = { patient -> onPatientItemClicked(patient) },
          onServerPatientClick = { patient ->
            patientListViewModel.openServerPatient(patient) { id -> openPatient(id) }
          },
          onFabClick = { onAddPatientClick() },
          onIdentifyLater = { note -> onIdentifyLater(note) },
        )
      }
    }

    requireActivity()
      .onBackPressedDispatcher
      .addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            if (patientListViewModel.uiState.value.query.isNotEmpty()) {
              patientListViewModel.clearSearch()
            } else {
              isEnabled = false
              activity?.onBackPressed()
            }
          }
        },
      )

    setHasOptionsMenu(true)
    mainActivityViewModel.setDrawerEnabled(false)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        if (patientListViewModel.uiState.value.query.isNotEmpty()) {
          patientListViewModel.clearSearch()
        } else {
          //          isEnabled = false
          NavHostFragment.findNavController(this).navigateUp()
        }
        true
      }
      else -> false
    }
  }

  private fun onPatientItemClicked(patientItem: PatientListViewModel.PatientItem) {
    openPatient(patientItem.resourceId)
  }

  private fun openPatient(patientId: String) {
    findNavController().navigate(PatientListFragmentDirections.navigateToProductDetail(patientId))
  }

  /** Records for a patient who is not on the device: a placeholder, opened to record them. */
  private fun onIdentifyLater(note: String) {
    lifecycleScope.launch {
      val id =
        FhirApplication.appComponent(requireContext().applicationContext)
          .unidentifiedPatients()
          .create(note)
      openPatient(id)
    }
  }

  private fun onAddPatientClick() {
    // Without a location (a guest, or before one is chosen) a patient can still be registered:
    // it gets the location, and its identifiers, once there is one.
    findNavController()
      .navigate(PatientListFragmentDirections.actionPatientListToAddPatientFragment())
  }
}
