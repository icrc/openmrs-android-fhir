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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.extensions.limitToReadableWidth
import org.openmrs.android.fhir.ui.screens.GroupSessionPatientsScreen
import org.openmrs.android.fhir.viewmodel.GroupSessionPatientsViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

class GroupSessionPatientsFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<GroupSessionPatientsViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private val listId: String
    get() = requireArguments().getString(ARG_LIST_ID).orEmpty()

  private val questionnaireId: String
    get() = requireArguments().getString(ARG_FORM_ID).orEmpty()

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
    viewModel.start(listId)
    return composeView {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      GroupSessionPatientsScreen(
        state = state,
        onTick = viewModel::setTicked,
        onTickAll = viewModel::setAllTicked,
        onRemove = viewModel::remove,
        onAddPatients = ::chooseMorePatients,
        onIdentifyLater = viewModel::addPatientToIdentify,
        onStart = ::startSession,
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.limitToReadableWidth()
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = getString(R.string.create_group_encounter)
      setDisplayHomeAsUpEnabled(true)
    }
    mainActivityViewModel.setDrawerEnabled(false)
    val handle = findNavController().currentBackStackEntry?.savedStateHandle
    handle?.getLiveData<ArrayList<String>>(RESULT_PATIENT_IDS)?.observe(viewLifecycleOwner) { ids ->
      if (ids == null) return@observe
      handle.remove<ArrayList<String>>(RESULT_PATIENT_IDS)
      viewModel.addPatients(ids)
    }
  }

  private fun chooseMorePatients() {
    findNavController()
      .navigate(
        R.id.action_groupSessionPatientsFragment_to_patientListPickerFragment,
        bundleOf(
          PatientListDetailFragment.ARG_LIST_ID to listId,
          PatientListPickerFragment.ARG_SESSION to true,
        ),
      )
  }

  private fun startSession() {
    viewModel.begin { ids ->
      findNavController()
        .navigate(
          GroupSessionPatientsFragmentDirections
            .actionGroupSessionPatientsFragmentToGroupFormEntryFragment(
              patientIds = ids.toTypedArray(),
              questionnaireId = questionnaireId,
              patientListId = listId,
            ),
        )
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean =
    when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this).navigateUp()
        true
      }
      else -> false
    }

  companion object {
    const val ARG_LIST_ID = "patient_list_id"
    const val ARG_FORM_ID = "questionnaire_id"

    const val RESULT_PATIENT_IDS = "session_patient_ids"
  }
}
