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
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.PatientSelectionAdapter
import org.openmrs.android.fhir.databinding.DialogPatientSelectionBinding
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class PatientSelectionDialogFragment : DialogFragment() {

  private var _binding: DialogPatientSelectionBinding? = null
  private val binding
    get() = _binding!!

  private lateinit var patientSelectionAdapter: PatientSelectionAdapter
  private var isProgrammaticallyChangingSelectAll = false

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel: PatientListViewModel by
    viewModels<PatientListViewModel> { viewModelFactory }
  private val args: PatientSelectionDialogFragmentArgs by navArgs()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = DialogPatientSelectionBinding.inflate(inflater, container, false)
    dialog?.setCanceledOnTouchOutside(false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    (requireActivity().application as FhirApplication).appComponent.inject(this)
    setupRecyclerView()
    setupListeners()
    observeViewModel()
  }

  private fun setupRecyclerView() {
    patientSelectionAdapter = PatientSelectionAdapter { updateUiBasedOnSelection() }
    binding.patientsRecyclerView.apply {
      adapter = patientSelectionAdapter
      layoutManager = LinearLayoutManager(requireContext())
    }
  }

  private fun setupListeners() {
    binding.buttonCancel.setOnClickListener { dismiss() }

    binding.buttonStartEncounter.setOnClickListener {
      val selectedIds = patientSelectionAdapter.getSelectedPatientIds()
      if (selectedIds.isEmpty()) { // Should be prevented by button enabled state
        Toast.makeText(
            requireContext(),
            R.string.please_select_at_least_one_patient,
            Toast.LENGTH_SHORT,
          )
          .show()
        return@setOnClickListener
      }
      val action =
        PatientSelectionDialogFragmentDirections
          .actionPatientSelectionDialogFragmentToGroupFormEntryFragment(
            questionnaireId = args.questionnaireId,
            patientIds = selectedIds.toTypedArray(),
          )
      findNavController().navigate(action)
    }

    binding.checkboxSelectAll.setOnCheckedChangeListener { _, isChecked ->
      if (isProgrammaticallyChangingSelectAll) return@setOnCheckedChangeListener

      val currentPatients = viewModel.liveSearchedPatients.value ?: emptyList()
      if (isChecked) {
        patientSelectionAdapter.selectAll(currentPatients)
      } else {
        patientSelectionAdapter.deselectAll()
      }
      // The adapter's onSelectionChanged callback will call updateUiBasedOnSelection()
    }
  }

  private fun observeViewModel() {
    viewModel.liveSearchedPatients.observe(
      viewLifecycleOwner,
      Observer { patients ->
        patientSelectionAdapter.submitList(patients ?: emptyList())
        updateUiBasedOnSelection(patients ?: emptyList())
      },
    )
  }

  private fun updateUiBasedOnSelection(
    currentPatientList: List<PatientListViewModel.PatientItem>? = null,
  ) {
    val patients = currentPatientList ?: viewModel.liveSearchedPatients.value ?: emptyList()
    val selectedCount = patientSelectionAdapter.getSelectedPatientIds().size

    // Update "Start Encounter" button
    binding.buttonStartEncounter.isEnabled = selectedCount > 0

    // Update "Select All" checkbox state
    binding.checkboxSelectAll.isEnabled = patients.isNotEmpty()
    if (patients.isNotEmpty()) {
      isProgrammaticallyChangingSelectAll = true
      binding.checkboxSelectAll.isChecked = patientSelectionAdapter.isAllSelected(patients)
      isProgrammaticallyChangingSelectAll = false
    } else {
      isProgrammaticallyChangingSelectAll = true
      binding.checkboxSelectAll.isChecked = false // No items to select
      isProgrammaticallyChangingSelectAll = false
    }
  }

  override fun onStart() {
    super.onStart()
    dialog
      ?.window
      ?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
