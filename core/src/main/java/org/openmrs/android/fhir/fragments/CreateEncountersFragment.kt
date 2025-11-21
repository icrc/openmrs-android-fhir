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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.CreateFormSectionAdapter
import org.openmrs.android.fhir.data.GroupSessionDraftRepository
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.FormSectionItem
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft
import org.openmrs.android.fhir.databinding.CreateEncounterFragmentBinding
import org.openmrs.android.fhir.viewmodel.CreateEncounterViewModel

class CreateEncountersFragment : Fragment(R.layout.create_encounter_fragment) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var groupSessionDraftRepository: GroupSessionDraftRepository
  private val viewModel by viewModels<CreateEncounterViewModel> { viewModelFactory }
  private lateinit var recyclerView: RecyclerView
  private lateinit var progressBar: ProgressBar
  private var draftResumeChecked = false

  private val args: CreateEncountersFragmentArgs by navArgs()

  private var _binding: CreateEncounterFragmentBinding? = null

  private val binding
    get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    _binding = CreateEncounterFragmentBinding.bind(view)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title =
        if (args.isGroupEncounter) {
          requireContext().getString(R.string.create_group_encounter)
        } else {
          requireContext().getString(R.string.create_encounter)
        }
      setDisplayHomeAsUpEnabled(true)
    }
    (requireActivity().application as FhirApplication).appComponent.inject(this)

    viewModel.loadFormData(
      getString(R.string.questionnaires),
      getString(R.string.encounter_type_system_url),
    )
    // Initialize views
    recyclerView = binding.rvFormSections
    progressBar = binding.progressBar

    // Setup RecyclerView
    recyclerView.layoutManager = LinearLayoutManager(requireContext())

    // Observe data
    setupObservers()
  }

  private fun setupObservers() {
    viewModel.formData.observe(viewLifecycleOwner) { formData ->
      formData?.let {
        setupAdapter(it)
        maybePromptToResumeDraft(it)
      }
    }

    viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
      progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
      errorMessage?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
    }
  }

  private fun setupAdapter(formSectionItems: List<FormSectionItem>) {
    val adapter =
      CreateFormSectionAdapter(formSectionItems) { formItem -> handleFormClick(formItem) }
    recyclerView.adapter = adapter
  }

  private fun maybePromptToResumeDraft(formSectionItems: List<FormSectionItem>) {
    if (!args.isGroupEncounter || draftResumeChecked) return
    draftResumeChecked = true

    val formItems = formSectionItems.flatMap { section -> section.forms }

    viewLifecycleOwner.lifecycleScope.launch {
      var latestDraft: Pair<FormItem, GroupSessionDraft>? = null

      formItems.forEach { formItem ->
        val draft = groupSessionDraftRepository.getDraft(formItem.questionnaireId)
        when {
          draft == null -> Unit
          draft.hasContent() -> {
            val currentLatest = latestDraft
            if (currentLatest == null || draft.lastUpdated > currentLatest.second.lastUpdated) {
              latestDraft = formItem to draft
            }
          }
          else -> groupSessionDraftRepository.deleteDraft(formItem.questionnaireId)
        }
      }

      val draftEntry =
        latestDraft?.let { (formItem, draft) -> formItem to draft.patientIds.toTypedArray() }

      if (draftEntry != null && isAdded) {
        val (formItem, patientIds) = draftEntry
        showResumeDraftDialog(formItem, patientIds)
      }
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

  private fun handleFormClick(formItem: FormItem) {
    val questionnaireId = formItem.questionnaireId
    if (args.isGroupEncounter) {
      viewLifecycleOwner.lifecycleScope.launch {
        val draft = groupSessionDraftRepository.getDraft(questionnaireId)
        if (draft != null) {
          if (draft.hasContent()) {
            showResumeDraftDialog(formItem, draft.patientIds.toTypedArray())
          } else {
            groupSessionDraftRepository.deleteDraft(questionnaireId)
            navigateToPatientSelection(questionnaireId)
          }
        } else {
          navigateToPatientSelection(questionnaireId)
        }
      }
    } else {
      findNavController()
        .navigate(
          CreateEncountersFragmentDirections
            .actionCreateEncounterFragmentToGenericFormEntryFragment(
              patientId = args.patientId
                  ?: "", // Ensure patientId is not null or handle appropriately
              questionnaireId = questionnaireId,
            ),
        )
    }
  }

  private fun GroupSessionDraft.hasContent(): Boolean {
    return screenerCompleted || patientResponses.isNotEmpty() || !screenerResponse.isNullOrBlank()
  }

  private fun navigateToPatientSelection(questionnaireId: String) {
    val action =
      CreateEncountersFragmentDirections
        .actionCreateEncounterFragmentToPatientSelectionDialogFragment(questionnaireId)
    findNavController().navigate(action)
  }

  private fun navigateToGroupFormEntry(questionnaireId: String, patientIds: Array<String>) {
    val action =
      CreateEncountersFragmentDirections.actionCreateEncounterFragmentToGroupFormEntryFragment(
        questionnaireId = questionnaireId,
        patientIds = patientIds,
        resumeDraft = true,
      )
    findNavController().navigate(action)
  }

  private fun showResumeDraftDialog(formItem: FormItem, patientIds: Array<String>) {
    AlertDialog.Builder(requireContext())
      .setTitle(getString(R.string.resume_group_session))
      .setMessage(
        getString(
          R.string.resume_group_session_message_with_name,
          formItem.name,
        ),
      )
      .setPositiveButton(getString(R.string.resume_session)) { _, _ ->
        navigateToGroupFormEntry(formItem.questionnaireId, patientIds)
      }
      .setNegativeButton(getString(R.string.cancel), null)
      .create()
      .show()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
