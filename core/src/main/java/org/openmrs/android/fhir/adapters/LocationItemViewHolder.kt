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

import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.LocationListItemViewBinding
import org.openmrs.android.fhir.viewmodel.LocationViewModel

class LocationItemViewHolder(private val binding: LocationListItemViewBinding) :
  RecyclerView.ViewHolder(binding.root) {

  private val locationNameView: TextView = binding.locationText
  private val favoriteStarIcon: ImageView = binding.locationFavoriteStarIcon
  private var selectedLocationId: String? = null

  fun bindTo(
    locationItem: LocationViewModel.LocationItem,
    onItemClicked: (LocationViewModel.LocationItem, Boolean) -> Unit,
    isFavorite: Boolean,
    isSelected: Boolean,
  ) {
    locationNameView.text = locationItem.name
    favoriteStarIcon.setImageResource(
      if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outlined,
    )
    favoriteStarIcon.setOnClickListener { onItemClicked(locationItem, true) }
    itemView.setOnClickListener { onItemClicked(locationItem, false) }

    itemView.setBackgroundResource(if (isSelected) R.drawable.selected_item_background else 0)

    selectedLocationId = locationItem.resourceId
  }
}
