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
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.patient.PatientSelectionDialogContent
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import org.openmrs.android.fhir.viewmodel.filterSelectedIdsForPatients

class PatientSelectionDialogFragment : DialogFragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel: PatientListViewModel by
    viewModels<PatientListViewModel> { viewModelFactory }
  private val args: PatientSelectionDialogFragmentArgs by navArgs()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: android.view.ViewGroup?,
    savedInstanceState: Bundle?,
  ): ComposeView {
    dialog?.setCanceledOnTouchOutside(false)
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val patients by viewModel.liveSearchedPatients.observeAsState(emptyList())
        var selectedPatientIds by
          rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf(setOf<String>()) }

        LaunchedEffect(patients) {
          selectedPatientIds = filterSelectedIdsForPatients(patients, selectedPatientIds)
        }

        PatientSelectionDialogContent(
          patients = patients,
          selectedIds = selectedPatientIds,
          onDismissRequest = { dismiss() },
          onPatientToggle = { patient ->
            selectedPatientIds = selectedPatientIds.toggleSelection(patient.resourceId)
          },
          onSelectAllToggle = { shouldSelectAll ->
            selectedPatientIds =
              if (shouldSelectAll) patients.map { it.resourceId }.toSet() else emptySet()
          },
          onStartEncounter = { handleStartEncounter(selectedPatientIds) },
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()
    dialog
      ?.window
      ?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
  }

  private fun handleStartEncounter(selectedPatientIds: Set<String>) {
    if (selectedPatientIds.isEmpty()) {
      Toast.makeText(
          requireContext(),
          R.string.please_select_at_least_one_patient,
          Toast.LENGTH_SHORT,
        )
        .show()
      return
    }

    val action =
      PatientSelectionDialogFragmentDirections
        .actionPatientSelectionDialogFragmentToGroupFormEntryFragment(
          questionnaireId = args.questionnaireId,
          patientIds = selectedPatientIds.toTypedArray(),
        )
    findNavController().navigate(action)
  }
}

internal fun Set<String>.toggleSelection(resourceId: String): Set<String> {
  val updatedSelection = toMutableSet()
  if (!updatedSelection.add(resourceId)) {
    updatedSelection.remove(resourceId)
  }
  return updatedSelection
}

private val stringSetSaver =
  Saver<Set<String>, List<String>>(save = { it.toList() }, restore = { it.toSet() })
