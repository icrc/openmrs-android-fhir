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

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.FhirEngine
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.patient.PatientDetailsScreen
import org.openmrs.android.fhir.ui.screens.IdentifyPatientDialog
import org.openmrs.android.fhir.ui.screens.UnidentifiedNoteDialog
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientDetailUnidentified
import org.openmrs.android.fhir.viewmodel.PatientDetailsViewModel

class PatientDetailsFragment : Fragment() {

  @Inject lateinit var fhirEngine: FhirEngine

  @Inject lateinit var openMRSHelper: OpenMRSHelper

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val patientDetailsViewModel: PatientDetailsViewModel by viewModels {
    viewModelSavedStateFactory
  }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private val args: PatientDetailsFragmentArgs by navArgs()

  var editMenuItem: MenuItem? = null

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
      val patientData by patientDetailsViewModel.livePatientData.observeAsState(null)
      var identifying by rememberSaveable { mutableStateOf(false) }
      var editingNote by rememberSaveable { mutableStateOf(false) }
      val toIdentify =
        patientData.orEmpty().filterIsInstance<PatientDetailUnidentified>().firstOrNull()
      if (identifying) {
        IdentifyPatientDialog(
          entryId = args.patientId,
          onDismiss = { identifying = false },
          onIdentified = { realId, moved ->
            identifying = false
            onIdentified(realId, moved)
          },
        )
      }
      if (editingNote && toIdentify != null) {
        UnidentifiedNoteDialog(
          title = stringResource(R.string.identify_note_edit_title),
          initial = toIdentify.note,
          confirmLabel = stringResource(R.string.save),
          onConfirm = {
            editingNote = false
            patientDetailsViewModel.updateNote(it)
          },
          onDismiss = { editingNote = false },
        )
      }
      PatientDetailsScreen(
        patientData = patientData,
        onEditPatient = ::onEditPatientClick,
        onViewEncounter = ::onViewEncounterClick,
        onEditEncounter = ::onEditEncounterClick,
        onEndVisit = ::onEditVisitClick,
        onStartVisit = ::onStartVisitClick,
        onReopenVisit = ::onReopenVisitClick,
        onCreateEncounter = ::onCreateEncounterClick,
        onIdentifyPatient = { identifying = true },
        onEditNote = { editingNote = true },
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    updateArguments(args.patientId)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = getString(R.string.patient_details_title)
      setDisplayHomeAsUpEnabled(true)
      setHomeAsUpIndicator(
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back_24),
      )
    }
    patientDetailsViewModel.livePatientData.observe(viewLifecycleOwner) {
      requireActivity().invalidateOptionsMenu()
    }
    patientDetailsViewModel.getPatientDetailData()
    mainActivityViewModel.setDrawerEnabled(false)
  }

  override fun onPrepareOptionsMenu(menu: Menu) {
    super.onPrepareOptionsMenu(menu)
    val editItem = menu.findItem(R.id.menu_patient_edit)
    editItem?.isEnabled = patientDetailsViewModel.livePatientData.value?.isNotEmpty() == true
    // A placeholder (patient to identify after) has no registration to edit.
    editItem?.isVisible =
      patientDetailsViewModel.livePatientData.value.orEmpty().none {
        it is PatientDetailUnidentified
      }
  }

  private fun onCreateEncounterClick() {
    lifecycleScope.launch {
      // With the visits managed by the user, an encounter needs an open one.
      if (patientDetailsViewModel.automaticVisits() || patientDetailsViewModel.hasActiveVisit()) {
        navigateToCreateEncounter()
      } else {
        showNoOpenVisitDialog()
      }
    }
  }

  private fun showNoOpenVisitDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(getString(R.string.no_open_visit_title))
      .setMessage(getString(R.string.no_open_visit_message))
      .setPositiveButton(getString(R.string.open_visit_action)) { _, _ ->
        showStartVisitDateTimeDialog { navigateToCreateEncounter() }
      }
      .setNegativeButton(getString(R.string.cancel), null)
      .show()
  }

  private fun onStartVisitClick() {
    showStartVisitDateTimeDialog {}
  }

  private fun onReopenVisitClick(visitId: String) {
    lifecycleScope.launch {
      if (!patientDetailsViewModel.reopenVisit(visitId)) {
        AppMessage.show(
          requireContext(),
          getString(R.string.reopen_visit_blocked),
          MessageKind.Error,
          long = true,
        )
      }
    }
  }

  private fun navigateToCreateEncounter() {
    findNavController()
      .navigate(
        PatientDetailsFragmentDirections.actionPatientDetailsToCreateEncounterFragment(
          patientId = args.patientId,
          isGroupEncounter = false,
        ),
      )
  }

  fun showEndVisitDialog(context: Context, visitId: String, onEndVisitConfirmed: (String) -> Unit) {
    val builder = MaterialAlertDialogBuilder(context)
    builder.setMessage(getString(R.string.end_visit_confirmation_message))

    builder.setPositiveButton(getString(R.string.yes)) { dialog, _ ->
      onEndVisitConfirmed(visitId)
      dialog.dismiss()
    }

    builder.setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }

    val dialog = builder.create()
    dialog.show()
  }

  /** Asks when the visit started, opens it, then calls [onStarted]. */
  private fun showStartVisitDateTimeDialog(onStarted: () -> Unit) {
    val calendar = Calendar.getInstance()

    DatePickerDialog(
        requireContext(),
        { _, selectedYear, selectedMonth, selectedDayOfMonth ->
          calendar[selectedYear, selectedMonth] = selectedDayOfMonth

          TimePickerDialog(
              requireContext(),
              { _, selectedHourOfDay, selectedMinute ->
                calendar[Calendar.HOUR_OF_DAY] = selectedHourOfDay
                calendar[Calendar.MINUTE] = selectedMinute

                val selectedDateTime = calendar.time

                if (selectedDateTime.after(Date())) {
                  AppMessage.show(
                    requireContext(),
                    getString(R.string.future_visit_datetime_not_allowed),
                    MessageKind.Error,
                  )
                } else {
                  lifecycleScope.launch {
                    patientDetailsViewModel.startVisit(selectedDateTime)
                    onStarted()
                  }
                }
              },
              calendar[Calendar.HOUR_OF_DAY],
              calendar[Calendar.MINUTE],
              true,
            )
            .apply { setTitle(getString(R.string.select_visit_time_title)) }
            .show()
        },
        calendar[Calendar.YEAR],
        calendar[Calendar.MONTH],
        calendar[Calendar.DAY_OF_MONTH],
      )
      .apply {
        setTitle(getString(R.string.start_new_visit_title))
        datePicker.maxDate = calendar.timeInMillis
      }
      .show()
  }

  /** Opens the encounter to look at it; the screen has an Edit action for changing it. */
  private fun onViewEncounterClick(encounterId: String, encounterType: String) {
    findNavController()
      .navigate(
        R.id.action_patient_details_to_editEncounterFragment,
        bundleOf(
          "encounter_id" to encounterId,
          "encounter_type" to encounterType,
          EditEncounterFragment.ARG_READ_ONLY to true,
        ),
      )
  }

  private fun onEditEncounterClick(
    encounterId: String,
    encounterType: String,
  ) {
    findNavController()
      .navigate(
        PatientDetailsFragmentDirections.actionPatientDetailsToEditEncounterFragment(
          encounterId,
          encounterType,
        ),
      )
  }

  private fun onEditVisitClick(visitId: String) {
    context?.let { ctx ->
      showEndVisitDialog(ctx, visitId) { id ->
        lifecycleScope.launch { patientDetailsViewModel.endVisit(id) }
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.details_options_menu, menu)
    editMenuItem = menu.findItem(R.id.menu_patient_edit)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this).navigateUp()
        true
      }
      R.id.menu_patient_edit -> {
        onEditPatientClick()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  /** The records now belong to the real patient: open their page. */
  private fun onIdentified(patientId: String, moved: Int) {
    AppMessage.show(
      requireContext(),
      getString(R.string.identify_done, moved),
      MessageKind.Success,
    )
    val navController = findNavController()
    navController.popBackStack()
    // From the patient list: straight to the patient. From anywhere else, back is enough.
    runCatching {
      navController.navigate(PatientListFragmentDirections.navigateToProductDetail(patientId))
    }
  }

  private fun onEditPatientClick() {
    findNavController()
      .navigate(
        PatientDetailsFragmentDirections.navigateToEditPatient(
          args.patientId,
          AppConfig.get(requireContext()).registrationForm,
        ),
      )
  }

  private fun updateArguments(patientId: String) {
    requireArguments().putString("patient_id", patientId)
  }
}
