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
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.SettingsPageBinding

class SettingsFragment : Fragment(R.layout.settings_page) {

  private var _binding: SettingsPageBinding? = null
  private val binding
    get() = _binding!!

  private lateinit var dataStore: DemoDataStore

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = SettingsPageBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setHasOptionsMenu(true)
    dataStore = DemoDataStore(requireContext())
    setupUI()
    observeNetworkConnectivity()
  }

  private fun setupUI() {
    setUpActionBar()
    (activity as? MainActivity)?.setDrawerEnabled(false)
    binding.btnCancelSettings.setOnClickListener {
      Toast.makeText(requireContext(), getString(R.string.settings_discarded), Toast.LENGTH_SHORT)
        .show()
      navigateUp()
    }
    binding.btnSaveSettings.setOnClickListener { saveSettings() }
    binding.checkNetworkSwitch.setOnCheckedChangeListener { _, isChecked ->
      lifecycleScope.launch { dataStore.setCheckNetworkConnectivity(isChecked) }
    }
  }

  private fun setUpActionBar() {
    (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
      title = getString(R.string.settings)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun observeNetworkConnectivity() {
    lifecycleScope.launch {
      dataStore.getCheckNetworkConnectivityFlow().collect { isConnected ->
        binding.checkNetworkSwitch.isChecked = isConnected
      }
    }
  }

  private fun saveSettings() {
    lifecycleScope
      .launch { dataStore.setCheckNetworkConnectivity(binding.checkNetworkSwitch.isChecked) }
      .invokeOnCompletion {
        Toast.makeText(requireContext(), getString(R.string.settings_saved), Toast.LENGTH_SHORT)
          .show()
        navigateUp()
      }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        navigateUp()
        true
      }
      else -> false
    }
  }

  private fun navigateUp() {
    NavHostFragment.findNavController(this).navigateUp()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
