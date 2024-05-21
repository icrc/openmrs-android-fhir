
package org.openmrs.android.fhir.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.QuestionnaireFragment
import org.openmrs.android.fhir.databinding.CreateEncounterFragmentBinding
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.R

/**
 * A fragment representing a single Patient detail screen. This fragment is contained in a
 * [MainActivity].
 */
class CreateEncountersFragment : Fragment(R.layout.create_encounter_fragment) {
  private lateinit var fhirEngine: FhirEngine
  private var _binding: CreateEncounterFragmentBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = CreateEncounterFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }
  private val args: CreateEncountersFragmentArgs by navArgs()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setHasOptionsMenu(true)
    updateArguments()
    onBackPressed()
    if (savedInstanceState == null) {
      addQuestionnaireFragment()
    }
    childFragmentManager.setFragmentResultListener(
      QuestionnaireFragment.SUBMIT_REQUEST_KEY,
      viewLifecycleOwner,
    ) { _, _ ->
      onSubmitAction()
    }
    loadFormSchemas(view)
  }

  fun loadFormSchemas(view: View) {
    setupFormButton(view, R.id.assessment_form_button, "assessment.json")
    setupFormButton(view, R.id.follow_up_form_button, "screener-questionnaire.json")
    setupFormButton(view, R.id.closure_form_button, "assessment.json")
  }

  private fun setupFormButton(view: View, buttonId: Int, formFileName: String) {
    view.findViewById<Button>(buttonId).setOnClickListener {
      findNavController().navigate(
        CreateEncountersFragmentDirections.actionCreateEncounterFragmentToGenericFormEntryFragment(
          formFileName,
          args.patientId
        )
      )
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

  private fun updateArguments() {
    requireArguments().putString(QUESTIONNAIRE_FILE_PATH_KEY, "screener-questionnaire.json")
  }

  private fun addQuestionnaireFragment() {
//    childFragmentManager.commit {
//      add(
//        R.id.add_patient_container,
//        QuestionnaireFragment.builder().setQuestionnaire(viewModel.questionnaire).build(),
//        QUESTIONNAIRE_FRAGMENT_TAG,
//      )
//    }
  }

  private fun onSubmitAction() {
//    lifecycleScope.launch {
//      val questionnaireFragment =
//        childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
//      viewModel.saveScreenerEncounter(
//        questionnaireFragment.getQuestionnaireResponse(),
//        args.patientId,
//      )
//    }
  }

  private fun showCancelScreenerQuestionnaireAlertDialog() {
    val alertDialog: AlertDialog? =
      activity?.let {
        val builder = AlertDialog.Builder(it)
        builder.apply {
          setMessage(getString(R.string.cancel_questionnaire_message))
          setPositiveButton(getString(android.R.string.yes)) { _, _ ->
            NavHostFragment.findNavController(this@CreateEncountersFragment).navigateUp()
          }
          setNegativeButton(getString(android.R.string.no)) { _, _ -> }
        }
        builder.create()
      }
    alertDialog?.show()
  }

  private fun onBackPressed() {
    activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
      showCancelScreenerQuestionnaireAlertDialog()
    }
  }
  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
