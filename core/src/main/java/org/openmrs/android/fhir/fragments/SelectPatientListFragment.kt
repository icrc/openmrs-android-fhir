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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.SelectPatientListItemRecyclerViewAdapter
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentSelectPatientListBinding
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

class SelectPatientListFragment : Fragment(R.layout.fragment_select_patient_list) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val selectPatientListViewModel by
    viewModels<SelectPatientListViewModel> { viewModelFactory }

  private var _binding: FragmentSelectPatientListBinding? = null
  private lateinit var selectPatientListAdapter: SelectPatientListItemRecyclerViewAdapter

  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentSelectPatientListBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    selectPatientListAdapter =
      SelectPatientListItemRecyclerViewAdapter(this::onSelectPatientListItemClicked)
    val selectPatientListRecyclerView: RecyclerView = binding.selectPatientListRecylcerView
    selectPatientListRecyclerView.adapter = selectPatientListAdapter

    lifecycleScope.launch {
      actionBar?.title = requireContext().getString(R.string.select_patient_lists)

      val selectedPatientListIds =
        context
          ?.applicationContext
          ?.dataStore
          ?.data
          ?.first()
          ?.get(PreferenceKeys.SELECTED_PATIENT_LISTS)

      selectedPatientListIds?.let { selectedPatientListIds ->
        if (::selectPatientListAdapter.isInitialized) {
          selectPatientListAdapter.selectPatientListItem(selectedPatientListIds)
        }
      }
    }

    arguments?.let {
      val fromLogin = it.getBoolean("from_login")
      if (fromLogin) {
        actionBar?.hide()
        (activity as MainActivity).setDrawerEnabled(true)
        binding.titleTextView.visibility = View.VISIBLE
        binding.actionButton.visibility = View.VISIBLE
        binding.actionButton.setOnClickListener {
          if (
            !selectPatientListAdapter.isAnyPatientListItemSelected() &&
              !selectPatientListViewModel.selectPatientListItems.value.isNullOrEmpty()
          ) {
            AlertDialog.Builder(requireContext()).apply {
              setTitle("Select Patient List")
              setMessage("No patient list is selected. Do you want to select one?")
              setPositiveButton("Yes") { dialog, _ -> dialog.dismiss() }
              setNegativeButton("No") { dialog, _ ->
                proceedToHomeFragment()
                dialog.dismiss()
              }
              show()
            }
          } else {
            proceedToHomeFragment()
          }
        }
      } else {
        actionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as MainActivity).setDrawerEnabled(false)
      }
    }

    binding.progressBar.visibility = View.VISIBLE
    selectPatientListViewModel.getSelectPatientListItems()
    selectPatientListViewModel.selectPatientListItems.observe(viewLifecycleOwner) {
      binding.progressBar.visibility = View.GONE
      if (::selectPatientListAdapter.isInitialized) {
        val selectedPatientList = selectPatientListViewModel.getSelectPatientListItemsListFiltered()
        if (selectedPatientList.isEmpty()) {
          binding.emptyStateContainer.visibility = View.VISIBLE
        } else {
          binding.emptyStateContainer.visibility = View.GONE
          selectPatientListAdapter.submitList(
            selectedPatientList,
          )
        }
      }
    }

    addSearchTextChangeListener()
  }

  private fun proceedToHomeFragment() {
    (activity as MainActivity).onSyncPress()
    NavHostFragment.findNavController(this)
      .navigate(SelectPatientListFragmentDirections.actionSelectPatientListFragmentToHomeFragment())
  }

  private fun onSelectPatientListItemClicked(
    selectPatientListItem: SelectPatientListViewModel.SelectPatientListItem,
    isSelected: Boolean,
  ) {
    if (isSelected) {
      lifecycleScope.launch {
        context?.applicationContext?.dataStore?.edit { preferences ->
          preferences[PreferenceKeys.SELECTED_PATIENT_LISTS] =
            preferences[PreferenceKeys.SELECTED_PATIENT_LISTS]?.minus(
              setOf(selectPatientListItem.resourceId),
            )
              ?: mutableSetOf()
        }
      }

      if (::selectPatientListAdapter.isInitialized) {
        selectPatientListAdapter.removeSelectPatientListItem(selectPatientListItem.resourceId)
      }
      Toast.makeText(context, "Removed Patient List from Sync", Toast.LENGTH_SHORT).show()
    } else {
      selectPatientListItem(selectPatientListItem)
    }
  }

  private fun addSearchTextChangeListener() {
    binding.selectPatientListInputEditText.doOnTextChanged { text, _, _, _ ->
      if (::selectPatientListAdapter.isInitialized) {
        selectPatientListAdapter.submitList(
          selectPatientListViewModel.getSelectPatientListItemsListFiltered(text.toString()),
        )
      }
    }
  }

  private fun selectPatientListItem(
    selectPatientListItem: SelectPatientListViewModel.SelectPatientListItem,
  ) {
    lifecycleScope.launch {
      context?.applicationContext?.dataStore?.edit { preferences ->
        preferences[PreferenceKeys.SELECTED_PATIENT_LISTS] =
          preferences[PreferenceKeys.SELECTED_PATIENT_LISTS]?.plus(
            setOf(selectPatientListItem.resourceId),
          )
            ?: mutableSetOf()
      }
      if (::selectPatientListAdapter.isInitialized) {
        selectPatientListAdapter.addSelectPatientListItem(selectPatientListItem.resourceId)
      }

      Toast.makeText(context, "Added Patient List to Sync", Toast.LENGTH_SHORT).show()
    }
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

  override fun onPause() {
    super.onPause()
    (requireActivity() as AppCompatActivity).supportActionBar?.show()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
