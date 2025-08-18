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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.datacapture.QuestionnaireFragment
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.GenericFormentryFragmentBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.showSnackBar
import org.openmrs.android.fhir.viewmodel.GenericFormEntryViewModel

/** A fragment class to show screener questionnaire screen. */
class GenericFormEntryFragment : Fragment(R.layout.generic_formentry_fragment) {

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val viewModel: GenericFormEntryViewModel by viewModels { viewModelSavedStateFactory }
  private val args: GenericFormEntryFragmentArgs by navArgs()

  private var _binding: GenericFormentryFragmentBinding? = null

  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = GenericFormentryFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setHasOptionsMenu(true)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    onBackPressed()
    observeResourcesSaveAction()
    if (savedInstanceState == null) {
      viewModel.getEncounterQuestionnaire(
        args.questionnaireId,
      )
    }
    observeLoading()
    observeQuestionnaire()
    childFragmentManager.setFragmentResultListener(
      QuestionnaireFragment.SUBMIT_REQUEST_KEY,
      viewLifecycleOwner,
    ) { _, _ ->
      onSubmitAction()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        showCancelScreenerQuestionnaireAlertDialog()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun addQuestionnaireFragment(questionnaireJson: String) {
    childFragmentManager.commit {
      if (questionnaireJson.isEmpty()) {
        showSnackBar(
          requireActivity(),
          getString(R.string.questionnaire_error_message),
        )
        NavHostFragment.findNavController(this@GenericFormEntryFragment).navigateUp()
      } else {
        add(
          R.id.form_entry_container,
          QuestionnaireFragment.builder()
            .showReviewPageBeforeSubmit(
              requireContext().resources.getBoolean(R.bool.show_review_page_before_submit),
            )
            .setQuestionnaire(questionnaireJson)
            .build(),
          QUESTIONNAIRE_FRAGMENT_TAG,
        )
      }
    }
  }

  private fun onSubmitAction() {
    viewModel.isLoading.value = true
    lifecycleScope.launch {
      val questionnaireFragment =
        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
      viewModel.saveEncounter(
        questionnaireFragment.getQuestionnaireResponse(),
        args.patientId,
        UUID.randomUUID().toString(),
      )
    }
  }

  private fun showCancelScreenerQuestionnaireAlertDialog() {
    val alertDialog: AlertDialog? =
      activity?.let {
        val builder = AlertDialog.Builder(it)
        builder.apply {
          setMessage(getString(R.string.cancel_questionnaire_message))
          setPositiveButton(getString(android.R.string.yes)) { _, _ ->
            NavHostFragment.findNavController(this@GenericFormEntryFragment).navigateUp()
          }
          setNegativeButton(getString(android.R.string.no)) { _, _ -> }
        }
        builder.create()
      }
    alertDialog?.show()
  }

  private fun onBackPressed() {
    //    activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
    //      showCancelScreenerQuestionnaireAlertDialog()
    //    }
  }

  private fun observeResourcesSaveAction() {
    viewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      viewModel.isLoading.value = false
      val isSaved = it.contains("SAVED")
      if (!isSaved) {
        Toast.makeText(requireContext(), getString(R.string.inputs_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      Toast.makeText(requireContext(), getString(R.string.resources_saved), Toast.LENGTH_SHORT)
        .show()

      findNavController()
        .navigate(GenericFormEntryFragmentDirections.actionGenericFormEntryFragmentToPatientList())
    }
  }

  private fun observeQuestionnaire() {
    viewModel.questionnaireJson.observe(viewLifecycleOwner) {
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
