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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.QuestionnaireFragment
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.keepBelowAppBar
import org.openmrs.android.fhir.forms.addQuestionnaireWhenDrawn
import org.openmrs.android.fhir.forms.newQuestionnaireFragment
import org.openmrs.android.fhir.forms.onQuestionnaireResult
import org.openmrs.android.fhir.forms.setFormLoading
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

/**
 * A fragment representing Edit Encounter screen. This fragment is contained in a [MainActivity].
 */
class EditEncounterFragment : Fragment(R.layout.generic_formentry_fragment) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory

  @Inject lateinit var fhirEngine: FhirEngine

  private val viewModel: EditEncounterViewModel by viewModels { viewModelSavedStateFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private lateinit var encounterType: String
  private lateinit var encounterId: String

  /** The answers are only shown; the Edit action turns this off and the form becomes editable. */
  private var readOnly = false

  /** The encounter's form is not on the device: what was saved is shown, and cannot be edited. */
  private val genericForm
    get() = viewModel.isGenericForm

  private val viewing
    get() = readOnly || genericForm

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
    readOnly =
      savedInstanceState?.getBoolean(STATE_READ_ONLY)
        ?: requireArguments().getBoolean(ARG_READ_ONLY, false)

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
    view.keepBelowAppBar(viewLifecycleOwner)
    updateActionBar()
    setFormLoading(true)
    viewModel.prepareEditEncounter(encounterId, encounterType)
    viewModel.encounterDataPair.observe(viewLifecycleOwner) {
      if (it.first.isBlank()) {
        AppMessage.show(
          requireContext(),
          getString(R.string.encounter_questionnaire_not_found_try_again_later),
          MessageKind.Error,
        )
        NavHostFragment.findNavController(this).navigateUp()
        return@observe
      }
      if (genericForm) {
        updateActionBar()
        requireActivity().invalidateOptionsMenu()
      }
      addQuestionnaireFragment(it)
    }
    viewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isSaved = it == "SAVED"
      if (!isSaved) {
        AppMessage.show(requireContext(), R.string.inputs_missing, MessageKind.Error)
        return@observe
      }
      AppMessage.show(requireContext(), R.string.message_encounter_updated, MessageKind.Success)
      NavHostFragment.findNavController(this).navigateUp()
    }

    mainActivityViewModel.setDrawerEnabled(false)

    /** Use the provided cancel|submit buttons from the sdc library */
    onQuestionnaireResult(
      onSubmit = ::onSubmitAction,
      onCancel = { NavHostFragment.findNavController(this@EditEncounterFragment).navigateUp() },
    )
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_READ_ONLY, readOnly)
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    if (!readOnly || genericForm) return
    inflater.inflate(R.menu.view_encounter_menu, menu)
    // A button as the action: the bar does not route taps on a custom action view to the menu.
    menu
      .findItem(R.id.menu_encounter_edit)
      ?.actionView
      ?.findViewById<View>(R.id.encounter_edit_button)
      ?.setOnClickListener { startEditing() }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this@EditEncounterFragment).navigateUp()
        true
      }
      R.id.menu_encounter_edit -> {
        startEditing()
        true
      }
      else -> false
    }
  }

  private fun updateActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = getString(if (viewing) R.string.view_encounter else R.string.edit_encounter)
      subtitle = if (viewing) getString(R.string.encounter_read_only) else null
    }
  }

  /** From viewing to editing: the same answers, now in a form that can be changed and saved. */
  private fun startEditing() {
    val pair = viewModel.encounterDataPair.value ?: return
    readOnly = false
    childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG)?.let {
      childFragmentManager.commitNow { remove(it) }
    }
    updateActionBar()
    requireActivity().invalidateOptionsMenu()
    addQuestionnaireFragment(pair)
  }

  private fun addQuestionnaireFragment(pair: Pair<String, String>) {
    if (pair.first.isNotBlank()) {
      val viewing = viewing
      addQuestionnaireWhenDrawn(
        containerId = R.id.form_entry_container,
        tag = QUESTIONNAIRE_FRAGMENT_TAG,
        create = {
          newQuestionnaireFragment(
            pair.first,
            responseJson = pair.second,
            showCancelButton = if (viewing) false else null,
            showSubmitButton = if (viewing) false else null,
            readOnly = viewing,
          )
        },
      )
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

    /** Navigation argument: open the encounter to look at it, editing only on request. */
    const val ARG_READ_ONLY = "read_only"
    private const val STATE_READ_ONLY = "read_only"
  }
}
