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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
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
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft
import org.openmrs.android.fhir.databinding.GroupFormentryFragmentBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.AppBarInsets
import org.openmrs.android.fhir.extensions.generateUuid
import org.openmrs.android.fhir.extensions.newQuestionnaireFragment
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModel
import org.openmrs.android.fhir.viewmodel.GenericFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.GroupFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import timber.log.Timber

class GroupFormEntryFragment : Fragment(R.layout.group_formentry_fragment) {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val genericFormEntryViewModel: GenericFormEntryViewModel by viewModels {
    viewModelSavedStateFactory
  }
  private val viewModel: GroupFormEntryViewModel by
    viewModels<GroupFormEntryViewModel> { viewModelSavedStateFactory }

  private val editEncounterViewModel: EditEncounterViewModel by viewModels {
    viewModelSavedStateFactory
  }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private val args: GroupFormEntryFragmentArgs by navArgs()

  private var _binding: GroupFormentryFragmentBinding? = null

  private val binding
    get() = _binding!!

  // Keep track of current questionnaire fragment
  private var currentQuestionnaireFragment: QuestionnaireFragment? = null

  /** Patients whose form is alive, the one in use first. */
  private val recentForms = ArrayDeque<String>()
  private var currentPatientId: String? = null
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  // Track the currently selected tab and prevent recursive tab changes
  private var currentSelectedTabPosition = 0
  private var isTabChanging = false
  private var pendingSavePatientId: String? = null
  private var pendingSaveEncounterId: String? = null
  private var draftToRestore: GroupSessionDraft? = null
  private var shouldRestoreDraft = false
  private var activeSubmissionSessionId: String? = null
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
    keepBelowAppBar()
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
    mainActivityViewModel.setDrawerEnabled(false)
    observePatients()
  }

  /** The screen is drawn under the app bar overlay: pad by what is really there. */
  private fun keepBelowAppBar() {
    viewLifecycleOwner.lifecycleScope.launch {
      AppBarInsets.heightPx.collect { barBottomPx ->
        binding.root.updatePadding(
          top = barBottomPx + (resources.displayMetrics.density * 4).toInt(),
        )
      }
    }
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
    val fragment =
      newQuestionnaireFragment(
        questionnaireJson,
        responseJson = viewModel.screenerResponseJson,
        showSubmitButton = true,
        submitButtonText = getString(R.string.submit),
      )
    childFragmentManager.commit {
      replace(R.id.form_entry_container, fragment, SCREENER_FRAGMENT_TAG)
    }
    currentQuestionnaireFragment = fragment
  }

  private fun addQuestionnaireFragment(questionnaireJson: String, patientId: String) {
    if (questionnaireJson.isEmpty()) {
      AppMessage.show(requireActivity(), R.string.questionnaire_error_message, MessageKind.Error)
      NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
      viewModel.isLoading.value = false
      return
    }

    // Building a form is the slow part of switching patients, so the last few stay alive (hidden)
    // and coming back to one is instant, with the answers and the open page as they were left.
    // A form that is built again starts from the answers kept in the view model.
    val tag = QUESTIONNAIRE_FRAGMENT_TAG + patientId
    val kept = childFragmentManager.findFragmentByTag(tag) as? QuestionnaireFragment
    val newFragment =
      kept
        ?: newQuestionnaireFragment(
          questionnaireJson,
          responseJson = viewModel.patientResponses[patientId],
          showSubmitButton = true,
          submitButtonText = getString(R.string.submit),
        )
    recentForms.remove(patientId)
    recentForms.addFirst(patientId)
    val dropped = mutableSetOf<String>()
    while (recentForms.size > MAX_LIVE_FORMS) dropped.add(recentForms.removeLast())
    childFragmentManager.commit {
      setReorderingAllowed(true)
      childFragmentManager.fragments.forEach { fragment ->
        val other = fragment.tag ?: return@forEach
        when {
          fragment === newFragment -> Unit
          other == SCREENER_FRAGMENT_TAG -> remove(fragment)
          other.startsWith(QUESTIONNAIRE_FRAGMENT_TAG) &&
            other.removePrefix(QUESTIONNAIRE_FRAGMENT_TAG) in dropped -> remove(fragment)
          else -> hide(fragment)
        }
      }
      if (kept != null) show(kept) else add(R.id.form_entry_container, newFragment, tag)
    }

    currentQuestionnaireFragment = newFragment
    currentPatientId = patientId

    view?.post { viewModel.isLoading.value = false }
  }

  /** The forms are out of date (another questionnaire, a restored draft): build them again. */
  private fun discardPatientForms() {
    recentForms.clear()
    val stale =
      childFragmentManager.fragments.filter {
        it.tag?.startsWith(QUESTIONNAIRE_FRAGMENT_TAG) == true
      }
    if (stale.isEmpty()) return
    childFragmentManager.commitNow(allowStateLoss = true) { stale.forEach { remove(it) } }
  }

  private fun startPatientQuestionnaires() {
    if (hasStartedPatientForms) return
    if (viewModel.patients.value.isNullOrEmpty()) return
    hasStartedPatientForms = true
    AuditLog.record(
      AuditAction.GROUP_SESSION_STARTED,
      AuditOutcome.SUCCESS,
      "form" to args.questionnaireId,
      "patients" to viewModel.patients.value?.size,
    )
    binding.patientTabLayout.visibility = View.VISIBLE
    observeQuestionnaire()
    loadQuestionnaireForTab(currentSelectedTabPosition)
  }

  private suspend fun cacheCurrentPatientResponse(
    patientId: String? = currentPatientId,
    questionnaireResponse: QuestionnaireResponse? = null,
    persist: Boolean = true,
    validate: Boolean = true,
  ) {
    val response =
      questionnaireResponse
        ?: safeQuestionnaireResponse(currentQuestionnaireFragment as? QuestionnaireFragment)

    if (patientId.isNullOrBlank() || response == null) return

    try {
      viewModel.patientResponses[patientId] = parser.encodeResourceToString(response)
      if (validate) updatePatientValidationIndicator(patientId, response)
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
      AppMessage.show(requireContext(), getString(R.string.draft_saved), MessageKind.Success)
    }
  }

  private fun showCancelScreenerQuestionnaireAlertDialog() {
    val alertDialog: AlertDialog? =
      activity?.let {
        val builder = AlertDialog.Builder(it)
        builder.apply {
          setMessage(getString(R.string.cancel_questionnaire_message))
          setPositiveButton(getString(android.R.string.cancel)) { _, _ -> }
          setNegativeButton(getString(R.string.save_as_draft)) { _, _ ->
            lifecycleScope.launch {
              saveDraft(showToast = true)
              viewModel.clearActiveSession()
              NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
            }
          }
          setNeutralButton(getString(R.string.discard)) { _, _ ->
            lifecycleScope.launch {
              viewModel.deleteDraft(args.questionnaireId)
              viewModel.clearSessionState()
              hasActiveDraft = false
              skipDraftSaveOnDestroy = true
              NavHostFragment.findNavController(this@GroupFormEntryFragment).navigateUp()
            }
          }
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
      val tab = binding.patientTabLayout.newTab()
      tab.customView = createTabView(patient.name)
      binding.patientTabLayout.addTab(tab)
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

          // Immediately restore the previous tab selection to prevent visual jumps
          revertToTab(previousTabPosition)

          // Validate the previous tab before switching
          lifecycleScope.launch {
            val hasValidationErrors = validateCurrentForm(previousTabPosition)
            val patientId = viewModel.patients.value?.get(previousTabPosition)?.resourceId
            if (!patientId.isNullOrBlank()) {
              updateTabIndicatorState(previousTabPosition, hasValidationErrors)
            }

            if (!hasValidationErrors) {
              switchToTab(newTabPosition)
              saveDraftInBackground()
            } else {
              showIncompleteFormDialog(
                onContinue = {
                  switchToTab(newTabPosition)
                  saveDraftInBackground()
                },
                onStay = { /* keep current tab */},
              )
            }
          }
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
          if (isTabChanging) return
          val selectedTab = tab.position
          val patientId = viewModel.patients.value?.get(selectedTab)?.resourceId
          lifecycleScope.launch { cacheCurrentPatientResponse(patientId) }
        }

        override fun onTabReselected(tab: TabLayout.Tab) {
          // No functionality required for now
        }
      },
    )

    lifecycleScope.launch { refreshTabValidationIndicators() }
  }

  /** The draft holds every answer and both forms: writing it must not hold up the next patient. */
  private fun saveDraftInBackground() {
    lifecycleScope.launch { viewModel.saveDraft(args.questionnaireId, patientIds) }
  }

  private fun createTabView(patientName: String, showIndicator: Boolean = false): View {
    val tabView =
      LayoutInflater.from(requireContext())
        .inflate(R.layout.patient_tab_item, binding.patientTabLayout, false)
    tabView.findViewById<TextView>(R.id.patient_tab_title)?.text = patientName
    tabView.findViewById<View>(R.id.patient_tab_indicator)?.visibility =
      if (showIndicator) View.VISIBLE else View.GONE
    return tabView
  }

  private fun updateTabIndicatorState(tabPosition: Int, hasErrors: Boolean) {
    val tab = binding.patientTabLayout.getTabAt(tabPosition) ?: return
    val patientName = viewModel.patients.value?.getOrNull(tabPosition)?.name
    val tabView =
      (tab.customView ?: createTabView(patientName.orEmpty(), hasErrors)).also { customView ->
        customView.findViewById<TextView>(R.id.patient_tab_title)?.text = patientName
        customView.findViewById<View>(R.id.patient_tab_indicator)?.visibility =
          if (hasErrors) View.VISIBLE else View.GONE
      }
    tab.customView = tabView
  }

  private suspend fun updatePatientValidationIndicator(
    patientId: String,
    questionnaireResponse: QuestionnaireResponse,
  ) {
    val questionnaire = genericFormEntryViewModel.questionnaire.value ?: return
    val hasValidationErrors =
      !viewModel.isValidQuestionnaireResponse(
        questionnaire,
        questionnaireResponse,
        requireContext(),
      )
    val tabPosition = viewModel.patients.value?.indexOfFirst { it.resourceId == patientId } ?: -1
    if (tabPosition >= 0) {
      withContext(Dispatchers.Main) { updateTabIndicatorState(tabPosition, hasValidationErrors) }
    }
  }

  private suspend fun refreshTabValidationIndicators() {
    val questionnaire = genericFormEntryViewModel.questionnaire.value ?: return
    val patients = viewModel.patients.value.orEmpty()
    patients.forEachIndexed { index, patient ->
      val responseJson = viewModel.patientResponses[patient.resourceId] ?: return@forEachIndexed
      try {
        val response =
          parser.parseResource(QuestionnaireResponse::class.java, responseJson)
            as QuestionnaireResponse
        val hasValidationErrors =
          !viewModel.isValidQuestionnaireResponse(questionnaire, response, requireContext())
        withContext(Dispatchers.Main) { updateTabIndicatorState(index, hasValidationErrors) }
      } catch (exception: Exception) {
        Timber.e(exception.localizedMessage)
      }
    }
  }

  private suspend fun validateCurrentForm(tabPosition: Int): Boolean {
    val patientId = viewModel.patients.value?.get(tabPosition)?.resourceId ?: return false

    val questionnaireFragment =
      childFragmentManager.findFragmentByTag(
        QUESTIONNAIRE_FRAGMENT_TAG + patientId,
      ) as? QuestionnaireFragment
        ?: return false

    val questionnaireResponse =
      safeQuestionnaireResponse(questionnaireFragment)
        ?: viewModel.patientResponses[patientId]?.let { savedResponse ->
          parser.parseResource(QuestionnaireResponse::class.java, savedResponse)
            as QuestionnaireResponse
        }
          ?: return false
    // Kept in memory now and validated once below; the draft is written after the switch.
    cacheCurrentPatientResponse(
      patientId,
      questionnaireResponse,
      persist = false,
      validate = false,
    )

    val questionnaire = genericFormEntryViewModel.questionnaire.value ?: return false
    val hasValidationErrors =
      !viewModel.isValidQuestionnaireResponse(
        questionnaire,
        questionnaireResponse,
        requireContext(),
      )
    updateTabIndicatorState(tabPosition, hasValidationErrors)
    return hasValidationErrors
  }

  private fun switchToTab(tabPosition: Int) {
    val newTab = binding.patientTabLayout.getTabAt(tabPosition) ?: return
    isTabChanging = true
    binding.patientTabLayout.selectTab(newTab)
    isTabChanging = false
    currentSelectedTabPosition = tabPosition
    loadQuestionnaireForTab(tabPosition)
  }

  private fun revertToTab(previousTabPosition: Int) {
    val previousTab = binding.patientTabLayout.getTabAt(previousTabPosition) ?: return
    isTabChanging = true
    binding.patientTabLayout.selectTab(previousTab)
    isTabChanging = false
  }

  private fun showIncompleteFormDialog(
    onContinue: () -> Unit,
    onStay: () -> Unit,
  ) {
    val message = getString(R.string.required_questions_missing_dialog_message)

    AlertDialog.Builder(requireContext())
      .setTitle(getString(R.string.incomplete_form_dialog_title))
      .setMessage(message)
      .setPositiveButton(getString(R.string.continue_without_completing)) { _, _ -> onContinue() }
      .setNegativeButton(getString(R.string.stay_on_current_patient)) { _, _ -> onStay() }
      .setCancelable(false)
      .create()
      .show()
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
        AppMessage.show(
          requireContext(),
          getString(R.string.complete_screener_before_submit),
          MessageKind.Error,
        )
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

      val patientsWithIssues = mutableListOf<PatientListViewModel.PatientItem>()
      for (patient in patients) {
        val responseJson = viewModel.patientResponses[patient.resourceId]
        if (responseJson.isNullOrBlank()) {
          patientsWithIssues.add(patient)
          continue
        }

        val response =
          parser.parseResource(QuestionnaireResponse::class.java, responseJson)
            as QuestionnaireResponse
        val hasValidationErrors =
          !viewModel.isValidQuestionnaireResponse(questionnaire, response, requireContext())
        if (hasValidationErrors) {
          patientsWithIssues.add(patient)
        }
      }

      if (patientsWithIssues.isNotEmpty()) {
        viewModel.isLoading.value = false
        showIncompletePatientsDialog(patientsWithIssues)
        return@launch
      }

      viewModel.submittedPatientIds.clear()
      AuditLog.record(
        AuditAction.GROUP_SESSION_SUBMITTED,
        AuditOutcome.SUCCESS,
        "form" to args.questionnaireId,
        "patients" to patients.size,
      )
      activeSubmissionSessionId = viewModel.sessionId
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

  private fun showIncompletePatientsDialog(patients: List<PatientListViewModel.PatientItem>) {
    val patientNames = patients.map { it.name }.toTypedArray()

    AlertDialog.Builder(requireContext())
      .setTitle(getString(R.string.incomplete_patients_dialog_title))
      .setMessage(getString(R.string.incomplete_patients_dialog_message))
      .setItems(patientNames) { dialog, which ->
        val patientId = patients[which].resourceId
        val tabIndex = viewModel.patients.value?.indexOfFirst { it.resourceId == patientId } ?: -1
        if (tabIndex >= 0) {
          binding.patientTabLayout.getTabAt(tabIndex)?.select()
        }
        dialog.dismiss()
      }
      .setPositiveButton(android.R.string.ok, null)
      .setCancelable(true)
      .create()
      .show()
  }

  private fun observeResourcesSaveAction() {
    genericFormEntryViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isError = it.contains("ERROR")
      if (isError) {
        viewModel.isLoading.value = false
        AppMessage.show(requireContext(), getString(R.string.inputs_missing), MessageKind.Error)
        return@observe
      }
      val isSaved = it.contains("SAVED")
      if (isSaved) {
        removeHiddenFragments()
        val patientId = it.split("/")[1]
        val encounterId = viewModel.getEncounterIdForPatientId(patientId)
        if (encounterId != null) {
          lifecycleScope.launch {
            viewModel.saveScreenerObservations(patientId, encounterId)
            viewModel.createInternalObservations(
              patientId,
              encounterId,
              activeSubmissionSessionId ?: viewModel.sessionId,
            )
            viewModel.submittedPatientIds.add(patientId)
            if (viewModel.submittedPatientIds.size == viewModel.patients.value?.size) {
              viewModel.isLoading.value = false
            }
            handleAllEncountersSaved()
          }
        }
        pendingSavePatientId = null
        pendingSaveEncounterId = null
        val patientName = viewModel.getPatientName(patientId)
        AppMessage.show(
          requireContext(),
          getString(R.string.encounter_saved_for_patient, patientName),
          MessageKind.Success,
        )
      }
    }
    editEncounterViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      val isError = it.contains("ERROR")
      if (isError) {
        viewModel.isLoading.value = false
        AppMessage.show(requireContext(), getString(R.string.inputs_missing), MessageKind.Error)
        return@observe
      }
      val isSaved = it.contains("SAVED")
      if (isSaved) {
        removeHiddenFragments()
        val patientId = pendingSavePatientId
        val encounterId = pendingSaveEncounterId
        if (patientId != null && encounterId != null) {
          lifecycleScope.launch {
            viewModel.saveScreenerObservations(patientId, encounterId)
            viewModel.createInternalObservations(
              patientId,
              encounterId,
              activeSubmissionSessionId ?: viewModel.sessionId,
            )
            viewModel.submittedPatientIds.add(patientId)
            if (viewModel.submittedPatientIds.size == viewModel.patients.value?.size) {
              viewModel.isLoading.value = false
            }
            handleAllEncountersSaved()
          }
        }
        pendingSavePatientId = null
        pendingSaveEncounterId = null
        AppMessage.show(
          requireContext(),
          getString(R.string.encounter_updated),
          MessageKind.Success,
        )
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
      activeSubmissionSessionId = null
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
    recentForms.retainAll(setOfNotNull(currentPatientId))
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
      discardPatientForms()
      if (patientId != null) {
        viewModel.isLoading.value = true
        it?.let {
          addQuestionnaireFragment(
            questionnaireJson = it,
            patientId = patientId,
          )
        }
      }
      lifecycleScope.launch { refreshTabValidationIndicators() }
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

    /** Forms kept alive: each holds all its views, so only a few are. */
    private const val MAX_LIVE_FORMS = 3
  }
}
