/*
 * Copyright 2022-2024 Google LLC
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
        if(
          context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME) != null
        ) {
          findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToAddPatientFragment())
        } else {
          Toast.makeText(context, "Please select a location first", Toast.LENGTH_LONG).show()
        }
      }

    }
    requireView().findViewById<CardView>(R.id.item_patient_list).setOnClickListener {
      findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToPatientList())
    }
    requireView().findViewById<CardView>(R.id.item_search).setOnClickListener {
      findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToPatientList())
    }
    requireView().findViewById<CardView>(R.id.select_location).setOnClickListener {
      findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToLocationFragment())
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
