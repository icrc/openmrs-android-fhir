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
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.UnsyncedResourcesAdapter
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.databinding.FragmentUnsyncedResourcesBinding
import org.openmrs.android.fhir.extensions.saveToFile
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.UnsyncedResourcesViewModel

class UnsyncedResourcesFragment : Fragment() {

  private var _binding: FragmentUnsyncedResourcesBinding? = null
  private val binding
    get() = _binding!!

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<UnsyncedResourcesViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private lateinit var adapter: UnsyncedResourcesAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    _binding = FragmentUnsyncedResourcesBinding.inflate(inflater, container, false)
    return binding.root
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
    observeLoading()

    // Setup RecyclerView
    adapter =
      UnsyncedResourcesAdapter(
        onTogglePatientExpand = { patientId -> viewModel.togglePatientExpansion(patientId) },
        onToggleEncounterExpand = { encounterId ->
          viewModel.toggleEncounterExpansion(encounterId)
        },
        onDelete = { resource -> showDeleteConfirmationDialog(resource) },
        onDownload = { resource ->
          viewModel.downloadResource(resource)
          showDownloadToast(resource)
        },
      )

    binding.rvUnsyncedResources.layoutManager = LinearLayoutManager(requireContext())
    binding.rvUnsyncedResources.adapter = adapter

    // Setup button click listeners
    binding.btnDeleteAll.setOnClickListener { showDeleteAllConfirmationDialog() }

    binding.btnDownloadAll.setOnClickListener {
      viewModel.downloadAll()
      Toast.makeText(
          requireContext(),
          "Downloading all resources",
          Toast.LENGTH_SHORT,
        )
        .show()
    }
    // Observe ViewModel
    viewModel.resources.observe(viewLifecycleOwner) { resources ->
      if (resources.isEmpty()) {
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.rvUnsyncedResources.visibility = View.GONE
      } else {
        binding.emptyStateContainer.visibility = View.GONE
        binding.rvUnsyncedResources.visibility = View.VISIBLE
      }
      adapter.submitList(resources)
    }
    observeDownloadResources()
    mainActivityViewModel.setDrawerEnabled(false)
  }

  private fun showDeleteConfirmationDialog(resource: UnsyncedResource) {
    val resourceType =
      when (resource) {
        is UnsyncedResource.PatientItem -> "patient"
        is UnsyncedResource.EncounterItem -> "encounter"
        is UnsyncedResource.ObservationItem -> "observation"
      }

    MaterialAlertDialogBuilder(requireContext())
      .setTitle("Delete Resource")
      .setMessage("Are you sure you want to delete this $resourceType?")
      .setPositiveButton("Delete") { _, _ ->
        viewModel.deleteResource(resource)
        Toast.makeText(
            requireContext(),
            "Resource deleted",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showDeleteAllConfirmationDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle("Delete All")
      .setMessage("Are you sure you want to delete all unsynced resources?")
      .setPositiveButton("Delete All") { _, _ ->
        viewModel.deleteAll()
        Toast.makeText(
            requireContext(),
            "All resources deleted",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showDownloadToast(resource: UnsyncedResource) {
    val message =
      when (resource) {
        is UnsyncedResource.PatientItem -> "Collecting patient data for ${resource.patient.name}"
        is UnsyncedResource.EncounterItem ->
          "Collecting encounter data: ${resource.encounter.title}"
        is UnsyncedResource.ObservationItem ->
          "Collecting observation: ${resource.observation.title}"
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
            putExtra(Intent.EXTRA_SUBJECT, "Unsynced Resources")
            putExtra(Intent.EXTRA_TEXT, "Attached are the unsynced resources.")
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
        startActivity(Intent.createChooser(emailIntent, "Send Unsynced Resources"))
      }
    }
  }

  private fun observeLoading() {
    viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
      binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
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

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
