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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.fhir.datacapture.QuestionnaireFragment
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.AddPatientFragmentBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.viewmodel.EditPatientViewModel

/** A fragment representing Edit Patient screen. This fragment is contained in a [MainActivity]. */
class EditPatientFragment : Fragment(R.layout.add_patient_fragment) {
  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val viewModel: EditPatientViewModel by viewModels { viewModelSavedStateFactory }
  private var _binding: AddPatientFragmentBinding? = null

  private val binding
    get() = _binding!!

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

    lifecycleScope.launch {
      val selectedLocation =
        context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME)

      binding.currentLocationLabel.text =
        selectedLocation ?: getString(R.string.no_location_selected)
    }
    requireArguments()
      .putString(QUESTIONNAIRE_FILE_PATH_KEY, "new-patient-registration-paginated.json")

    viewModel.livePatientData.observe(viewLifecycleOwner) { addQuestionnaireFragment(it) }
    viewModel.isPatientSaved.observe(viewLifecycleOwner) {
      if (!it) {
        Toast.makeText(requireContext(), R.string.inputs_missing, Toast.LENGTH_SHORT).show()
        return@observe
      }
      Toast.makeText(requireContext(), R.string.message_patient_updated, Toast.LENGTH_SHORT).show()
      NavHostFragment.findNavController(this).navigateUp()
    }
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
      NavHostFragment.findNavController(this@EditPatientFragment).navigateUp()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this@EditPatientFragment).navigateUp()
        true
      }
      else -> false
    }
  }

  private fun addQuestionnaireFragment(pair: Pair<String, String>) {
    lifecycleScope.launch {
      childFragmentManager.commit {
        add(
          R.id.add_patient_container,
          QuestionnaireFragment.builder()
            .setQuestionnaire(pair.first)
            .setQuestionnaireResponse(pair.second)
            .build(),
          QUESTIONNAIRE_FRAGMENT_TAG,
        )
      }
    }
  }

  private fun onSubmitAction() {
    lifecycleScope.launch {
      val questionnaireFragment =
        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
      viewModel.updatePatient(questionnaireFragment.getQuestionnaireResponse())
    }
  }

  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "edit-questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "edit-questionnaire-fragment-tag"
  }
}
