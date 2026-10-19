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

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.NavHostFragment
import javax.inject.Inject
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.data.sync.SyncMode
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.extensions.saveToFile
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.SyncScreen
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SyncInfoViewModel
import org.openmrs.android.fhir.viewmodel.UnsyncedResourcesViewModel

/** Synchronization in one screen: sync now, changes waiting to be uploaded, and the history. */
class SyncInfoFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val syncInfoViewModel by viewModels<SyncInfoViewModel> { viewModelFactory }
  private val unsyncedViewModel by viewModels<UnsyncedResourcesViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

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
    return composeView {
      OpenMrsTheme {
        val sessions by syncInfoViewModel.syncSessions.observeAsState(emptyList())
        val pending by unsyncedViewModel.resources.observeAsState(emptyList())
        val isLoadingPending by unsyncedViewModel.isLoading.observeAsState(false)
        val isSyncing by mainActivityViewModel.isSyncing.observeAsState(false)
        val activityState by mainActivityViewModel.uiState.collectAsStateWithLifecycle()
        // What "Sync now" does until the user picks another: the automatic sync's setting.
        val context = androidx.compose.ui.platform.LocalContext.current
        val defaultMode by
          remember { DemoDataStore(context.applicationContext).getAutoSyncModeFlow() }
            .collectAsStateWithLifecycle(initialValue = SyncMode.BOTH)
        SyncScreen(
          defaultSyncMode = defaultMode,
          syncSessions = sessions,
          pendingResources = pending,
          isLoadingPending = isLoadingPending,
          isSyncing = isSyncing,
          syncProgress = activityState.syncProgressState,
          lastSyncText = activityState.lastSyncText,
          onSyncNow = { mode -> (requireActivity() as? MainActivity)?.onSyncPress(mode) },
          onTogglePatientExpand = unsyncedViewModel::togglePatientExpansion,
          onToggleEncounterExpand = unsyncedViewModel::toggleEncounterExpansion,
          onDeleteResource = { resource ->
            unsyncedViewModel.deleteResource(resource)
            toast(R.string.resource_deleted)
          },
          onDownloadResource = { resource ->
            unsyncedViewModel.downloadResource(resource)
            showDownloadToast(resource)
          },
          onDeleteAllPending = {
            unsyncedViewModel.deleteAll()
            toast(R.string.all_resources_deleted)
          },
          onDownloadAllPending = {
            unsyncedViewModel.downloadAll()
            toast(R.string.downloading_all_resources)
          },
          onDeleteSession = syncInfoViewModel::deleteSyncSession,
          onClearHistory = syncInfoViewModel::clearAllSyncSessions,
          onImportExport = {
            NavHostFragment.findNavController(this@SyncInfoFragment)
              .navigate(R.id.action_syncInfoFragment_to_importExportFragment)
          },
        )
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.nav_sync)
      setDisplayHomeAsUpEnabled(true)
    }
    mainActivityViewModel.setDrawerEnabled(false)
    observeDownloadResources()
    observeSyncFinished()
  }

  /** What was just uploaded no longer belongs in the pending list. */
  private fun observeSyncFinished() {
    var wasSyncing = false
    mainActivityViewModel.isSyncing.observe(viewLifecycleOwner) { syncing ->
      if (wasSyncing && !syncing) unsyncedViewModel.loadUnsyncedResources()
      wasSyncing = syncing
    }
  }

  private fun toast(messageResId: Int) {
    AppMessage.show(requireContext(), getString(messageResId), MessageKind.Info)
  }

  private fun showDownloadToast(resource: UnsyncedResource) {
    val message =
      when (resource) {
        is UnsyncedResource.PatientItem ->
          getString(R.string.collecting_patient_data, resource.patient.name)
        is UnsyncedResource.EncounterItem ->
          getString(R.string.collecting_encounter_data, resource.encounter.title)
        is UnsyncedResource.ObservationItem ->
          getString(R.string.collecting_observation_data, resource.observation.title)
      }
    AppMessage.show(requireContext(), message, MessageKind.Info)
  }

  private fun observeDownloadResources() {
    unsyncedViewModel.downloadResource.observe(viewLifecycleOwner) { resource ->
      val bundle = saveToFile(requireContext().applicationContext, "bundle.json", resource)
      if (bundle != null) {
        val emailIntent =
          Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.get(requireContext()).supportEmail))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.unsynced_resources_email_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.unsynced_resources_email_body))
            putExtra(
              Intent.EXTRA_STREAM,
              FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().applicationContext.packageName}.provider",
                bundle,
              ),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        startActivity(
          Intent.createChooser(emailIntent, getString(R.string.send_unsynced_resources)),
        )
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
}
