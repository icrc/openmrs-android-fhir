package org.openmrs.android.fhir.fragments

import android.app.AlertDialog
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.FhirEngine
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.MockConstants
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.PatientDetailsRecyclerViewAdapter
import org.openmrs.android.fhir.databinding.PatientDetailBinding
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.viewmodel.PatientDetailsViewModel
import org.openmrs.android.helpers.OpenMRSHelper.VisitHelper.endVisit
import java.util.*
import javax.inject.Inject
import kotlin.getValue

class PatientDetailsFragment : Fragment() {

  @Inject
  lateinit var fhirEngine: FhirEngine

  @Inject
  lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory
  private val patientDetailsViewModel: PatientDetailsViewModel by viewModels {
    viewModelSavedStateFactory
  }

  private val args: PatientDetailsFragmentArgs by navArgs()
  private var _binding: PatientDetailBinding? = null
  private val binding
    get() = _binding!!

  var editMenuItem: MenuItem? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = PatientDetailBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    updateArguments(args.patientId)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    val adapter = PatientDetailsRecyclerViewAdapter(::onCreateEncounterClick, ::onEditEncounterClick, ::onEditVisitClick)
    binding.createEncounterFloatingButton.setOnClickListener { onCreateEncounterClick() }
    binding.recycler.adapter = adapter
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = "Patient"
      setDisplayHomeAsUpEnabled(true)
    }
    patientDetailsViewModel.livePatientData.observe(viewLifecycleOwner) {
      adapter.submitList(it)
      if (!it.isNullOrEmpty()) {
        editMenuItem?.isEnabled = true
      }
    }
    patientDetailsViewModel.getPatientDetailData()
    (activity as MainActivity).setDrawerEnabled(false)
  }

  private fun onCreateEncounterClick() {
    lifecycleScope.launch {
      val hasActiveVisit = patientDetailsViewModel.hasActiveVisit()
      if (MockConstants.WRAP_ENCOUNTER) {
        navigateToCreateEncounter()
      } else {
        if (hasActiveVisit) {
          navigateToCreateEncounter()
        } else {
          showStartVisitDateTimeDialog()
        }
      }
    }
  }

  private fun navigateToCreateEncounter() {
    findNavController().navigate(
      PatientDetailsFragmentDirections.actionPatientDetailsToCreateEncounterFragment(args.patientId)
    )
  }


  fun showEndVisitDialog(context: Context, visitId: String, onEndVisitConfirmed: (String) -> Unit) {
    val builder = AlertDialog.Builder(context)
    builder.setMessage("Are you sure you want to end the visit now?")

    builder.setPositiveButton("Yes") { dialog, _ ->
      onEndVisitConfirmed(visitId)
      dialog.dismiss()
    }

    builder.setNegativeButton("No") { dialog, _ ->
      dialog.dismiss()
    }

    val dialog = builder.create()
    dialog.show()
  }


  private fun showStartVisitDateTimeDialog() {
    val calendar = Calendar.getInstance()

    DatePickerDialog(
      requireContext(),
      { _, selectedYear, selectedMonth, selectedDayOfMonth ->
        calendar.set(selectedYear, selectedMonth, selectedDayOfMonth)

        TimePickerDialog(
          requireContext(),
          { _, selectedHourOfDay, selectedMinute ->
            calendar.set(Calendar.HOUR_OF_DAY, selectedHourOfDay)
            calendar.set(Calendar.MINUTE, selectedMinute)

            val selectedDateTime = calendar.time

            if (selectedDateTime.after(Date())) {
              Toast.makeText(requireContext(), "Future date/time is not allowed.", Toast.LENGTH_SHORT).show()
            } else {
              patientDetailsViewModel.createVisit(selectedDateTime)
              navigateToCreateEncounter()
            }
          },
          calendar.get(Calendar.HOUR_OF_DAY),
          calendar.get(Calendar.MINUTE),
          true
        ).apply {
          setTitle("Select time for the visit")
        }.show()
      },
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH),
      calendar.get(Calendar.DAY_OF_MONTH)
    ).apply {
      setTitle("Start a new visit?")
      datePicker.maxDate = calendar.timeInMillis
    }.show()
  }

  private fun onEditEncounterClick(encounterId: String, formDisplay: String, formResource: String) {
    findNavController().navigate(
      PatientDetailsFragmentDirections.actionPatientDetailsToEditEncounterFragment(
        encounterId, formResource
      )
    )
  }


  private fun onEditVisitClick(visitId: String) {
    context?.let { ctx ->
      showEndVisitDialog(ctx, visitId) { id ->
        lifecycleScope.launch {
          endVisit(fhirEngine, id)
        }
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
        findNavController()
          .navigate(PatientDetailsFragmentDirections.navigateToEditPatient(args.patientId))
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun updateArguments(patientId: String) {
    requireArguments().putString("patient_id", patientId)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
