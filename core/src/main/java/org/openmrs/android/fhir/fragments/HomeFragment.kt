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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.extensions.getServerConnectivityState

class HomeFragment : Fragment(R.layout.fragment_home) {

  @Inject lateinit var apiManager: ApiManager

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = resources.getString(R.string.app_name)
      setDisplayHomeAsUpEnabled(true)
    }
    setHasOptionsMenu(true)
    (activity as MainActivity).setDrawerEnabled(true)
    setOnClicks()
  }

  private fun setOnClicks() {
    requireView().findViewById<CardView>(R.id.card_new_patient).setOnClickListener {
      lifecycleScope.launch {
        if (
          context
            ?.applicationContext
            ?.dataStore
            ?.data
            ?.first()
            ?.get(PreferenceKeys.LOCATION_NAME) != null
        ) {
          findNavController()
            .navigate(HomeFragmentDirections.actionHomeFragmentToAddPatientFragment())
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

    requireView().findViewById<CardView>(R.id.card_patient_list).setOnClickListener {
      lifecycleScope.launch {
        if (
          context
            ?.applicationContext
            ?.dataStore
            ?.data
            ?.first()
            ?.get(PreferenceKeys.LOCATION_NAME) != null
        ) {
          findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToPatientList())
        } else {
          Toast.makeText(context, R.string.please_select_a_location_first, Toast.LENGTH_LONG).show()
        }
      }
    }

    requireView().findViewById<CardView>(R.id.card_custom_patient_list).setOnClickListener {
      viewLifecycleOwner.lifecycleScope.launch {
        when (requireContext().getServerConnectivityState(apiManager)) {
          ServerConnectivityState.ServerConnected ->
            findNavController()
              .navigate(
                HomeFragmentDirections.actionHomeFragmentToSelectPatientListFragment(false),
              )
          ServerConnectivityState.InternetOnly ->
            Toast.makeText(
                context,
                getString(R.string.server_unreachable_try_again_message),
                Toast.LENGTH_LONG,
              )
              .show()
          ServerConnectivityState.Offline ->
            Toast.makeText(
                context,
                getString(R.string.connect_internet_to_select_patient_list),
                Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }

    requireView().findViewById<CardView>(R.id.card_group_encounter).setOnClickListener {
      findNavController()
        .navigate(
          HomeFragmentDirections.actionHomeFragmentToCreateEncounterFragment(
            patientId = "",
            isGroupEncounter = true,
          ),
        )
    }
    requireView().findViewById<CardView>(R.id.card_sync_info).setOnClickListener {
      findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToSyncInfoFragment())
    }
    requireView().findViewById<CardView>(R.id.card_unsynced_resources).setOnClickListener {
      findNavController()
        .navigate(HomeFragmentDirections.actionHomeFragmentToUnsyncedResourcesFragment())
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        (requireActivity() as MainActivity).openNavigationDrawer()
        true
      }
      else -> false
    }
  }
}
