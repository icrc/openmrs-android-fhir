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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import javax.inject.Inject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.SyncSessionsAdapter
import org.openmrs.android.fhir.databinding.FragmentSyncInfoBinding
import org.openmrs.android.fhir.viewmodel.SyncInfoViewModel

class SyncInfoFragment : Fragment() {

  private var _binding: FragmentSyncInfoBinding? = null
  private val binding
    get() = _binding!!

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<SyncInfoViewModel> { viewModelFactory }

  private lateinit var adapter: SyncSessionsAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentSyncInfoBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.sync_info)
      setDisplayHomeAsUpEnabled(true)
    }
    setupRecyclerView()
    observeViewModel()

    binding.btnClearAllSyncData.setOnClickListener { viewModel.clearAllSyncSessions() }
    (activity as MainActivity).setDrawerEnabled(false)
  }

  private fun setupRecyclerView() {
    adapter = SyncSessionsAdapter { session -> viewModel.deleteSyncSession(session) }

    binding.recyclerViewSyncSessions.apply {
      layoutManager = LinearLayoutManager(requireContext())
      adapter = this@SyncInfoFragment.adapter
    }
  }

  private fun observeViewModel() {
    // Observe the live data for the sync sessions
    viewModel.syncSessions.observe(
      viewLifecycleOwner,
      Observer { sessions ->
        if (sessions.isNullOrEmpty()) {
          binding.recyclerViewSyncSessions.isVisible = false
          binding.emptyStateContainer.isVisible = true
        } else {
          binding.recyclerViewSyncSessions.isVisible = true
          binding.emptyStateContainer.isVisible = false
        }
        adapter.submitList(sessions)
      },
    )
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this).navigateUp()
        true
      }
      else -> false
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
