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
        isSelected: Boolean
    ) {
        locationNameView.text = locationItem.name
        favoriteStarIcon.setImageResource(
            if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outlined
        )
        favoriteStarIcon.setOnClickListener { onItemClicked(locationItem, true) }
        itemView.setOnClickListener { onItemClicked(locationItem, false) }

        itemView.setBackgroundResource(if (isSelected) R.drawable.selected_location_background else 0)

        selectedLocationId = locationItem.resourceId
    }
}
