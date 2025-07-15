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
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.fhir.datacapture.QuestionnaireFragment
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.AddPatientFragmentBinding
import org.openmrs.android.fhir.viewmodel.AddPatientViewModel

/** A fragment class to show patient registration screen. */
class AddPatientFragment : Fragment(R.layout.add_patient_fragment) {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<AddPatientViewModel> { viewModelFactory }

  private var _binding: AddPatientFragmentBinding? = null

  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = AddPatientFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setHasOptionsMenu(true)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    lifecycleScope.launch {
      val selectedLocation =
        context
          ?.applicationContext
          ?.dataStore
          ?.data
          ?.first()
          ?.get(
            PreferenceKeys.LOCATION_NAME,
          )
      if (selectedLocation != null) {
        binding.currentLocationLabel.text = selectedLocation
      }
      viewModel.locationId =
        context
          ?.applicationContext
          ?.dataStore
          ?.data
          ?.first()
          ?.get(
            PreferenceKeys.LOCATION_ID,
          )
    }
    observeLoading()
    if (savedInstanceState == null) {
      viewModel.getEmbeddedQuestionnaire(
        getString(R.string.registration_questionnaire_name),
      )
    }
    observeQuestionnaire()
    observePatientSaveAction()
    (activity as MainActivity).setDrawerEnabled(false)

    /** Use the provided cancel|submit buttons from the sdc library */
    childFragmentManager.setFragmentResultListener(
      QuestionnaireFragment.SUBMIT_REQUEST_KEY,
      viewLifecycleOwner,
    ) { _, _ ->
      onSubmitAction()
    }
    childFragmentManager.setFragmentResultListener(
      QuestionnaireFragment.CANCEL_REQUEST_KEY,
      viewLifecycleOwner,
    ) { _, _ ->
      NavHostFragment.findNavController(this).navigateUp()
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

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.register_new_patient)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun addQuestionnaireFragment(questionnaire: String) {
    childFragmentManager.commit {
      add(
        R.id.add_patient_container,
        QuestionnaireFragment.builder()
          .setQuestionnaire(questionnaire)
          .showReviewPageBeforeSubmit(
            requireContext().resources.getBoolean(R.bool.show_review_page_before_submit),
          )
          .setShowCancelButton(true)
          .setShowSubmitButton(true)
          .showOptionalText(true)
          .showRequiredText(false)
          .build(),
        QUESTIONNAIRE_FRAGMENT_TAG,
      )
    }
  }

  private fun onSubmitAction() {
    viewModel.isLoading.value = true
    lifecycleScope.launch {
      val questionnaireFragment =
        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
      savePatient(questionnaireFragment.getQuestionnaireResponse())
    }
  }

  private fun savePatient(questionnaireResponse: QuestionnaireResponse) {
    val fetchIdentifiers =
      requireContext().applicationContext.resources.getBoolean(R.bool.fetch_identifiers)
    viewModel.savePatient(questionnaireResponse, fetchIdentifiers)
  }

  private fun observePatientSaveAction() {
    viewModel.isPatientSaved.observe(viewLifecycleOwner) {
      viewModel.isLoading.value = false
      if (!it) {
        Toast.makeText(requireContext(), getString(R.string.inputs_are_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      Toast.makeText(requireContext(), getString(R.string.patient_is_saved), Toast.LENGTH_SHORT)
        .show()
      NavHostFragment.findNavController(this).navigateUp()
    }
  }

  private fun observeQuestionnaire() {
    viewModel.embeddedQuestionnaire.observe(viewLifecycleOwner) {
      if (it.isNotEmpty()) {
        it?.let { addQuestionnaireFragment(it) }
      } else {
        Toast.makeText(
            requireContext(),
            getString(R.string.questionnaire_error_message),
            Toast.LENGTH_SHORT,
          )
          .show()
        NavHostFragment.findNavController(this).navigateUp()
      }
    }
  }

  private fun observeLoading() {
    viewModel.isLoading.observe(viewLifecycleOwner) {
      if (it) {
        binding.progressBar.visibility = View.VISIBLE
        binding.touchBlockerView.visibility = View.VISIBLE // Show the blocker
      } else {
        binding.progressBar.visibility = View.GONE
        binding.touchBlockerView.visibility = View.GONE // Hide the blocker
      }
    }
  }

  companion object {
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
