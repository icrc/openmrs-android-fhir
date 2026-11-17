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
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.PatientListPickerScreen
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientListPickerViewModel

class PatientListPickerFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<PatientListPickerViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private val session: Boolean
    get() = requireArguments().getBoolean(ARG_SESSION, false)

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
    viewModel.start(
      requireArguments().getString(PatientListDetailFragment.ARG_LIST_ID).orEmpty(),
      session,
    )
    return composeView {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      PatientListPickerScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onToggle = viewModel::toggle,
        onCreatePatient = {
          findNavController()
            .navigate(
              R.id.action_patientListPickerFragment_to_addPatientFragment,
              if (session) {
                bundleOf(AddPatientFragment.ARG_FOR_SESSION to true)
              } else {
                bundleOf(
                  AddPatientFragment.ARG_JOIN_LIST to
                    requireArguments().getString(PatientListDetailFragment.ARG_LIST_ID).orEmpty(),
                )
              },
            )
        },
        onIdentifyLater =
          if (session) {
            null
          } else {
            { note ->
              viewModel.addPatientToIdentify(note) {
                AppMessage.show(context, R.string.plist_picker_later_done, MessageKind.Success)
              }
            }
          },
        onSelectAll = viewModel::selectAll,
        onLoadOtherLists = viewModel::loadOtherLists,
        onPickList = { otherId ->
          viewModel.addFromList(otherId) { chosen, unreachable ->
            when {
              chosen > 0 ->
                AppMessage.show(
                  context,
                  resources.getQuantityString(
                    R.plurals.plist_picker_from_list_done,
                    chosen,
                    chosen,
                  ),
                  MessageKind.Success,
                )
              unreachable ->
                AppMessage.show(
                  context,
                  R.string.plist_picker_from_list_unreachable,
                  MessageKind.Error,
                )
              else ->
                AppMessage.show(context, R.string.plist_picker_from_list_empty, MessageKind.Info)
            }
          }
        },
        onAdd = {
          viewModel.addSelected { count, failed ->
            if (failed > 0) {
              AppMessage.show(context, R.string.plist_session_download_failed, MessageKind.Error)
            }
            if (session) {
              // The session this was opened from takes the patients; nothing is added to the list
              // yet.
              findNavController()
                .previousBackStackEntry
                ?.savedStateHandle
                ?.set(GroupSessionPatientsFragment.RESULT_PATIENT_IDS, viewModel.selectedIds())
              findNavController().navigateUp()
              return@addSelected
            }
            if (count > 0) {
              AppMessage.show(
                context,
                resources.getQuantityString(R.plurals.plist_picker_added, count, count) +
                  ". " +
                  getString(R.string.plist_picker_added_note),
                MessageKind.Success,
              )
            }
            findNavController().navigateUp()
          }
        },
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.limitToReadableWidth()
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = getString(R.string.plist_add_patients)
      setDisplayHomeAsUpEnabled(true)
    }
    mainActivityViewModel.setDrawerEnabled(false)
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
    const val ARG_SESSION = "session"
  }
}
