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
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.GroupFormentryFragmentBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.showSnackBar
import org.openmrs.android.fhir.viewmodel.GenericFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.GroupFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class GroupFormEntryFragment : Fragment(R.layout.group_formentry_fragment) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val genericFormEntryViewModel: GenericFormEntryViewModel by viewModels {
    viewModelSavedStateFactory
  }
  private val groupFormEntryViewModel: GroupFormEntryViewModel by
    viewModels<GroupFormEntryViewModel> { viewModelFactory }
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
    groupFormEntryViewModel.getPatients(args.patientIds.toSet())
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
      handleSubmitEncounter()
    }
    (activity as MainActivity).setDrawerEnabled(false)
    binding.btnSaveAllEncounters.setOnClickListener { showSaveAllEncounterDialog() }
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
          groupFormEntryViewModel.isLoading.value = false
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
    view?.post { groupFormEntryViewModel.isLoading.value = false }
  }

  private fun handleSubmitEncounter() {
    val selectedTab = binding.patientTabLayout.selectedTabPosition
    val savedPatients = groupFormEntryViewModel.patientQuestionnaireResponseMap.keys.size
    val totalPatients = groupFormEntryViewModel.patients.value?.size ?: 0
    saveCurrentPatientQuestionnaireResponse(
      groupFormEntryViewModel.patients.value?.get(selectedTab)?.resourceId,
    )
    if (selectedTab < binding.patientTabLayout.tabCount - 1) {
      binding.patientTabLayout.selectTab(binding.patientTabLayout.getTabAt(selectedTab + 1))
      binding.patientTabLayout.setScrollPosition(selectedTab + 1, 0f, true)
    }

    if (totalPatients <= savedPatients + 1) {
      showSnackBar(
        requireActivity(),
        getString(R.string.all_patients_have_been_submitted_click_on_save_all_encounters),
      )
    }
  }

  private fun saveAllEncounters() {
    val savedEncounterPatientId =
      groupFormEntryViewModel.getAllPatientIdsFromPatientQuestionnaireResponse()
    if (savedEncounterPatientId.isEmpty()) {
      showSnackBar(requireActivity(), getString(R.string.no_encounters_to_save))
      return
    }
    groupFormEntryViewModel.isLoading.value = true
    for (patientId in savedEncounterPatientId) {
      genericFormEntryViewModel.saveEncounter(
        groupFormEntryViewModel.getPatientQuestionnaireResponse(patientId)!!,
        patientId,
      )
    }
  }

  private fun saveCurrentPatientQuestionnaireResponse(patientId: String?) {
    if (patientId != null) {
      lifecycleScope.launch {
        val questionnaireFragment =
          childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG + patientId)
            as QuestionnaireFragment
        groupFormEntryViewModel.setPatientQuestionnaireResponse(
          patientId,
          questionnaireFragment.getQuestionnaireResponse(),
        )
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
          groupFormEntryViewModel.isLoading.value = true
          val patientId = groupFormEntryViewModel.patients.value?.get(tab.position)?.resourceId
          if (patientId != null) {
            addQuestionnaireFragment(
              genericFormEntryViewModel.questionnaireJson.value.toString(),
              patientId,
            )
          } else {
            groupFormEntryViewModel.isLoading.value = false
          }
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
          // No Functionality required for now
        }

        override fun onTabReselected(tab: TabLayout.Tab) {
          // No functionality required for now
        }
      },
    )
  }

  private fun observeResourcesSaveAction() {
    genericFormEntryViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isSaved = it.contains(getString(R.string.saved))
      val patientId = it.substringAfter("/")
      if (!isSaved) {
        groupFormEntryViewModel.incrementErrorCount()
        return@observe
      }
      groupFormEntryViewModel.addSavedPatientId(patientId)
      val errorCount = groupFormEntryViewModel.errorCount.value ?: 0
      val savedPatientCount = groupFormEntryViewModel.savedPatientIds.value?.size ?: 0
      if (
        errorCount + savedPatientCount ==
          groupFormEntryViewModel.getAllPatientIdsFromPatientQuestionnaireResponse().size
      ) {
        if (errorCount == 0) {
          Toast.makeText(requireContext(), getString(R.string.resources_saved), Toast.LENGTH_SHORT)
            .show()
          NavHostFragment.findNavController(this).navigateUp()
        } else {
          handleUnsavedEncounters()
        }
      }
    }
  }

  private fun handleUnsavedEncounters() {
    val savedPatientIds = groupFormEntryViewModel.savedPatientIds.value
    savedPatientIds?.forEach { patientId ->
      val fragment = childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG + patientId)
      if (fragment != null) {
        childFragmentManager.commit { remove(fragment) }
      }
    }
    if (savedPatientIds != null) {
      groupFormEntryViewModel.removePatientsFromList(savedPatientIds)
    }
    groupFormEntryViewModel.resetSavedPatientId()
    groupFormEntryViewModel.resetErrorCount()
    binding.groupProgressBar.visibility = View.GONE
  }

  private fun observeQuestionnaire() {
    genericFormEntryViewModel.questionnaireJson.observe(viewLifecycleOwner) {
      val patientId =
        groupFormEntryViewModel.patients.value
          ?.get(binding.patientTabLayout.selectedTabPosition)
          ?.resourceId
      if (patientId != null) {
        groupFormEntryViewModel.isLoading.value = true
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
    groupFormEntryViewModel.patients.observe(viewLifecycleOwner) {
      it?.let {
        setupPatientTabs(it)
        observeQuestionnaire()
      }
    }
  }

  private fun observeLoading() {
    groupFormEntryViewModel.isLoading.observe(viewLifecycleOwner) {
      binding.groupProgressBar.visibility = if (it) View.VISIBLE else View.GONE
      binding.btnSaveAllEncounters.visibility = if (it) View.GONE else View.VISIBLE
    }
  }

  private fun showSaveAllEncounterDialog() {
    AlertDialog.Builder(requireContext())
      .setTitle(getString(R.string.save_all_submitted_encounters))
      .setMessage(getString(R.string.do_you_want_to_save_all_submitted_encounters))
      .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
        dialog.dismiss()
        saveAllEncounters()
      }
      .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
      .setCancelable(false)
      .show()
  }

  companion object {
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
