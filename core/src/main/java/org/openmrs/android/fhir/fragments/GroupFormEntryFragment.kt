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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.material.tabs.TabLayout
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.GroupFormentryFragmentBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.showSnackBar
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModel
import org.openmrs.android.fhir.viewmodel.GenericFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.GroupFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class GroupFormEntryFragment : Fragment(R.layout.group_formentry_fragment) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val genericFormEntryViewModel: GenericFormEntryViewModel by viewModels {
    viewModelSavedStateFactory
  }
  private val viewModel: GroupFormEntryViewModel by
    viewModels<GroupFormEntryViewModel> { viewModelFactory }

  private val editEncounterViewModel: EditEncounterViewModel by viewModels {
    viewModelSavedStateFactory
  }

  private val args: GroupFormEntryFragmentArgs by navArgs()

  private var _binding: GroupFormentryFragmentBinding? = null

  private val binding
    get() = _binding!!

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    _binding = GroupFormentryFragmentBinding.bind(view)
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setHasOptionsMenu(true)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    observeLoading()
    viewModel.getPatients(args.patientIds.toSet())
    observeResourcesSaveAction()
    if (savedInstanceState == null) {
      genericFormEntryViewModel.getEncounterQuestionnaire(
        args.questionnaireId,
      )
    }
    childFragmentManager.setFragmentResultListener(
      QuestionnaireFragment.SUBMIT_REQUEST_KEY,
      viewLifecycleOwner,
    ) { _, _ ->
      val selectedTab = binding.patientTabLayout.selectedTabPosition
      if (selectedTab < binding.patientTabLayout.tabCount - 1) {
        binding.patientTabLayout.selectTab(binding.patientTabLayout.getTabAt(selectedTab + 1))
        binding.patientTabLayout.setScrollPosition(selectedTab + 1, 0f, true)
      } else {
        handleSubmitEncounter(selectedTab)
      }
    }
    (activity as MainActivity).setDrawerEnabled(false)
    observePatients()
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
      title = getString(R.string.group_encounters)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun addQuestionnaireFragment(questionnaireJson: String, patientId: String) {
    childFragmentManager.fragments.forEach { childFragmentManager.commit { hide(it) } }

    val questionnaireFragment =
      childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG + patientId)
    if (questionnaireFragment != null) {
      childFragmentManager.commit { show(questionnaireFragment) }
    } else {
      childFragmentManager.commit {
        if (questionnaireJson.isEmpty()) {
          showSnackBar(
            requireActivity(),
            getString(R.string.questionnaire_error_message),
          )
          NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
          viewModel.isLoading.value = false
          return@commit
        } else {
          val questionnaireFragmentBuilder =
            QuestionnaireFragment.builder()
              .showReviewPageBeforeSubmit(
                requireContext().resources.getBoolean(R.bool.show_review_page_before_submit),
              )
              .setSubmitButtonText(getString(R.string.submit))
              .setShowSubmitButton(true)
              .setQuestionnaire(questionnaireJson)

          add(
            R.id.form_entry_container,
            questionnaireFragmentBuilder.build(),
            QUESTIONNAIRE_FRAGMENT_TAG + patientId,
          )
        }
      }
    }
    view?.post { viewModel.isLoading.value = false }
  }

  private fun handleSubmitEncounter(selectedTab: Int) {
    val patientId = viewModel.patients.value?.get(selectedTab)?.resourceId

    if (patientId != null) {
      viewModel.isLoading.value = true
      lifecycleScope.launch {
        try {
          val questionnaireFragment =
            childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG + patientId)
              as QuestionnaireFragment
          val questionnaireResponse = questionnaireFragment.getQuestionnaireResponse()

          var encounterId = viewModel.getEncounterIdForPatientId(patientId)
          if (encounterId != null) {
            editEncounterViewModel.updateEncounter(
              questionnaireResponse,
              encounterId,
            )
          } else {
            encounterId = UUID.randomUUID().toString()
            genericFormEntryViewModel.saveEncounter(
              questionnaireResponse,
              patientId,
              encounterId,
            )
            viewModel.setPatientIdToEncounterIdMap(patientId, encounterId)
          }
        } catch (exception: Exception) {
          print(exception)
          viewModel.isLoading.value = false
          return@launch
        }
      }
    }
  }

  private fun showCancelScreenerQuestionnaireAlertDialog() {
    val alertDialog: AlertDialog? =
      activity?.let {
        val builder = AlertDialog.Builder(it)
        builder.apply {
          setMessage(getString(R.string.cancel_questionnaire_message))
          setPositiveButton(getString(android.R.string.yes)) { _, _ ->
            NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
          }
          setNegativeButton(getString(android.R.string.no)) { _, _ -> }
        }
        builder.create()
      }
    alertDialog?.show()
  }

  private fun setupPatientTabs(patients: List<PatientListViewModel.PatientItem>) {
    binding.patientTabLayout.clearOnTabSelectedListeners() // Clear previous listeners if any
    binding.patientTabLayout.removeAllTabs() // Clear existing tabs before adding new ones

    for (patient in patients) {
      binding.patientTabLayout.addTab(binding.patientTabLayout.newTab().setText(patient.name))
    }

    // Set up tab selection listener
    binding.patientTabLayout.addOnTabSelectedListener(
      object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
          viewModel.isLoading.value = true
          val patientId = viewModel.patients.value?.get(tab.position)?.resourceId
          if (patientId != null) {
            addQuestionnaireFragment(
              genericFormEntryViewModel.questionnaireJson.value.toString(),
              patientId,
            )
          } else {
            viewModel.isLoading.value = false
          }
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
          val selectedTab = tab.position
          handleSubmitEncounter(selectedTab)
        }

        override fun onTabReselected(tab: TabLayout.Tab) {
          // No functionality required for now
        }
      },
    )
  }

  private fun observeResourcesSaveAction() {
    genericFormEntryViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isSaved = it.contains("SAVED")
      if (!isSaved) {
        Toast.makeText(requireContext(), getString(R.string.inputs_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      val savedPatients = viewModel.getPatientIdToEncounterIdMap().keys.size
      val totalPatients = viewModel.patients.value?.size ?: 0

      if (totalPatients <= savedPatients) {
        showSavedAllExitDialog()
      }

      val patientId = it.split("/")[1]
      val patientName = viewModel.getPatientName(patientId)
      Toast.makeText(requireContext(), "Encounter for $patientName saved", Toast.LENGTH_SHORT)
        .show()
    }
    editEncounterViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isSaved = it
      if (!isSaved) {
        Toast.makeText(requireContext(), getString(R.string.inputs_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      Toast.makeText(requireContext(), "Encounter updated!", Toast.LENGTH_SHORT).show()
    }
  }

  private fun observeQuestionnaire() {
    genericFormEntryViewModel.questionnaireJson.observe(viewLifecycleOwner) {
      val patientId =
        viewModel.patients.value?.get(binding.patientTabLayout.selectedTabPosition)?.resourceId
      if (patientId != null) {
        viewModel.isLoading.value = true
        it?.let {
          addQuestionnaireFragment(
            questionnaireJson = it,
            patientId = patientId,
          )
        }
      }
    }
  }

  private fun observePatients() {
    viewModel.patients.observe(viewLifecycleOwner) {
      it?.let {
        setupPatientTabs(it)
        observeQuestionnaire()
      }
    }
  }

  private fun observeLoading() {
    viewModel.isLoading.observe(viewLifecycleOwner) {
      binding.groupProgressBar.visibility = if (it) View.VISIBLE else View.GONE
    }
  }

  private fun showSavedAllExitDialog() {
    AlertDialog.Builder(requireContext())
      .setTitle(getString(R.string.all_encounter_for_group_session_have_been_saved))
      .setMessage(getString(R.string.do_you_want_to_exit))
      .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
        dialog.dismiss()
        NavHostFragment.findNavController(this).navigateUp()
      }
      .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
      .setCancelable(false)
      .show()
  }

  companion object {
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
