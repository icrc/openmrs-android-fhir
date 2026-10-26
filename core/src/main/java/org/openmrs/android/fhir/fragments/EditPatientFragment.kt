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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.fhir.datacapture.QuestionnaireFragment
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.AddPatientFragmentBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.UnsavedChangesGuard
import org.openmrs.android.fhir.extensions.addQuestionnaireWhenDrawn
import org.openmrs.android.fhir.extensions.newQuestionnaireFragment
import org.openmrs.android.fhir.extensions.onQuestionnaireResult
import org.openmrs.android.fhir.extensions.savedLocation
import org.openmrs.android.fhir.extensions.setFormLoading
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.LocationBannerState
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.components.setLocationBanner
import org.openmrs.android.fhir.viewmodel.EditPatientViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

/** A fragment representing Edit Patient screen. This fragment is contained in a [MainActivity]. */
class EditPatientFragment : Fragment(R.layout.add_patient_fragment) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val viewModel: EditPatientViewModel by viewModels { viewModelSavedStateFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private var _binding: AddPatientFragmentBinding? = null

  private val binding
    get() = _binding!!

  private val unsavedChangesGuard by lazy { UnsavedChangesGuard(this) }
  private val locationBanner = mutableStateOf<LocationBannerState>(LocationBannerState.Loading)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    _binding = AddPatientFragmentBinding.bind(view)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.edit_patient)
    }

    binding.locationBanner.setLocationBanner(locationBanner, showHint = false)
    lifecycleScope.launch {
      val location = context?.savedLocation() ?: return@launch
      locationBanner.value = location.bannerState(requireContext())
    }

    setFormLoading(true)
    viewModel.livePatientData.observe(viewLifecycleOwner) { addQuestionnaireFragment(it) }
    viewModel.isPatientSaved.observe(viewLifecycleOwner) {
      if (!it) {
        val reason = viewModel.saveFailure.value ?: R.string.inputs_missing
        viewModel.saveFailure.value = null
        AppMessage.show(requireContext(), reason, MessageKind.Error)
        return@observe
      }
      AppMessage.show(requireContext(), R.string.message_patient_updated, MessageKind.Success)
      NavHostFragment.findNavController(this).navigateUp()
    }
    mainActivityViewModel.setDrawerEnabled(false)
    unsavedChangesGuard.attach(binding.root)

    /** Use the provided cancel|submit buttons from the sdc library */
    onQuestionnaireResult(
      onSubmit = ::onSubmitAction,
      onCancel = {
        unsavedChangesGuard.confirmLeave {
          NavHostFragment.findNavController(this@EditPatientFragment).navigateUp()
        }
      },
    )
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        unsavedChangesGuard.confirmLeave {
          NavHostFragment.findNavController(this@EditPatientFragment).navigateUp()
        }
        true
      }
      else -> false
    }
  }

  private fun addQuestionnaireFragment(pair: Pair<String, String>) {
    if (pair.first.isEmpty()) {
      AppMessage.show(requireContext(), R.string.questionnaire_error_message, MessageKind.Error)
      NavHostFragment.findNavController(this).navigateUp()
      return
    }
    addQuestionnaireWhenDrawn(
      containerId = R.id.add_patient_container,
      tag = QUESTIONNAIRE_FRAGMENT_TAG,
      create = { newQuestionnaireFragment(pair.first, responseJson = pair.second) },
    )
  }

  private fun onSubmitAction() {
    lifecycleScope.launch {
      val questionnaireFragment =
        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
      viewModel.updatePatient(questionnaireFragment.getQuestionnaireResponse())
    }
  }

  companion object {
    const val QUESTIONNAIRE_FRAGMENT_TAG = "edit-questionnaire-fragment-tag"
  }
}
