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
package org.openmrs.android.fhir.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.databinding.ItemPatientSelectableBinding
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class PatientSelectionAdapter(
  private val onSelectionChanged: () -> Unit,
) :
  ListAdapter<PatientListViewModel.PatientItem, PatientSelectionAdapter.PatientViewHolder>(
    PatientDiffCallback(),
  ) {

  private val selectedPatientIds = mutableSetOf<String>()

  fun getSelectedPatientIds(): List<String> {
    return selectedPatientIds.toList()
  }

  fun selectAll(patients: List<PatientListViewModel.PatientItem>) {
    val changed = patients.any { selectedPatientIds.add(it.resourceId) }
    if (changed || selectedPatientIds.size != patients.size) {
      selectedPatientIds.clear()
      patients.forEach { selectedPatientIds.add(it.resourceId) }
      notifyDataSetChanged()
      onSelectionChanged()
    }
  }

  fun deselectAll() {
    if (selectedPatientIds.isNotEmpty()) {
      selectedPatientIds.clear()
      notifyDataSetChanged()
      onSelectionChanged()
    }
  }

  fun isAllSelected(currentPatientList: List<PatientListViewModel.PatientItem>): Boolean {
    if (currentPatientList.isEmpty()) return false
    return currentPatientList.all { selectedPatientIds.contains(it.resourceId) }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
    val binding =
      ItemPatientSelectableBinding.inflate(
        LayoutInflater.from(parent.context),
        parent,
        false,
      )
    return PatientViewHolder(binding)
  }

  override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
    val patient = getItem(position)
    holder.bind(patient, selectedPatientIds.contains(patient.resourceId))
  }

  inner class PatientViewHolder(private val binding: ItemPatientSelectableBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(patient: PatientListViewModel.PatientItem, isSelected: Boolean) {
      binding.textviewPatientName.text = patient.name
      binding.checkboxPatientSelect.isChecked = isSelected

      binding.root.setOnClickListener {
        // Toggle selection
        val currentlyChecked = binding.checkboxPatientSelect.isChecked
        if (!currentlyChecked) { // If it's about to be checked
          selectedPatientIds.add(patient.resourceId)
        } else { // If it's about to be unchecked
          selectedPatientIds.remove(patient.resourceId)
        }
        binding.checkboxPatientSelect.isChecked = !currentlyChecked // Update UI
        onSelectionChanged() // Notify fragment
      }
      // Prevent CheckBox from consuming click if root handles it
      binding.checkboxPatientSelect.isClickable = false
    }
  }
}

class PatientDiffCallback : DiffUtil.ItemCallback<PatientListViewModel.PatientItem>() {
  override fun areItemsTheSame(
    oldItem: PatientListViewModel.PatientItem,
    newItem: PatientListViewModel.PatientItem,
  ): Boolean {
    return oldItem.resourceId == newItem.resourceId
  }

  override fun areContentsTheSame(
    oldItem: PatientListViewModel.PatientItem,
    newItem: PatientListViewModel.PatientItem,
  ): Boolean {
    return oldItem == newItem
  }
}
