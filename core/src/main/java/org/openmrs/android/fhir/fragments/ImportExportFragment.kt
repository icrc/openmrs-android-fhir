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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.screens.ImportExportScreen
import org.openmrs.android.fhir.viewmodel.ImportExportViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

/** Export what cannot be synced to a PIN-protected file, or import such a file and upload it. */
class ImportExportFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<ImportExportViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private val pickFile =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let(viewModel::fileChosen)
    }

  private val saveFile =
    registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
      uri ->
      uri?.let(viewModel::saveExportTo)
    }

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
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      ImportExportScreen(
        state = state,
        onExport = viewModel::export,
        onShare = ::shareExport,
        onSave = { state.exported?.let { saveFile.launch(it.file.name) } },
        onChooseFile = { pickFile.launch(arrayOf("*/*")) },
        onImport = viewModel::import,
        onCancelImport = viewModel::cancelImport,
        onUpload = { (requireActivity() as? MainActivity)?.onSyncPress() },
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.title_import_export)
      setDisplayHomeAsUpEnabled(true)
    }
    mainActivityViewModel.setDrawerEnabled(false)
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.messages.collect { message ->
          AppMessage.show(
            requireContext(),
            getString(message.resId, *message.args.toTypedArray()),
            message.kind,
          )
        }
      }
    }
    // After a sync there is less (or nothing) left to export.
    var wasSyncing = false
    mainActivityViewModel.isSyncing.observe(viewLifecycleOwner) { syncing ->
      if (wasSyncing && !syncing) viewModel.refreshPending()
      wasSyncing = syncing
    }
  }

  private fun shareExport() {
    val file = viewModel.uiState.value.exported?.file ?: return
    val uri =
      FileProvider.getUriForFile(
        requireContext(),
        "${requireContext().applicationContext.packageName}.provider",
        file,
      )
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    startActivity(Intent.createChooser(send, getString(R.string.export_share_chooser)))
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean =
    when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this).navigateUp()
        true
      }
      else -> false
    }
}
