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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.PatientListSelectionScreen
import org.openmrs.android.fhir.ui.screens.selectionScreenContentPadding
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

class SelectPatientListFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val selectPatientListViewModel by
    viewModels<SelectPatientListViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private var fromLogin = false
  private val locationName = androidx.compose.runtime.mutableStateOf<String?>(null)

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
    return composeView {
      OpenMrsTheme {
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
          // Placeholders until the device has been read, and while the first lists are fetched.
          isLoading = uiState.isLoading || (uiState.isRefreshing && patientLists.isEmpty()),
          onPatientListToggle = ::onSelectPatientListItemClicked,
          onActionClick = ::handleActionClick,
          locationName = locationName.value,
          onChangeLocationClick = if (fromLogin) ::changeLocation else null,
          topPadding = selectionScreenContentPadding().calculateTopPadding(),
          isRefreshing = uiState.isRefreshing,
          loadFailed = uiState.loadFailed,
          onRetry = selectPatientListViewModel::refresh,
          continueWithoutSelection = true,
        )
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
    (requireActivity().application as FhirApplication).appComponent.inject(this)

    // The name is saved a moment after the location is picked: follow it.
    viewLifecycleOwner.lifecycleScope.launch {
      requireContext()
        .applicationContext
        .dataStore
        .data
        .map { it[PreferenceKeys.LOCATION_NAME] }
        .distinctUntilChanged()
        .collect { locationName.value = it }
    }
    actionBar?.title = requireContext().getString(R.string.select_patient_lists)
    configureUi(actionBar)
    // The lists on the device show at once; the server is asked in the background.
    selectPatientListViewModel.start(fromLogin)
  }

  /** Back to the location list; the lists are rebuilt for whichever location is chosen. */
  private fun changeLocation() {
    findNavController().navigateUp()
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
      // Allowed, but said: with no list no patients are downloaded (never every patient of the
      // server), and the lists can be chosen later from the home screen.
      MaterialAlertDialogBuilder(requireContext()).apply {
        setTitle(getString(R.string.patient_lists_continue_without_title))
        setMessage(getString(R.string.patient_lists_continue_without_message))
        setPositiveButton(getString(R.string.patient_lists_continue_action)) { dialog, _ ->
          dialog.dismiss()
          proceedToHomeFragment()
        }
        setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
        show()
      }
    } else {
      proceedToHomeFragment()
    }
  }

  private fun onSelectPatientListItemClicked(
    selectPatientListItem: SelectPatientListViewModel.SelectPatientListItem,
    isSelected: Boolean,
  ) {
    if (isSelected) {
      selectPatientListViewModel.onPatientListToggle(selectPatientListItem.resourceId, true)

      AppMessage.show(
        context,
        getString(R.string.removed_patient_list_from_sync),
        MessageKind.Success,
      )
    } else {
      selectPatientListItem(selectPatientListItem)
    }
  }

  private fun selectPatientListItem(
    selectPatientListItem: SelectPatientListViewModel.SelectPatientListItem,
  ) {
    selectPatientListViewModel.onPatientListToggle(selectPatientListItem.resourceId, false)
    AppMessage.show(context, getString(R.string.added_patient_list_to_sync), MessageKind.Success)
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
