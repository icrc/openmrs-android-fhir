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
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.fhir.datacapture.QuestionnaireFragment
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModel
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModelFactory

/** A fragment representing Edit Encounter screen. This fragment is contained in a [MainActivity]. */
class EditEncounterFragment : Fragment(R.layout.generic_formentry_fragment) {
  private lateinit var viewModel: EditEncounterViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)

    // Retrieve the encounter_id from arguments
    val encounterId = requireArguments().getString("encounter_id")
      ?: throw IllegalArgumentException("Encounter ID is required")

    val formResource = requireArguments().getString("form_resource")
      ?: throw IllegalArgumentException("Encounter ID is required")

    // Initialize the ViewModel using a factory
    val viewModelFactory = EditEncounterViewModelFactory(requireActivity().application, formResource, encounterId)
    viewModel = viewModels<EditEncounterViewModel> { viewModelFactory }.value
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.edit_encounter)
    }

    viewModel.liveEncounterData.observe(viewLifecycleOwner) { addQuestionnaireFragment(it) }
    viewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      if (!it) {
        Toast.makeText(requireContext(), R.string.inputs_missing, Toast.LENGTH_SHORT).show()
        return@observe
      }
      Toast.makeText(requireContext(), R.string.message_encounter_updated, Toast.LENGTH_SHORT).show()
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
    lifecycleScope.launch {
      childFragmentManager.commit {
        add(
          R.id.form_entry_container,
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
      viewModel.updateEncounter(
        questionnaireFragment.getQuestionnaireResponse())
    }
  }


  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "edit-questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "edit-questionnaire-fragment-tag"
  }
}
