/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.fhir.datacapture.QuestionnaireFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.openmrs.android.fhir.viewmodel.AddPatientViewModel
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.AddPatientFragmentBinding

/** A fragment class to show patient registration screen. */
class AddPatientFragment : Fragment(R.layout.add_patient_fragment) {

  private val viewModel: AddPatientViewModel by viewModels()
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
    updateArguments()
    lifecycleScope.launch {
      val selectedLocation = context?.applicationContext?.dataStore?.data?.first()?.get(
        PreferenceKeys.LOCATION_NAME)
      if (selectedLocation != null) {
        binding.currentLocationLabel.text = selectedLocation
      }
      viewModel.locationId = context?.applicationContext?.dataStore?.data?.first()?.get(
        PreferenceKeys.LOCATION_ID
      )
    }
    if (savedInstanceState == null) {
      addQuestionnaireFragment()
    }
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
      title = requireContext().getString(R.string.add_patient)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun updateArguments() {
    requireArguments()
      .putString(QUESTIONNAIRE_FILE_PATH_KEY, "new-patient-registration-paginated.json")
  }

  private fun addQuestionnaireFragment() {
    childFragmentManager.commit {
      add(
          R.id.add_patient_container,
        QuestionnaireFragment.builder()
          .setQuestionnaire(viewModel.questionnaireJson)
          .setShowCancelButton(true)
          .setShowSubmitButton(true)
          .build(),
        QUESTIONNAIRE_FRAGMENT_TAG,
      )
    }
  }

  private fun onSubmitAction() {
    lifecycleScope.launch {
      val questionnaireFragment =
        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
      savePatient(questionnaireFragment.getQuestionnaireResponse())
    }
  }

  private fun savePatient(questionnaireResponse: QuestionnaireResponse) {
    viewModel.savePatient(questionnaireResponse)
  }

  private fun observePatientSaveAction() {
    viewModel.isPatientSaved.observe(viewLifecycleOwner) {
      if (!it) {
        Toast.makeText(requireContext(), "Inputs are missing.", Toast.LENGTH_SHORT).show()
        return@observe
      }
      Toast.makeText(requireContext(), "Patient is saved.", Toast.LENGTH_SHORT).show()
      NavHostFragment.findNavController(this).navigateUp()
    }
  }

  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
