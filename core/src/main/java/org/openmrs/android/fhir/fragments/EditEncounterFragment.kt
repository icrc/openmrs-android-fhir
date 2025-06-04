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
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.QuestionnaireFragment
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModel

/**
 * A fragment representing Edit Encounter screen. This fragment is contained in a [MainActivity].
 */
class EditEncounterFragment : Fragment(R.layout.generic_formentry_fragment) {
  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory

  @Inject lateinit var fhirEngine: FhirEngine

  private val viewModel: EditEncounterViewModel by viewModels { viewModelSavedStateFactory }

  private lateinit var encounterType: String
  private lateinit var encounterId: String

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)

    // Retrieve the encounter_id from arguments
    encounterId =
      requireArguments().getString("encounter_id")
        ?: throw IllegalArgumentException("Encounter ID is required")

    encounterType =
      requireArguments().getString("encounter_type")
        ?: throw IllegalArgumentException("Encounter type is required")
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.edit_encounter)
    }
    viewModel.prepareEditEncounter(encounterId, encounterType)
    viewModel.encounterDataPair.observe(viewLifecycleOwner) {
      if (it.first.isBlank()) {
        Toast.makeText(
            requireContext(),
            getString(R.string.encounter_questionnaire_not_found_try_again_later),
            Toast.LENGTH_SHORT,
          )
          .show()
        NavHostFragment.findNavController(this).navigateUp()
        return@observe
      }
      addQuestionnaireFragment(it)
    }
    viewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isSaved = it == "SAVED"
      if (!isSaved) {
        Toast.makeText(requireContext(), R.string.inputs_missing, Toast.LENGTH_SHORT).show()
        return@observe
      }
      Toast.makeText(requireContext(), R.string.message_encounter_updated, Toast.LENGTH_SHORT)
        .show()
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
      NavHostFragment.findNavController(this@EditEncounterFragment).navigateUp()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this@EditEncounterFragment).navigateUp()
        true
      }
      else -> false
    }
  }

  private fun addQuestionnaireFragment(pair: Pair<String, String>) {
    if (pair.first.isNotBlank()) {
      lifecycleScope.launch {
        val questionnaireJson = pair.first

        childFragmentManager.commit {
          add(
            R.id.form_entry_container,
            QuestionnaireFragment.builder()
              .setQuestionnaire(questionnaireJson)
              .setQuestionnaireResponse(pair.second)
              .showReviewPageBeforeSubmit(
                requireContext().resources.getBoolean(R.bool.show_review_page_before_submit),
              )
              .build(),
            QUESTIONNAIRE_FRAGMENT_TAG,
          )
        }
      }
    }
  }

  private fun onSubmitAction() {
    lifecycleScope.launch {
      val questionnaireFragment =
        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
      viewModel.updateEncounter(
        questionnaireFragment.getQuestionnaireResponse(),
        encounterId,
        encounterType,
      )
    }
  }

  companion object {
    const val QUESTIONNAIRE_FRAGMENT_TAG = "edit-questionnaire-fragment-tag"
  }
}
