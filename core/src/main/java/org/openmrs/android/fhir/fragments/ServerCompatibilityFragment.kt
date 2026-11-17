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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.NavHostFragment
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.ServerCompatibilityScreen
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.ServerCompatibilityViewModel

class ServerCompatibilityFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<ServerCompatibilityViewModel> { viewModelFactory }
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
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      ServerCompatibilityScreen(
        state = state,
        onCheckAgain = viewModel::checkAgain,
        onWriteTest = viewModel::testWriting,
        onCopy = ::copyReport,
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.title_server_compatibility)
      setDisplayHomeAsUpEnabled(true)
    }
    mainActivityViewModel.setDrawerEnabled(false)
  }

  private fun copyReport() {
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("report", viewModel.reportText()))
    AppMessage.show(requireContext(), getString(R.string.compat_copied), MessageKind.Success)
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
