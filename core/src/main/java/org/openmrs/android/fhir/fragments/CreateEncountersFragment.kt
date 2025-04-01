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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.CreateFormSectionAdapter
import org.openmrs.android.fhir.data.database.model.FormSection
import org.openmrs.android.fhir.databinding.CreateEncounterFragmentBinding
import org.openmrs.android.fhir.viewmodel.CreateEncounterViewModel

class CreateEncountersFragment : Fragment(R.layout.create_encounter_fragment) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<CreateEncounterViewModel> { viewModelFactory }
  private lateinit var recyclerView: RecyclerView
  private lateinit var progressBar: ProgressBar

  private var _binding: CreateEncounterFragmentBinding? = null

  private val binding
    get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    _binding = CreateEncounterFragmentBinding.bind(view)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.new_encounter)
    }
    (requireActivity().application as FhirApplication).appComponent.inject(this)

    viewModel.loadFormData()
    // Initialize views
    recyclerView = binding.rvFormSections
    progressBar = binding.progressBar

    // Setup RecyclerView
    recyclerView.layoutManager = LinearLayoutManager(requireContext())

    // Observe data
    setupObservers()
  }

  private fun setupObservers() {
    viewModel.formData.observe(viewLifecycleOwner) { formData ->
      formData?.let { setupAdapter(it.formSections) }
    }

    viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
      progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
      errorMessage?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
    }
  }

  private fun setupAdapter(formSections: List<FormSection>) {
    val adapter = CreateFormSectionAdapter(formSections) { formId -> handleFormClick(formId) }
    recyclerView.adapter = adapter
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this).navigateUp()
        true
      }
      else -> false
    }
  }

  private fun handleFormClick(formId: String) {
    // TODO: Handle navigation to form
    Toast.makeText(requireContext(), "Form clicked: $formId", Toast.LENGTH_SHORT).show()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
