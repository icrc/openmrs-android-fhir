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
import org.openmrs.android.fhir.ui.components.IdentifierTypesDialog
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.IdentifierSelectionViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

/**
 * Opens the identifier types popup over an empty screen; closing it goes back. Settings shows the
 * same popup in place, this is for the places that send the user here (a guest's patients cannot be
 * uploaded before the types are chosen).
 */
class IdentifierFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private val identifierSelectionViewModel by
    viewModels<IdentifierSelectionViewModel> { viewModelFactory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    return composeView {
      OpenMrsTheme {
        val uiState by identifierSelectionViewModel.uiState.collectAsStateWithLifecycle()
        IdentifierTypesDialog(
          identifierTypes = uiState.identifierTypes,
          selectedIds = uiState.selectedIdentifierIds,
          isLoading = uiState.isLoading,
          onSave = {
            identifierSelectionViewModel.saveSelection(it)
            close()
          },
          onDismiss = ::close,
        )
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.select_identifier_types)
      setDisplayHomeAsUpEnabled(true)
    }
    identifierSelectionViewModel.loadIdentifiers()
    mainActivityViewModel.setDrawerEnabled(false)
  }

  private fun close() {
    NavHostFragment.findNavController(this).navigateUp()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        close()
        true
      }
      else -> false
    }
  }
}
