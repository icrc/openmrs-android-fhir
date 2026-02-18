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
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.fhir.sync.CurrentSyncJobStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.extensions.getServerConnectivityState
import org.openmrs.android.fhir.ui.screens.PatientListSelectionScreen
import org.openmrs.android.fhir.ui.screens.selectionScreenContentPadding
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

class SelectPatientListFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var apiManager: ApiManager
  private val selectPatientListViewModel by
    viewModels<SelectPatientListViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private var fromLogin = false
  private var filterPatientListsByGroup = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
    fromLogin = arguments?.getBoolean("from_login") ?: false
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MaterialTheme {
          val patientLists by
            selectPatientListViewModel.selectPatientListItems.observeAsState(emptyList())
          val uiState by selectPatientListViewModel.uiState.collectAsStateWithLifecycle()
          val filteredPatientLists =
            remember(patientLists, uiState.query) {
              patientLists.filter { it.name.contains(uiState.query, true) }
            }

          PatientListSelectionScreen(
            query = uiState.query,
            onQueryChange = selectPatientListViewModel::onQueryChanged,
            patientLists = filteredPatientLists,
            selectedPatientListIds = uiState.selectedPatientListIds,
            showTitle = fromLogin,
            showActionButton = fromLogin,
            showEmptyState = patientLists.isEmpty(),
            isLoading = uiState.isLoading,
            onPatientListToggle = ::onSelectPatientListItemClicked,
            onActionClick = ::handleActionClick,
            topPadding =
              if (fromLogin) {
                20.dp
              } else {
                selectionScreenContentPadding().calculateTopPadding()
              },
          )
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
    (requireActivity().application as FhirApplication).appComponent.inject(this)

    filterPatientListsByGroup =
      requireContext().resources.getBoolean(R.bool.filter_patient_lists_by_group)

    lifecycleScope.launch {
      actionBar?.title = requireContext().getString(R.string.select_patient_lists)
      configureUi(actionBar)

      if (filterPatientListsByGroup) {
        selectPatientListViewModel.getSelectPatientListItems()
        observeSelectedLocation()
      } else {
        triggerLegacyInitialSync()
      }
    }
    observePollState()

    selectPatientListViewModel.selectPatientListItems.observe(viewLifecycleOwner) {
      selectPatientListViewModel.onLoadingChanged(false)
    }
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
              selectPatientListViewModel.onLoadingChanged(true)
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

  private fun configureUi(actionBar: androidx.appcompat.app.ActionBar?) {
    if (fromLogin) {
      actionBar?.hide()
      mainActivityViewModel.setDrawerEnabled(true)
    } else {
      actionBar?.setDisplayHomeAsUpEnabled(true)
      mainActivityViewModel.setDrawerEnabled(false)
    }
  }

  private fun handleActionClick() {
    val hasSelection = selectPatientListViewModel.uiState.value.selectedPatientListIds.isNotEmpty()
    val hasLists = !selectPatientListViewModel.selectPatientListItems.value.isNullOrEmpty()
    if (!hasSelection && hasLists) {
      AlertDialog.Builder(requireContext()).apply {
        setTitle(getString(R.string.select_patient_list_dialog_title))
        setMessage(getString(R.string.no_patient_list_selected_message))
        setPositiveButton(getString(R.string.yes)) { dialog, _ -> dialog.dismiss() }
        setNegativeButton(getString(R.string.no)) { dialog, _ ->
          proceedToHomeFragment()
          dialog.dismiss()
        }
        show()
      }
    } else {
      proceedToHomeFragment()
    }
  }

  private fun triggerLegacyInitialSync() {
    if (fromLogin) {
      selectPatientListViewModel.getSelectPatientListItems()
    } else {
      viewLifecycleOwner.lifecycleScope.launch {
        selectPatientListViewModel.onLoadingChanged(true)
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
      selectPatientListViewModel.onPatientListToggle(selectPatientListItem.resourceId, true)

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

  private fun selectPatientListItem(
    selectPatientListItem: SelectPatientListViewModel.SelectPatientListItem,
  ) {
    selectPatientListViewModel.onPatientListToggle(selectPatientListItem.resourceId, false)
    Toast.makeText(context, getString(R.string.added_patient_list_to_sync), Toast.LENGTH_SHORT)
      .show()
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
              selectPatientListViewModel.onLoadingChanged(false)
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
}
