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
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.SelectPatientListItemViewBinding
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel

class SelectPatientListItemRecyclerViewAdapter(
  private val onItemClicked: (SelectPatientListViewModel.SelectPatientListItem, Boolean) -> Unit,
) :
  ListAdapter<SelectPatientListViewModel.SelectPatientListItem, SelectPatientListItemViewHolder>(
    SelectPatientListItemDiffCallback(),
  ) {

  private var selectedPatientListIds: MutableSet<String> = mutableSetOf()

  class SelectPatientListItemDiffCallback :
    DiffUtil.ItemCallback<SelectPatientListViewModel.SelectPatientListItem>() {
    override fun areItemsTheSame(
      oldItem: SelectPatientListViewModel.SelectPatientListItem,
      newItem: SelectPatientListViewModel.SelectPatientListItem,
    ): Boolean = oldItem.resourceId == newItem.resourceId

    override fun areContentsTheSame(
      oldItem: SelectPatientListViewModel.SelectPatientListItem,
      newItem: SelectPatientListViewModel.SelectPatientListItem,
    ): Boolean = oldItem == newItem
  }

  fun isAnyPatientListItemSelected(): Boolean = selectedPatientListIds.isNotEmpty()

  fun addSelectPatientListItem(selectPatientListId: String) {
    selectedPatientListIds.add(selectPatientListId)
    notifyDataSetChanged()
  }

  fun removeSelectPatientListItem(selectPatientListId: String) {
    selectedPatientListIds.remove(selectPatientListId)
    notifyDataSetChanged()
  }

  fun selectPatientListItem(selectPatientListIds: Set<String>) {
    selectedPatientListIds.addAll(selectPatientListIds)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ): SelectPatientListItemViewHolder {
    val binding =
      SelectPatientListItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return SelectPatientListItemViewHolder(binding)
  }

  override fun onBindViewHolder(holder: SelectPatientListItemViewHolder, position: Int) {
    val item = getItem(position)
    val isSelected = selectedPatientListIds.contains(item.resourceId)
    holder.bindTo(item, onItemClicked, isSelected)
  }
}

class SelectPatientListItemViewHolder(private val binding: SelectPatientListItemViewBinding) :
  RecyclerView.ViewHolder(binding.root) {

  private val selectPatientListItemText: TextView = binding.selectPatientListItemText
  private val selectPatientListItemCheckbox: CheckBox = binding.selectPatientListItemCheckbox

  fun bindTo(
    selectPatientListItem: SelectPatientListViewModel.SelectPatientListItem,
    onItemClicked: (SelectPatientListViewModel.SelectPatientListItem, Boolean) -> Unit,
    isSelected: Boolean,
  ) {
    selectPatientListItemText.text = selectPatientListItem.name
    selectPatientListItemCheckbox.isChecked = isSelected
    selectPatientListItemCheckbox.setOnClickListener {
      onItemClicked(selectPatientListItem, isSelected)
    }

    itemView.setOnClickListener { onItemClicked(selectPatientListItem, isSelected) }
    itemView.setBackgroundResource(
      if (isSelected) R.drawable.selected_patient_list_item_background else 0,
    )
  }
}
