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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.NavHostFragment
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.ui.screens.IdentifierSelectionScreen
import org.openmrs.android.fhir.viewmodel.IdentifierSelectionViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

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
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MaterialTheme {
          val uiState by identifierSelectionViewModel.uiState.collectAsStateWithLifecycle()
          val filteredIdentifiers =
            remember(uiState.identifierTypes, uiState.query) {
              uiState.identifierTypes.filter { it.display?.contains(uiState.query) ?: true }
            }

          IdentifierSelectionScreen(
            query = uiState.query,
            onQueryChange = identifierSelectionViewModel::onQueryChanged,
            identifierTypes = filteredIdentifiers,
            selectedIdentifierIds = uiState.selectedIdentifierIds,
            isLoading = uiState.isLoading,
            onIdentifierToggle = ::onIdentifierTypeItemClicked,
          )
        }
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

  private fun onIdentifierTypeItemClicked(identifierTypeItem: IdentifierType, isSelected: Boolean) {
    if (!identifierTypeItem.required) {
      identifierSelectionViewModel.onIdentifierToggle(identifierTypeItem, isSelected)
      Toast.makeText(
          context,
          if (isSelected) {
            getString(R.string.identifier_removed)
          } else {
            getString(R.string.identifier_added)
          },
          Toast.LENGTH_SHORT,
        )
        .show()
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
