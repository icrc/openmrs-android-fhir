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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentPatientListBinding
import org.openmrs.android.fhir.ui.components.PatientListContainerScreen
import org.openmrs.android.fhir.ui.components.PatientListItemRow
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import timber.log.Timber

class PatientListFragment : Fragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val patientListViewModel by viewModels<PatientListViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }
  private lateinit var patientQuery: String
  private var _binding: FragmentPatientListBinding? = null
  private val binding
    get() = _binding!!

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
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    binding.patientListContainer.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
    )
    binding.patientListContainer.setContent {
      MaterialTheme {
        val patients = patientListViewModel.liveSearchedPatients.observeAsState(emptyList()).value
        val isLoading by patientListViewModel.isLoadingFlow.collectAsState(false)
        val isRefreshing by patientListViewModel.isRefreshingFlow.collectAsState(false)
        Timber.d("Submitting ${patients.count()} patient records")
        PatientListContainerScreen(
          patients = patients,
          isLoading = isLoading,
          isRefreshing = isRefreshing,
          onRefresh = { patientListViewModel.refreshPatients() },
          emptyContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Spacer(modifier = Modifier.height(40.dp))
              Image(
                modifier = Modifier.size(80.dp),
                painter = painterResource(id = R.drawable.ic_home_new_patient),
                contentDescription = null,
                colorFilter =
                  ColorFilter.tint(
                    colorResource(id = R.color.dashboard_cardview_textcolor),
                  ),
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text =
                  stringResource(
                    id = R.string.no_patients_available_register_a_new_one_using_the_button_below,
                  ),
                color = colorResource(id = R.color.black),
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
              )
            }
          },
        ) { patient ->
          PatientListItemRow(
            name = patient.name,
            ageGenderLabel = getFormattedAge(patient) + "," + patient.gender[0].uppercase(),
            isSynced = patient.isSynced,
            onClick = { onPatientItemClicked(patient) },
          )
        }
      }
    }

    patientQuery = binding.patientInputEditText.text.toString()
    addSearchTextChangeListener()

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

    binding.apply { addPatient.setOnClickListener { onAddPatientClick() } }
    setHasOptionsMenu(true)
    mainActivityViewModel.setDrawerEnabled(false)
  }

  private fun addSearchTextChangeListener() {
    binding.patientInputEditText.doOnTextChanged { newText, _, _, _ ->
      patientListViewModel.searchPatientsByName(newText.toString().trim())
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
      if (
        context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME) !=
          null
      ) {
        findNavController()
          .navigate(PatientListFragmentDirections.actionPatientListToAddPatientFragment())
      } else {
        Toast.makeText(
            context,
            getString(R.string.please_select_a_location_first),
            Toast.LENGTH_LONG,
          )
          .show()
      }
    }
  }

  private fun getFormattedAge(
    patientItem: PatientListViewModel.PatientItem,
  ): String {
    if (patientItem.dob == null) return ""
    return Period.between(patientItem.dob, LocalDate.now()).let {
      when {
        it.years > 0 -> it.years.toString()
        it.months > 0 -> it.months.toString() + " months"
        else -> it.days.toString() + " months"
      }
    }
  }
}
