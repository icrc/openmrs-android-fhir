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
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.sync.CurrentSyncJobStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.SelectPatientListItemRecyclerViewAdapter
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.databinding.FragmentSelectPatientListBinding
import org.openmrs.android.fhir.extensions.getServerConnectivityState
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

class SelectPatientListFragment : Fragment(R.layout.fragment_select_patient_list) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var apiManager: ApiManager
  private val selectPatientListViewModel by
    viewModels<SelectPatientListViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private var _binding: FragmentSelectPatientListBinding? = null
  private lateinit var selectPatientListAdapter: SelectPatientListItemRecyclerViewAdapter

  private val binding
    get() = _binding!!

  var fromLogin = false
  private var filterPatientListsByGroup = false

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

    filterPatientListsByGroup =
      requireContext().resources.getBoolean(R.bool.filter_patient_lists_by_group)

    lifecycleScope.launch {
      if (!isAdded) {
        return@launch
      }

      val appContext = requireContext().applicationContext
      val preferences = appContext.dataStore.data.first()

      actionBar?.title = requireContext().getString(R.string.select_patient_lists)

      preferences[PreferenceKeys.SELECTED_PATIENT_LISTS]?.let { selectedPatientListIds ->
        if (::selectPatientListAdapter.isInitialized) {
          selectPatientListAdapter.selectPatientListItem(selectedPatientListIds)
        }
      }

      configureUi(actionBar)

      if (filterPatientListsByGroup) {
        selectPatientListViewModel.getSelectPatientListItems()
        observeSelectedLocation()
      } else {
        triggerLegacyInitialSync()
      }
    }
    observePollState()

    binding.progressBar.visibility = View.VISIBLE
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

  private fun observeSelectedLocation() {
    if (!filterPatientListsByGroup) {
      return
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val appContext = requireContext().applicationContext
        appContext.dataStore.data
          .map { preferences -> preferences[PreferenceKeys.LOCATION_ID] }
          .distinctUntilChanged()
          .collect { locationId ->
            if (!isAdded) return@collect
            val connectivityState = requireContext().getServerConnectivityState(apiManager)
            if (
              connectivityState == ServerConnectivityState.ServerConnected &&
                !locationId.isNullOrBlank()
            ) {
              binding.progressBar.visibility = View.VISIBLE
              selectPatientListViewModel.fetchPatientListItems()
            } else {
              selectPatientListViewModel.getSelectPatientListItems()
            }
          }
      }
    }
  }

  private fun proceedToHomeFragment() {
    mainActivityViewModel.requestSync()
    findNavController()
      .navigate(
        SelectPatientListFragmentDirections.actionSelectPatientListFragmentToHomeFragment(),
      )
  }

  private fun configureUi(actionBar: ActionBar?) {
    arguments?.let {
      fromLogin = it.getBoolean("from_login")
      if (fromLogin) {
        actionBar?.hide()
        mainActivityViewModel.setDrawerEnabled(true)
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
        mainActivityViewModel.setDrawerEnabled(false)
      }
    }
      ?: run {
        actionBar?.setDisplayHomeAsUpEnabled(true)
        mainActivityViewModel.setDrawerEnabled(false)
      }
  }

  private fun triggerLegacyInitialSync() {
    if (fromLogin) {
      selectPatientListViewModel.getSelectPatientListItems()
    } else {
      viewLifecycleOwner.lifecycleScope.launch {
        when (requireContext().getServerConnectivityState(apiManager)) {
          ServerConnectivityState.ServerConnected ->
            selectPatientListViewModel.fetchPatientListItems()
          else -> selectPatientListViewModel.getSelectPatientListItems()
        }
      }
    }
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
              selectPatientListItem.resourceId,
            )
              ?: mutableSetOf()
        }
      }

      if (::selectPatientListAdapter.isInitialized) {
        selectPatientListAdapter.removeSelectPatientListItem(selectPatientListItem.resourceId)
      }
      Toast.makeText(
          context,
          getString(R.string.removed_patient_list_from_sync),
          Toast.LENGTH_SHORT,
        )
        .show()
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
            selectPatientListItem.resourceId,
          )
            ?: mutableSetOf(selectPatientListItem.resourceId)
      }
      if (::selectPatientListAdapter.isInitialized) {
        selectPatientListAdapter.addSelectPatientListItem(selectPatientListItem.resourceId)
      }

      Toast.makeText(context, getString(R.string.added_patient_list_to_sync), Toast.LENGTH_SHORT)
        .show()
    }
  }

  private fun observePollState() {
    lifecycleScope.launch {
      selectPatientListViewModel.pollState.collect {
        when (it) {
          is CurrentSyncJobStatus.Succeeded -> {
            selectPatientListViewModel.getSelectPatientListItems()
          }
          is CurrentSyncJobStatus.Running,
          CurrentSyncJobStatus.Enqueued, -> {
            if (fromLogin) {
              binding.progressBar.visibility = View.GONE
            }
          }
          is CurrentSyncJobStatus.Failed -> {
            selectPatientListViewModel.getSelectPatientListItems()
            Toast.makeText(
                context,
                getString(R.string.failed_to_fetch_all_patient_lists),
                Toast.LENGTH_SHORT,
              )
              .show()
          }
          else -> {
            selectPatientListViewModel.getSelectPatientListItems()
          }
        }
      }
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
