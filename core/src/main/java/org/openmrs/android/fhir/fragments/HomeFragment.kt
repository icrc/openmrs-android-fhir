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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

class HomeFragment : Fragment(R.layout.fragment_home) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = resources.getString(R.string.app_name)
      setDisplayHomeAsUpEnabled(true)
    }
    setHasOptionsMenu(true)
    (activity as MainActivity).setDrawerEnabled(true)
    setOnClicks()
  }

  private fun setOnClicks() {
    requireView().findViewById<CardView>(R.id.item_new_patient).setOnClickListener {
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
          Toast.makeText(context, "Please select a location first", Toast.LENGTH_LONG).show()
        }
      }
    }
    requireView().findViewById<CardView>(R.id.item_patient_list).setOnClickListener {
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
          Toast.makeText(context, "Please select a location first", Toast.LENGTH_LONG).show()
        }
      }
    }
    requireView().findViewById<CardView>(R.id.select_location).setOnClickListener {
      findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToLocationFragment())
    }
    requireView().findViewById<CardView>(R.id.select_identifier).setOnClickListener {
      findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToIdentifierFragment())
    }
    requireView().findViewById<CardView>(R.id.sync_info).setOnClickListener {
      findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToSyncInfoFragment())
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
