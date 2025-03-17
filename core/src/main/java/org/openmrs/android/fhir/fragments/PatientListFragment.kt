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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import javax.inject.Inject
import kotlin.getValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.PatientItemRecyclerViewAdapter
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentPatientListBinding
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import timber.log.Timber

class PatientListFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val patientListViewModel by viewModels<PatientListViewModel> { viewModelFactory }
  private lateinit var patientQuery: String
  private var _binding: FragmentPatientListBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentPatientListBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = resources.getString(R.string.title_patient_list)
      setDisplayHomeAsUpEnabled(true)
    }
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    observeLoading()
    val recyclerView: RecyclerView = binding.patientListContainer.patientList
    val adapter = PatientItemRecyclerViewAdapter(this::onPatientItemClicked)
    recyclerView.adapter = adapter

    patientListViewModel.liveSearchedPatients.observe(viewLifecycleOwner) {
      Timber.d("Submitting ${it.count()} patient records")
      binding.emptyStateContainer.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
      adapter.submitList(it)
    }

    patientQuery = binding.patientInputEditText.text.toString()
    addSearchTextChangeListener()

    requireActivity()
      .onBackPressedDispatcher
      .addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            if (binding.patientInputEditText.text.toString().trim().isNotEmpty()) {
              binding.patientInputEditText.setText("")
            } else {
              isEnabled = false
              activity?.onBackPressed()
            }
          }
        },
      )

    binding.apply { addPatient.setOnClickListener { onAddPatientClick() } }
    setHasOptionsMenu(true)
    (activity as MainActivity).setDrawerEnabled(false)
  }

  private fun addSearchTextChangeListener() {
    binding.patientInputEditText.doOnTextChanged { newText, _, _, _ ->
      patientListViewModel.searchPatientsByName(newText.toString().trim())
    }
  }

  private fun observeLoading() {
    patientListViewModel.isLoading.observe(viewLifecycleOwner) {
      binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        if (binding.patientInputEditText.text.toString().trim().isNotEmpty()) {
          binding.patientInputEditText.setText("")
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
    findNavController()
      .navigate(PatientListFragmentDirections.navigateToProductDetail(patientItem.resourceId))
  }

  private fun onAddPatientClick() {
    lifecycleScope.launch {
      if (
        context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME) !=
          null
      ) {
        findNavController()
          .navigate(PatientListFragmentDirections.actionPatientListToAddPatientFragment())
      } else {
        Toast.makeText(context, "Please select a location first", Toast.LENGTH_LONG).show()
      }
    }
  }
}
