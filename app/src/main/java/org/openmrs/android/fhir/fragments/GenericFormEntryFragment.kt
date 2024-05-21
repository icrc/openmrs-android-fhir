package org.openmrs.android.fhir.fragments

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.datacapture.QuestionnaireFragment
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.Form
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.viewmodel.GenericFormEntryViewModel

/** A fragment class to show screener questionnaire screen. */
class GenericFormEntryFragment : Fragment(R.layout.generic_formentry_fragment) {

  private val viewModel: GenericFormEntryViewModel by viewModels()
  private val args: GenericFormEntryFragmentArgs by navArgs()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setHasOptionsMenu(true)
    updateArguments(args.formResource)
    onBackPressed()
    observeResourcesSaveAction()
    if (savedInstanceState == null) {
      addQuestionnaireFragment()
    }
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

  private fun updateArguments(formResource: String) {
    requireArguments().putString(QUESTIONNAIRE_FILE_PATH_KEY, formResource)
//    requireArguments().putString(QUESTIONNAIRE_FILE_PATH_KEY, "screener-questionnaire.json")
  }

  private fun addQuestionnaireFragment() {
    childFragmentManager.commit {
      add(
        R.id.form_entry_container,
        QuestionnaireFragment.builder().setQuestionnaire(viewModel.questionnaire).build(),
        QUESTIONNAIRE_FRAGMENT_TAG,
      )
    }
  }

  private fun onSubmitAction() {
    lifecycleScope.launch {
      val questionnaireFragment =
        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
      viewModel.saveEncounter(
        questionnaireFragment.getQuestionnaireResponse(),
        Form(args.formDisplay, args.formCode), args.patientId
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
      if (!it) {
        Toast.makeText(requireContext(), getString(R.string.inputs_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      Toast.makeText(requireContext(), getString(R.string.resources_saved), Toast.LENGTH_SHORT)
        .show()
      NavHostFragment.findNavController(this).navigateUp()
    }
  }

  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
