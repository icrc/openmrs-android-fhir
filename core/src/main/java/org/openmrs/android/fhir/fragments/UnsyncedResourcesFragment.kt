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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.extensions.saveToFile
import org.openmrs.android.fhir.ui.screens.UnsyncedResourcesScreen
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.UnsyncedResourcesViewModel

class UnsyncedResourcesFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<UnsyncedResourcesViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: android.view.ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val resources = viewModel.resources.observeAsState(emptyList()).value
        val isLoading = viewModel.isLoading.observeAsState(false).value
        MaterialTheme {
          UnsyncedResourcesScreen(
            resources = resources,
            isLoading = isLoading,
            onTogglePatientExpand = viewModel::togglePatientExpansion,
            onToggleEncounterExpand = viewModel::toggleEncounterExpansion,
            onDeleteResource = { resource ->
              viewModel.deleteResource(resource)
              Toast.makeText(
                  requireContext(),
                  getString(R.string.resource_deleted),
                  Toast.LENGTH_SHORT,
                )
                .show()
            },
            onDownloadResource = { resource ->
              viewModel.downloadResource(resource)
              showDownloadToast(resource)
            },
            onDeleteAll = {
              viewModel.deleteAll()
              Toast.makeText(
                  requireContext(),
                  getString(R.string.all_resources_deleted),
                  Toast.LENGTH_SHORT,
                )
                .show()
            },
            onDownloadAll = {
              viewModel.downloadAll()
              Toast.makeText(
                  requireContext(),
                  getString(R.string.downloading_all_resources),
                  Toast.LENGTH_SHORT,
                )
                .show()
            },
          )
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Initialize views
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.title_unsynced_resources)
      setDisplayHomeAsUpEnabled(true)
    }
    observeDownloadResources()
    if (requireActivity() is MainActivity) {
      mainActivityViewModel.setDrawerEnabled(false)
    }
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

    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
  }

  private fun observeDownloadResources() {
    viewModel.downloadResource.observe(viewLifecycleOwner) { resource ->
      val bundle = saveToFile(requireContext().applicationContext, "bundle.json", resource)
      if (bundle != null) {
        val emailIntent =
          Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.support_email)))
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
