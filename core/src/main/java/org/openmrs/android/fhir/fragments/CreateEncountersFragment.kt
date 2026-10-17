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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import javax.inject.Inject
import kotlin.getValue
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.GroupSessionDraftRepository
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.FormSectionItem
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.CreateEncounterScreen
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.CreateEncounterViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel

class CreateEncountersFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var groupSessionDraftRepository: GroupSessionDraftRepository
  private val viewModel by viewModels<CreateEncounterViewModel> { viewModelFactory }
  private var draftResumeChecked = false

  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private val args: CreateEncountersFragmentArgs by navArgs()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View = composeView {
    OpenMrsTheme {
      val sections by viewModel.formData.observeAsState(null)
      val isLoading by viewModel.isLoading.observeAsState(false)
      val pending by viewModel.pendingSessions.observeAsState(emptyMap())
      CreateEncounterScreen(
        sections = sections,
        isLoading = isLoading,
        pendingSessions = pending,
        onFormClick = ::handleFormClick,
      )
    }
  }

  override fun onResume() {
    super.onResume()
    // A session may have been started, resumed or finished since this list was last in front.
    if (args.isGroupEncounter) viewModel.loadPendingSessions()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
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

    viewModel.loadFormData()

    viewModel.formData.observe(viewLifecycleOwner) { formData ->
      formData?.let { maybePromptToResumeDraft(it) }
    }
    viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
      errorMessage?.let { AppMessage.show(requireContext(), it, MessageKind.Error, long = true) }
    }
    mainActivityViewModel.setDrawerEnabled(false)
  }

  private fun maybePromptToResumeDraft(formSectionItems: List<FormSectionItem>) {
    if (!args.isGroupEncounter || draftResumeChecked) return
    draftResumeChecked = true

    val formItems = formSectionItems.flatMap { section -> section.forms }

    viewLifecycleOwner.lifecycleScope.launch {
      groupSessionDraftRepository.deleteEmptyDrafts()
      // The most recently touched pending session is offered first; the summaries are light.
      val latest =
        groupSessionDraftRepository.pendingSessions().values.maxByOrNull { it.lastUpdated }
      val formItem =
        latest?.let { draft ->
          formItems.firstOrNull { it.questionnaireId == draft.questionnaireId }
        }
      if (latest != null && formItem != null && isAdded) {
        showResumeDraftDialog(formItem, latest.patientIds.toTypedArray(), true)
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

  private fun showResumeDraftDialog(
    formItem: FormItem,
    patientIds: Array<String>,
    showCancel: Boolean = false,
  ) {
    val dialog =
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

    if (showCancel) {
      dialog.setNegativeButton(getString(R.string.cancel), null)
    } else {
      dialog.setNegativeButton(getString(R.string.start_new_session)) { _, _ ->
        viewLifecycleOwner.lifecycleScope.launch {
          groupSessionDraftRepository.deleteDraft(formItem.questionnaireId)
          navigateToPatientSelection(formItem.questionnaireId)
        }
      }
    }

    dialog.create().show()
  }
}
