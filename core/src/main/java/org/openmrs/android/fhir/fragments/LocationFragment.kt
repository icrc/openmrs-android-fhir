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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.google.android.fhir.sync.SyncJobStatus
import com.google.android.fhir.sync.SyncOperation
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.extensions.getServerConnectivityState
import org.openmrs.android.fhir.ui.screens.LocationSelectionScreen
import org.openmrs.android.fhir.ui.screens.selectionScreenContentPadding
import org.openmrs.android.fhir.viewmodel.LocationViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

class LocationFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var apiManager: ApiManager
  private val locationViewModel by viewModels<LocationViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private var fromLogin = false

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
          val locations by locationViewModel.locations.observeAsState(emptyList())
          val uiState by locationViewModel.uiState.collectAsStateWithLifecycle()
          val filteredFavorites =
            remember(locations, uiState.favoriteLocationIds, uiState.query) {
              locations.filter {
                uiState.favoriteLocationIds.contains(it.resourceId) &&
                  it.name.contains(uiState.query, true)
              }
            }
          val filteredLocations =
            remember(locations, uiState.favoriteLocationIds, uiState.query) {
              locations.filter {
                !uiState.favoriteLocationIds.contains(it.resourceId) &&
                  it.name.contains(uiState.query, true)
              }
            }

          if (uiState.showContent) {
            LocationSelectionScreen(
              query = uiState.query,
              onQueryChange = locationViewModel::onQueryChanged,
              favoriteLocations = filteredFavorites,
              locations = filteredLocations,
              selectedLocationId = uiState.selectedLocationId,
              showTitle = fromLogin,
              showActionButton = fromLogin,
              showEmptyState = locations.isEmpty(),
              isLoading = uiState.isLoading,
              onLocationClick = ::selectLocationItem,
              onFavoriteToggle = ::onFavoriteLocationToggle,
              onActionClick = ::handleActionClick,
              topPadding =
                if (fromLogin) {
                  20.dp
                } else {
                  selectionScreenContentPadding().calculateTopPadding()
                },
            )
          } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE8F0FE)))
          }
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
    (requireActivity().application as FhirApplication).appComponent.inject(this)

    viewLifecycleOwner.lifecycleScope.launch {
      val preferences = context?.applicationContext?.dataStore?.data?.first()
      val savedLocationName = preferences?.get(PreferenceKeys.LOCATION_NAME)
      actionBar?.title = savedLocationName ?: requireContext().getString(R.string.select_a_location)
    }

    observePollState()

    if (fromLogin) {
      showSyncTasksScreen()
      viewLifecycleOwner.lifecycleScope.launch {
        when (requireContext().getServerConnectivityState(apiManager)) {
          ServerConnectivityState.ServerConnected -> locationViewModel.fetchPreSyncData()
          else -> locationViewModel.getLocations()
        }
      }
      actionBar?.hide()
      mainActivityViewModel.setDrawerEnabled(true)
    } else {
      viewLifecycleOwner.lifecycleScope.launch {
        locationViewModel.onLoadingChanged(true)
        when (requireContext().getServerConnectivityState(apiManager)) {
          ServerConnectivityState.ServerConnected -> locationViewModel.fetchLocations()
          else -> locationViewModel.getLocations()
        }
      }
      actionBar?.setDisplayHomeAsUpEnabled(true)
      mainActivityViewModel.setDrawerEnabled(false)
    }

    locationViewModel.locations.observe(viewLifecycleOwner) {
      showLocationScreen()
      locationViewModel.onLoadingChanged(false)
    }
  }

  private fun showSyncTasksScreen() {
    mainActivityViewModel.showSyncTasksScreen(
      headerTextResId = R.string.get_started,
      showCloseButton = false,
    )
    locationViewModel.onShowContentChanged(false)
  }

  private fun showLocationScreen() {
    mainActivityViewModel.hideSyncTasksScreen()
    locationViewModel.onShowContentChanged(true)
  }

  private fun handleActionClick() {
    if (
      locationViewModel.uiState.value.selectedLocationId.isNullOrBlank() &&
        !locationViewModel.locations.value.isNullOrEmpty()
    ) {
      AlertDialog.Builder(requireContext()).apply {
        setTitle(getString(R.string.select_location))
        setMessage(getString(R.string.no_location_is_selected_do_you_want_to_select_one))
        setPositiveButton(getString(R.string.yes)) { dialog, _ -> dialog.dismiss() }
        setNegativeButton(getString(R.string.no)) { dialog, _ ->
          findNavController()
            .navigate(
              LocationFragmentDirections.actionLocationFragmentToSelectPatientListFragment(true),
            )
          dialog.dismiss()
        }
        show()
      }
    } else {
      findNavController()
        .navigate(
          LocationFragmentDirections.actionLocationFragmentToSelectPatientListFragment(true)
        )
    }
  }

  private fun observePollState() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        locationViewModel.pollState.collect {
          when (it) {
            is CurrentSyncJobStatus.Succeeded -> {
              showLocationScreen()
              locationViewModel.getLocations()
            }
            is CurrentSyncJobStatus.Running -> {
              if (fromLogin) {
                locationViewModel.onLoadingChanged(false)
              }
              if (it.inProgressSyncJob is SyncJobStatus.InProgress) {
                val inProgressState = it.inProgressSyncJob as SyncJobStatus.InProgress
                if (inProgressState.syncOperation == SyncOperation.DOWNLOAD) {
                  mainActivityViewModel.updateSyncProgress(
                    current = inProgressState.completed,
                    total = inProgressState.total,
                  )
                }
              }
            }
            is CurrentSyncJobStatus.Failed -> {
              showLocationScreen()
              locationViewModel.getLocations()
              Toast.makeText(
                  context,
                  getString(R.string.failed_to_fetch_all_locations),
                  Toast.LENGTH_SHORT,
                )
                .show()
            }
            else -> {
              showLocationScreen()
            }
          }
        }
      }
    }
  }

  private fun onFavoriteLocationToggle(
    locationItem: LocationViewModel.LocationItem,
    isFavorite: Boolean,
  ) {
    locationViewModel.onFavoriteToggle(locationItem.resourceId, isFavorite)
    Toast.makeText(
        context,
        if (isFavorite) {
          getString(R.string.location_removed_from_favorites)
        } else {
          getString(R.string.location_added_to_favorites)
        },
        Toast.LENGTH_SHORT,
      )
      .show()
  }

  private fun selectLocationItem(locationItem: LocationViewModel.LocationItem) {
    lifecycleScope.launch {
      locationViewModel.onLocationSelected(locationItem.resourceId, locationItem.name)
      mainActivityViewModel.updateLocationName(locationItem.name)
      (requireActivity() as AppCompatActivity).supportActionBar?.title = locationItem.name
      locationViewModel.updateSessionLocation(locationItem.resourceId)
    }

    Toast.makeText(context, getString(R.string.location_updated), Toast.LENGTH_SHORT).show()
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
