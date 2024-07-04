package org.openmrs.android.fhir.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.openmrs.android.fhir.Form
import org.openmrs.android.fhir.MockConstants.MOCK_FORMS
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.CreateEncounterFragmentBinding

class CreateEncountersFragment : Fragment(R.layout.create_encounter_fragment) {
  private var _binding: CreateEncounterFragmentBinding? = null
  private val binding
    get() = _binding!!

  private val args: CreateEncountersFragmentArgs by navArgs()

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

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setHasOptionsMenu(true)
    updateArguments()
    onBackPressed()
    loadFormSchemas(view)
  }

  fun loadFormSchemas(view: View) {
    val buttonContainer = view.findViewById<LinearLayout>(R.id.button_container)
    for (form in MOCK_FORMS) {
      setupFormButton(buttonContainer, form)
    }
  }

  private fun setupFormButton(parentView: ViewGroup, form: Form) {
    val button = Button(context).apply {
      text = form.display
      setOnClickListener {
        findNavController().navigate(
          CreateEncountersFragmentDirections.actionCreateEncounterFragmentToGenericFormEntryFragment(
            form.resource,
            form.display,
            form.code,
            args.patientId
          )
        )
      }
    }
    parentView.addView(button)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this@CreateEncountersFragment).navigateUp()
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
    requireArguments().putString(QUESTIONNAIRE_FILE_PATH_KEY, "assessment.json")
  }

  private fun onBackPressed() {}
  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
