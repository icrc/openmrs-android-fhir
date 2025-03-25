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
import org.openmrs.android.fhir.databinding.LocationListItemViewBinding
import org.openmrs.android.fhir.viewmodel.LocationViewModel

class LocationItemRecyclerViewAdapter(
  private val onItemClicked: (LocationViewModel.LocationItem, Boolean) -> Unit,
  private val isFavorite: Boolean,
) :
  ListAdapter<LocationViewModel.LocationItem, LocationItemViewHolder>(LocationItemDiffCallback()) {

  private var selectedLocationId: String? = null

  class LocationItemDiffCallback : DiffUtil.ItemCallback<LocationViewModel.LocationItem>() {
    override fun areItemsTheSame(
      oldItem: LocationViewModel.LocationItem,
      newItem: LocationViewModel.LocationItem,
    ): Boolean = oldItem.resourceId == newItem.resourceId

    override fun areContentsTheSame(
      oldItem: LocationViewModel.LocationItem,
      newItem: LocationViewModel.LocationItem,
    ): Boolean = oldItem == newItem
  }

  fun setSelectedLocation(locationId: String?) {
    selectedLocationId = locationId
    notifyDataSetChanged()
  }

  fun isLocationSelected(): Boolean {
    return selectedLocationId != null
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationItemViewHolder {
    val binding =
      LocationListItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return LocationItemViewHolder(binding)
  }

  override fun onBindViewHolder(holder: LocationItemViewHolder, position: Int) {
    val item = getItem(position)
    val isSelected = item.resourceId == selectedLocationId
    holder.bindTo(item, onItemClicked, isFavorite, isSelected)
  }
}
