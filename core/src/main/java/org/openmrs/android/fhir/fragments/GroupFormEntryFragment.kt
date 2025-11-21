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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.material.tabs.TabLayout
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft
import org.openmrs.android.fhir.databinding.GroupFormentryFragmentBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.showSnackBar
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModel
import org.openmrs.android.fhir.viewmodel.GenericFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.GroupFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import timber.log.Timber

class GroupFormEntryFragment : Fragment(R.layout.group_formentry_fragment) {
  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val genericFormEntryViewModel: GenericFormEntryViewModel by viewModels {
    viewModelSavedStateFactory
  }
  private val viewModel: GroupFormEntryViewModel by
    viewModels<GroupFormEntryViewModel> { viewModelSavedStateFactory }

  private val editEncounterViewModel: EditEncounterViewModel by viewModels {
    viewModelSavedStateFactory
  }

  private val args: GroupFormEntryFragmentArgs by navArgs()

  private var _binding: GroupFormentryFragmentBinding? = null

  private val binding
    get() = _binding!!

  // Keep track of current questionnaire fragment
  private var currentQuestionnaireFragment: QuestionnaireFragment? = null
  private var currentPatientId: String? = null
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  // Track the currently selected tab and prevent recursive tab changes
  private var currentSelectedTabPosition = 0
  private var isTabChanging = false
  private var pendingSavePatientId: String? = null
  private var pendingSaveEncounterId: String? = null
  private var draftToRestore: GroupSessionDraft? = null
  private var shouldRestoreDraft = false
  private val patientIds by lazy { args.patientIds.toList() }
  private var hasStartedPatientForms = false
  private var hasActiveDraft = false
  private var skipDraftSaveOnDestroy = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    _binding = GroupFormentryFragmentBinding.bind(view)
    super.onViewCreated(view, savedInstanceState)
    setHasOptionsMenu(true)
    setUpActionBar()
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    observeLoading()
    binding.patientTabLayout.visibility = View.GONE
    viewModel.getPatients(args.patientIds.toSet())
    observeResourcesSaveAction()
    lifecycleScope.launch {
      val shouldShowResumeDialog = !viewModel.hasActiveSession && !args.resumeDraft
      loadDraftIfAvailable(showResumeDialog = shouldShowResumeDialog)
    }
    if (savedInstanceState == null) {
      genericFormEntryViewModel.getEncounterQuestionnaire(
        args.questionnaireId,
      )
    }
    genericFormEntryViewModel.questionnaire.observe(viewLifecycleOwner) {
      it?.let { questionnaire ->
        viewModel.prepareScreenerQuestionnaire(questionnaire)
        restoreDraftNowIfReady()
        if (viewModel.isScreenerCompleted) {
          startPatientQuestionnaires()
        }
      }
    }
    viewModel.screenerQuestionnaireJson.observe(viewLifecycleOwner) {
      if (!viewModel.isScreenerCompleted) {
        it?.let { addScreenerQuestionnaireFragment(it) }
      }
    }
    viewModel.encounterQuestionnaireJson.observe(viewLifecycleOwner) {
      it?.let { json ->
        val q = parser.parseResource(Questionnaire::class.java, json)
        genericFormEntryViewModel.updateQuestionnaire(q)
        if (viewModel.isScreenerCompleted && hasStartedPatientForms) {
          loadQuestionnaireForTab(currentSelectedTabPosition)
        }
      }
    }
    childFragmentManager.setFragmentResultListener(
      QuestionnaireFragment.SUBMIT_REQUEST_KEY,
      viewLifecycleOwner,
    ) { _, _ ->
      if (!viewModel.isScreenerCompleted) {
        lifecycleScope.launch {
          val screenerFragment = currentQuestionnaireFragment as? QuestionnaireFragment
          val response = safeQuestionnaireResponse(screenerFragment) ?: return@launch
          viewModel.plugAnswersToEncounter(response)
          viewModel.setSessionDate(response)
          saveDraft()
          startPatientQuestionnaires()
        }
      } else {
        val selectedTab = binding.patientTabLayout.selectedTabPosition
        val patientId = viewModel.patients.value?.get(selectedTab)?.resourceId
        lifecycleScope.launch {
          cacheCurrentPatientResponse(patientId)
          patientId?.let { viewModel.submittedPatientIds.add(it) }
          if (selectedTab < binding.patientTabLayout.tabCount - 1) {
            binding.patientTabLayout.selectTab(binding.patientTabLayout.getTabAt(selectedTab + 1))
            binding.patientTabLayout.setScrollPosition(selectedTab + 1, 0f, true)
          } else {
            submitAllEncounters()
          }
        }
      }
    }
    addBackPressedListener()
    (activity as MainActivity).setDrawerEnabled(false)
    observePatients()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        onBackPressed()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun addBackPressedListener() {
    requireActivity()
      .onBackPressedDispatcher
      .addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            onBackPressed()
          }
        },
      )
  }

  private fun onBackPressed() {
    if (viewModel.hasDraftData() || currentQuestionnaireFragment != null) {
      showCancelScreenerQuestionnaireAlertDialog()
    } else {
      NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
    }
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = getString(R.string.group_encounters)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun addScreenerQuestionnaireFragment(questionnaireJson: String) {
    val fragmentBuilder =
      QuestionnaireFragment.builder()
        .showReviewPageBeforeSubmit(
          requireContext().resources.getBoolean(R.bool.show_review_page_before_submit),
        )
        .setSubmitButtonText(getString(R.string.submit))
        .setShowSubmitButton(true)
        .showOptionalText(true)
        .showRequiredText(false)
        .setQuestionnaire(questionnaireJson)

    viewModel.screenerResponseJson?.let { fragmentBuilder.setQuestionnaireResponse(it) }

    val fragment = fragmentBuilder.build()
    childFragmentManager.commit {
      replace(R.id.form_entry_container, fragment, SCREENER_FRAGMENT_TAG)
    }
    currentQuestionnaireFragment = fragment
  }

  private fun addQuestionnaireFragment(questionnaireJson: String, patientId: String) {
    if (questionnaireJson.isEmpty()) {
      showSnackBar(
        requireActivity(),
        getString(R.string.questionnaire_error_message),
      )
      NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
      viewModel.isLoading.value = false
      return
    }

    val questionnaireFragmentBuilder =
      QuestionnaireFragment.builder()
        .showReviewPageBeforeSubmit(
          requireContext().resources.getBoolean(R.bool.show_review_page_before_submit),
        )
        .setSubmitButtonText(getString(R.string.submit))
        .setShowSubmitButton(true)
        .showOptionalText(true)
        .showRequiredText(false)
        .setQuestionnaire(questionnaireJson)

    // Restore previous response if exists
    viewModel.patientResponses[patientId]?.let { savedResponse ->
      questionnaireFragmentBuilder.setQuestionnaireResponse(savedResponse)
    }
    val newFragment = questionnaireFragmentBuilder.build()
    childFragmentManager.commit {
      replace(
        R.id.form_entry_container,
        newFragment,
        QUESTIONNAIRE_FRAGMENT_TAG + patientId,
      )
    }

    currentQuestionnaireFragment = newFragment
    currentPatientId = patientId

    view?.post { viewModel.isLoading.value = false }
  }

  private fun startPatientQuestionnaires() {
    if (hasStartedPatientForms) return
    if (viewModel.patients.value.isNullOrEmpty()) return
    hasStartedPatientForms = true
    binding.patientTabLayout.visibility = View.VISIBLE
    observeQuestionnaire()
    loadQuestionnaireForTab(currentSelectedTabPosition)
  }

  private suspend fun cacheCurrentPatientResponse(
    patientId: String? = currentPatientId,
    questionnaireResponse: QuestionnaireResponse? = null,
    persist: Boolean = true,
  ) {
    val response =
      questionnaireResponse
        ?: safeQuestionnaireResponse(currentQuestionnaireFragment as? QuestionnaireFragment)

    if (patientId.isNullOrBlank() || response == null) return

    try {
      viewModel.patientResponses[patientId] = parser.encodeResourceToString(response)
      if (persist) {
        viewModel.saveDraft(args.questionnaireId, patientIds)
      }
    } catch (exception: Exception) {
      Timber.e(exception.localizedMessage)
    }
  }

  private suspend fun cacheScreenerDraft(
    questionnaireResponse: QuestionnaireResponse? = null,
    persist: Boolean = true,
  ) {
    val response =
      questionnaireResponse
        ?: safeQuestionnaireResponse(currentQuestionnaireFragment as? QuestionnaireFragment)
    response?.let {
      viewModel.cacheScreenerDraft(it)
      viewModel.setSessionDate(it)
      if (persist) {
        viewModel.saveDraft(args.questionnaireId, patientIds)
      }
    }
  }

  private suspend fun saveDraft(showToast: Boolean = false) {
    if (viewModel.isScreenerCompleted) {
      cacheCurrentPatientResponse(persist = false)
    } else {
      cacheScreenerDraft(persist = false)
    }
    if (!viewModel.hasDraftData()) return
    viewModel.saveDraft(args.questionnaireId, patientIds)
    if (showToast) {
      Toast.makeText(requireContext(), getString(R.string.draft_saved), Toast.LENGTH_SHORT).show()
    }
  }

  private fun showCancelScreenerQuestionnaireAlertDialog() {
    val alertDialog: AlertDialog? =
      activity?.let {
        val builder = AlertDialog.Builder(it)
        builder.apply {
          setMessage(getString(R.string.cancel_questionnaire_message))
          setPositiveButton(getString(android.R.string.yes)) { _, _ ->
            lifecycleScope.launch {
              viewModel.deleteDraft(args.questionnaireId)
              viewModel.clearSessionState()
              hasActiveDraft = false
              skipDraftSaveOnDestroy = true
              NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
            }
          }
          setNeutralButton(getString(R.string.save_as_draft)) { _, _ ->
            lifecycleScope.launch {
              saveDraft(showToast = true)
              viewModel.clearActiveSession()
              NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
            }
          }
          setNegativeButton(getString(android.R.string.no)) { _, _ -> }
        }
        builder.create()
      }
    alertDialog?.show()
  }

  private suspend fun loadDraftIfAvailable(showResumeDialog: Boolean = true) {
    val draft = viewModel.loadDraft(args.questionnaireId)
    if (draft == null) {
      viewModel.markSessionActive()
      return
    }
    draftToRestore = draft
    withContext(Dispatchers.Main) {
      if (showResumeDialog) {
        showResumeDraftDialog()
      } else {
        restoreDraftNowIfReady()
      }
    }
  }

  private fun showResumeDraftDialog() {
    AlertDialog.Builder(requireContext())
      .setTitle(getString(R.string.resume_group_session))
      .setMessage(getString(R.string.resume_group_session_message))
      .setPositiveButton(getString(R.string.resume_session)) { _, _ -> restoreDraftNowIfReady() }
      .setNegativeButton(getString(R.string.start_new_session)) { _, _ ->
        lifecycleScope.launch { viewModel.deleteDraft(args.questionnaireId) }
        viewModel.clearSessionState()
        draftToRestore = null
        shouldRestoreDraft = false
        hasActiveDraft = false
        viewModel.markSessionActive()
      }
      .create()
      .show()
  }

  private fun restoreDraftNowIfReady() {
    val draft = draftToRestore ?: return
    shouldRestoreDraft = true
    if (genericFormEntryViewModel.questionnaire.value != null) {
      viewModel.restoreDraft(draft)
      draftToRestore = null
      shouldRestoreDraft = false
      hasActiveDraft = true
      if (viewModel.isScreenerCompleted) {
        startPatientQuestionnaires()
      }
    }
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
          if (isTabChanging) return

          val newTabPosition = tab.position
          val previousTabPosition = currentSelectedTabPosition

          // If it's the same tab, just load the questionnaire
          if (newTabPosition == previousTabPosition) {
            loadQuestionnaireForTab(newTabPosition)
            return
          }

          // Validate the previous tab before switching
          lifecycleScope.launch {
            val canSwitchTab = validateCurrentForm(previousTabPosition)

            if (canSwitchTab) {
              // Allow tab switch
              currentSelectedTabPosition = newTabPosition
              loadQuestionnaireForTab(newTabPosition)
            } else {
              // Prevent tab switch - revert to previous tab
              isTabChanging = true
              binding.patientTabLayout.selectTab(
                binding.patientTabLayout.getTabAt(previousTabPosition),
              )
              isTabChanging = false

              // Show validation error message
              Toast.makeText(
                  requireContext(),
                  getString(R.string.please_complete_the_required_fields_first),
                  Toast.LENGTH_SHORT,
                )
                .show()
            }
          }
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
          val selectedTab = tab.position
          val patientId = viewModel.patients.value?.get(selectedTab)?.resourceId
          lifecycleScope.launch { cacheCurrentPatientResponse(patientId) }
        }

        override fun onTabReselected(tab: TabLayout.Tab) {
          // No functionality required for now
        }
      },
    )
  }

  private suspend fun validateCurrentForm(tabPosition: Int): Boolean {
    val patientId = viewModel.patients.value?.get(tabPosition)?.resourceId ?: return true

    val questionnaireFragment =
      childFragmentManager.findFragmentByTag(
        QUESTIONNAIRE_FRAGMENT_TAG + patientId,
      ) as? QuestionnaireFragment
        ?: return true

    val questionnaireResponse =
      safeQuestionnaireResponse(questionnaireFragment)
        ?: viewModel.patientResponses[patientId]?.let { savedResponse ->
          parser.parseResource(QuestionnaireResponse::class.java, savedResponse)
            as QuestionnaireResponse
        }
          ?: return true
    cacheCurrentPatientResponse(patientId, questionnaireResponse)
    return viewModel.isValidQuestionnaireResponse(
      genericFormEntryViewModel.questionnaire.value!!,
      questionnaireResponse,
      requireContext(),
    )
  }

  private fun loadQuestionnaireForTab(tabPosition: Int) {
    viewModel.isLoading.value = true
    val patientId = viewModel.patients.value?.get(tabPosition)?.resourceId
    val questionnaireJson = genericFormEntryViewModel.questionnaireJson.value
    if (patientId != null && !questionnaireJson.isNullOrEmpty()) {
      addQuestionnaireFragment(questionnaireJson, patientId)
    } else {
      viewModel.isLoading.value = false
    }
  }

  private fun submitAllEncounters() {
    lifecycleScope.launch {
      if (!viewModel.isScreenerCompleted) {
        Toast.makeText(
            requireContext(),
            getString(R.string.complete_screener_before_submit),
            Toast.LENGTH_SHORT,
          )
          .show()
        return@launch
      }

      viewModel.isLoading.value = true
      cacheCurrentPatientResponse()
      val questionnaire = genericFormEntryViewModel.questionnaire.value
      val patients = viewModel.patients.value.orEmpty()
      if (questionnaire == null || patients.isEmpty()) {
        viewModel.isLoading.value = false
        return@launch
      }

      val missingPatient =
        patients.firstOrNull { patient ->
          viewModel.patientResponses[patient.resourceId].isNullOrBlank()
        }
      if (missingPatient != null) {
        viewModel.isLoading.value = false
        Toast.makeText(
            requireContext(),
            getString(R.string.inputs_missing_for_patient) + " ${missingPatient.name}",
            Toast.LENGTH_SHORT,
          )
          .show()
        return@launch
      }

      for (patient in patients) {
        val responseJson = viewModel.patientResponses[patient.resourceId] ?: continue
        val response =
          parser.parseResource(QuestionnaireResponse::class.java, responseJson)
            as QuestionnaireResponse
        val isValid =
          viewModel.isValidQuestionnaireResponse(questionnaire, response, requireContext())
        if (!isValid) {
          viewModel.isLoading.value = false
          Toast.makeText(
              requireContext(),
              getString(R.string.inputs_missing_for_patient) + " ${patient.name}",
              Toast.LENGTH_SHORT,
            )
            .show()
          return@launch
        }
      }

      viewModel.submittedPatientIds.clear()
      patients.forEach { patient ->
        val patientId = patient.resourceId
        val responseJson = viewModel.patientResponses[patientId] ?: return@forEach
        val response =
          parser.parseResource(QuestionnaireResponse::class.java, responseJson)
            as QuestionnaireResponse
        var encounterId = viewModel.getEncounterIdForPatientId(patientId)
        val encounterType = genericFormEntryViewModel.getEncounterTypeValue()
        pendingSavePatientId = patientId
        if (encounterId != null && encounterType != null) {
          pendingSaveEncounterId = encounterId
          editEncounterViewModel.updateEncounter(response, encounterId, encounterType)
        } else {
          encounterId = encounterId ?: generateUuid()
          pendingSaveEncounterId = encounterId
          genericFormEntryViewModel.saveEncounter(
            response,
            patientId,
            encounterId,
            viewModel.sessionDate,
          )
          viewModel.setPatientIdToEncounterIdMap(patientId, encounterId)
        }
      }
    }
  }

  private fun observeResourcesSaveAction() {
    genericFormEntryViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isError = it.contains("ERROR")
      if (isError) {
        viewModel.isLoading.value = false
        Toast.makeText(requireContext(), getString(R.string.inputs_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      val isSaved = it.contains("SAVED")
      if (isSaved) {
        removeHiddenFragments()
        val patientId = it.split("/")[1]
        viewModel.submittedPatientIds.add(patientId)
        val encounterId = viewModel.getEncounterIdForPatientId(patientId)
        if (encounterId != null) {
          viewModel.saveScreenerObservations(patientId, encounterId)
          viewModel.createInternalObservations(patientId, encounterId)
        }
        pendingSavePatientId = null
        pendingSaveEncounterId = null
        val patientName = viewModel.getPatientName(patientId)
        Toast.makeText(
            requireContext(),
            getString(R.string.encounter_saved_for_patient, patientName),
            Toast.LENGTH_SHORT,
          )
          .show()
        if (viewModel.submittedPatientIds.size == viewModel.patients.value?.size) {
          viewModel.isLoading.value = false
        }
        handleAllEncountersSaved()
      }
    }
    editEncounterViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isError = it.contains("ERROR")
      if (isError) {
        viewModel.isLoading.value = false
        Toast.makeText(requireContext(), getString(R.string.inputs_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      val isSaved = it.contains("SAVED")
      if (isSaved) {
        removeHiddenFragments()
        val patientId = pendingSavePatientId
        val encounterId = pendingSaveEncounterId
        if (patientId != null && encounterId != null) {
          viewModel.saveScreenerObservations(patientId, encounterId)
          viewModel.submittedPatientIds.add(patientId)
        }
        pendingSavePatientId = null
        pendingSaveEncounterId = null
        Toast.makeText(requireContext(), getString(R.string.encounter_updated), Toast.LENGTH_SHORT)
          .show()
        if (viewModel.submittedPatientIds.size == viewModel.patients.value?.size) {
          viewModel.isLoading.value = false
        }
        handleAllEncountersSaved()
      }
    }
  }

  private fun handleAllEncountersSaved() {
    if (viewModel.patients.value?.size == viewModel.submittedPatientIds.size) {
      lifecycleScope.launch { viewModel.deleteDraft(args.questionnaireId) }
      viewModel.clearSessionState()
      draftToRestore = null
      shouldRestoreDraft = false
      hasActiveDraft = false
      hasStartedPatientForms = false
      currentQuestionnaireFragment = null
      currentPatientId = null
      currentSelectedTabPosition = 0
      pendingSavePatientId = null
      pendingSaveEncounterId = null
      val alertDialog: AlertDialog? =
        activity?.let {
          val builder = AlertDialog.Builder(it)
          builder.apply {
            setMessage(getString(R.string.all_encounters_saved_do_you_want_to_exit))
            setPositiveButton(getString(android.R.string.yes)) { _, _ ->
              NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
            }
            setNegativeButton(getString(android.R.string.no)) { _, _ -> }
          }
          builder.create()
        }
      viewModel.isLoading.value = false
      alertDialog?.show()
    }
  }

  private fun removeHiddenFragments() {
    childFragmentManager.fragments.forEach { fragment ->
      if (fragment != currentQuestionnaireFragment) {
        childFragmentManager.commit { remove(fragment) }
      }
    }
  }

  private suspend fun safeQuestionnaireResponse(
    questionnaireFragment: QuestionnaireFragment?,
  ): QuestionnaireResponse? {
    if (questionnaireFragment == null) return null
    if (!questionnaireFragment.isAdded || questionnaireFragment.context == null) return null
    return questionnaireFragment.getQuestionnaireResponse()
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
        if (viewModel.isScreenerCompleted) {
          startPatientQuestionnaires()
        }
      }
    }
  }

  private fun observeLoading() {
    viewModel.isLoading.observe(viewLifecycleOwner) {
      binding.groupProgressBar.visibility = if (it) View.VISIBLE else View.GONE
    }
  }

  override fun onDestroyView() {
    if (!skipDraftSaveOnDestroy) {
      lifecycleScope.launch { saveDraft() }
    }
    currentQuestionnaireFragment = null
    currentPatientId = null
    super.onDestroyView()
  }

  companion object {
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
    const val SCREENER_FRAGMENT_TAG = "screener-fragment-tag"
  }
}
