/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.android.fhir.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.sync.CurrentSyncJobStatus
import com.google.android.fhir.sync.LastSyncJobStatus
import com.google.android.fhir.sync.PeriodicSyncJobStatus
import com.google.android.fhir.sync.SyncJobStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.adapters.PatientItemRecyclerViewAdapter
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentPatientListBinding
import org.openmrs.android.fhir.extensions.launchAndRepeatStarted
import timber.log.Timber
import kotlin.math.roundToInt

class PatientListFragment : Fragment() {
  private lateinit var fhirEngine: FhirEngine
  private lateinit var patientListViewModel: PatientListViewModel
  private lateinit var topBanner: LinearLayout
  private lateinit var syncStatus: TextView
  private lateinit var syncPercent: TextView
  private lateinit var syncProgress: ProgressBar
  private lateinit var patientQuery: String
  private var _binding: FragmentPatientListBinding? = null
  private val binding
    get() = _binding!!

  private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentPatientListBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = resources.getString(R.string.title_patient_list)
      setDisplayHomeAsUpEnabled(true)
    }
    fhirEngine = FhirApplication.fhirEngine(requireContext())
    patientListViewModel =
      ViewModelProvider(
          this,
        PatientListViewModel.PatientListViewModelFactory(
          requireActivity().application,
          fhirEngine
        ),
      )[PatientListViewModel::class.java]
    val recyclerView: RecyclerView = binding.patientListContainer.patientList
    val adapter = PatientItemRecyclerViewAdapter(this::onPatientItemClicked)
    recyclerView.adapter = adapter

    patientListViewModel.liveSearchedPatients.observe(viewLifecycleOwner) {
      Timber.d("Submitting ${it.count()} patient records")
      adapter.submitList(it)
    }

    patientQuery = binding.patientInputEditText.text.toString()
    addSearchTextChangeListener()

    topBanner = binding.syncStatusContainer.linearLayoutSyncStatus
    topBanner.visibility = View.GONE
    syncStatus = binding.syncStatusContainer.tvSyncingStatus
    syncPercent = binding.syncStatusContainer.tvSyncingPercent
    syncProgress = binding.syncStatusContainer.progressSyncing
    requireActivity()
      .onBackPressedDispatcher
      .addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            if (binding.patientInputEditText.text.toString().trim().isNotEmpty()) {
              binding.patientInputEditText.setText("")
            } else {
              isEnabled = false
              activity?.onBackPressed()
            }
          }
        },
      )

    binding.apply {
      addPatient.setOnClickListener { onAddPatientClick() }
    }
    setHasOptionsMenu(true)
    (activity as MainActivity).setDrawerEnabled(false)
    launchAndRepeatStarted(
      { mainActivityViewModel.pollState.collect(::currentSyncJobStatus) },
      { mainActivityViewModel.pollPeriodicSyncJobStatus.collect(::periodicSyncJobStatus) },
    )
  }

  private fun addSearchTextChangeListener() {
    binding.patientInputEditText.doOnTextChanged { newText, _, _, _ ->
      patientListViewModel.searchPatientsByName(newText.toString().trim())
    }
  }

  private fun currentSyncJobStatus(currentSyncJobStatus: CurrentSyncJobStatus) {
    when (currentSyncJobStatus) {
      is CurrentSyncJobStatus.Running -> {
        Timber.i(
          "Sync: ${currentSyncJobStatus::class.java.simpleName} with data ${currentSyncJobStatus.inProgressSyncJob}",
        )
        fadeInTopBanner(currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Succeeded -> {
        Timber.i(
          "Sync: ${currentSyncJobStatus::class.java.simpleName} at ${currentSyncJobStatus.timestamp}",
        )
        patientListViewModel.searchPatientsByName(binding.patientInputEditText.text.toString().trim())
        mainActivityViewModel.updateLastSyncTimestamp(currentSyncJobStatus.timestamp)
        fadeOutTopBanner(currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Failed -> {
        Timber.i(
          "Sync: ${currentSyncJobStatus::class.java.simpleName} at ${currentSyncJobStatus.timestamp}",
        )
        patientListViewModel.searchPatientsByName(binding.patientInputEditText.text.toString().trim())
        mainActivityViewModel.updateLastSyncTimestamp(currentSyncJobStatus.timestamp)
        fadeOutTopBanner(currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Enqueued -> {
        Timber.i("Sync: Enqueued")
        patientListViewModel.searchPatientsByName(binding.patientInputEditText.text.toString().trim())
        fadeOutTopBanner(currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Cancelled -> {
        Timber.i("Sync: Cancelled")
        fadeOutTopBanner(currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Blocked -> {
        Timber.i("Sync: Blocked")
        fadeOutTopBanner(currentSyncJobStatus)
      }
    }
  }

  private fun periodicSyncJobStatus(periodicSyncJobStatus: PeriodicSyncJobStatus) {
    when (periodicSyncJobStatus.currentSyncJobStatus) {
      is CurrentSyncJobStatus.Running -> {
        fadeInTopBanner(periodicSyncJobStatus.currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Succeeded -> {
        val lastSyncTimestamp =
          (periodicSyncJobStatus.currentSyncJobStatus as CurrentSyncJobStatus.Succeeded).timestamp
        patientListViewModel.searchPatientsByName(binding.patientInputEditText.text.toString().trim())
        mainActivityViewModel.updateLastSyncTimestamp(lastSyncTimestamp)
        fadeOutTopBanner(periodicSyncJobStatus.currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Failed -> {
        val lastSyncTimestamp =
          (periodicSyncJobStatus.currentSyncJobStatus as CurrentSyncJobStatus.Failed).timestamp
        Timber.i(
          "Sync: ${periodicSyncJobStatus.currentSyncJobStatus::class.java.simpleName} at $lastSyncTimestamp}",
        )
        patientListViewModel.searchPatientsByName(binding.patientInputEditText.text.toString().trim())
        mainActivityViewModel.updateLastSyncTimestamp(lastSyncTimestamp)
        fadeOutTopBanner(periodicSyncJobStatus.currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Enqueued -> {
        Timber.i("Sync: Enqueued")
        patientListViewModel.searchPatientsByName(binding.patientInputEditText.text.toString().trim())
        fadeOutTopBanner(periodicSyncJobStatus.currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Cancelled -> {
        Timber.i("Sync: Cancelled")
        fadeOutTopBanner(periodicSyncJobStatus.currentSyncJobStatus)
      }
      is CurrentSyncJobStatus.Blocked -> {
        Timber.i("Sync: Blocked")
        fadeOutTopBanner(periodicSyncJobStatus.currentSyncJobStatus)
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        if (binding.patientInputEditText.text.toString().trim().isNotEmpty()) {
          binding.patientInputEditText.setText("")
        } else {
//          isEnabled = false
          NavHostFragment.findNavController(this).navigateUp()

        }
        true
      }
      else -> false
    }
  }

  private fun onPatientItemClicked(patientItem: PatientListViewModel.PatientItem) {
    findNavController()
      .navigate(PatientListFragmentDirections.navigateToProductDetail(patientItem.resourceId))
  }

  private fun onAddPatientClick() {
    lifecycleScope.launch {
      if(
        context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME) != null
      ) {
        findNavController().navigate(PatientListFragmentDirections.actionPatientListToAddPatientFragment())
      } else {
        Toast.makeText(context, "Please select a location first", Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun fadeInTopBanner(state: CurrentSyncJobStatus) {
    if (topBanner.visibility != View.VISIBLE) {
      syncStatus.text = resources.getString(R.string.syncing).uppercase()
      syncPercent.text = ""
      syncProgress.progress = 0
      syncProgress.visibility = View.VISIBLE
      topBanner.visibility = View.VISIBLE
      val animation = AnimationUtils.loadAnimation(topBanner.context, R.anim.fade_in)
      topBanner.startAnimation(animation)
    } else if (
      state is CurrentSyncJobStatus.Running && state.inProgressSyncJob is SyncJobStatus.InProgress
    ) {
      val inProgressState = state.inProgressSyncJob as? SyncJobStatus.InProgress
      val progress =
        inProgressState
          ?.let { it.completed.toDouble().div(it.total) }
          ?.let { if (it.isNaN()) 0.0 else it }
          ?.times(100)
          ?.roundToInt()
      "$progress% ${inProgressState?.syncOperation?.name?.lowercase()}ed"
        .also { syncPercent.text = it }
      syncProgress.progress = progress ?: 0
    }
  }

  private fun fadeOutTopBanner(state: CurrentSyncJobStatus) {
    fadeOutTopBanner(state::class.java.simpleName.uppercase())
  }

  private fun fadeOutTopBanner(state: LastSyncJobStatus) {
    fadeOutTopBanner(state::class.java.simpleName.uppercase())
  }

  private fun fadeOutTopBanner(statusText: String) {
    syncPercent.text = ""
    syncProgress.visibility = View.GONE
    if (topBanner.visibility == View.VISIBLE) {
      "${resources.getString(R.string.sync).uppercase()} $statusText".also { syncStatus.text = it }

      val animation = AnimationUtils.loadAnimation(topBanner.context, R.anim.fade_out)
      topBanner.startAnimation(animation)
      Handler(Looper.getMainLooper()).postDelayed({ topBanner.visibility = View.GONE }, 2000)
    }
  }
}
