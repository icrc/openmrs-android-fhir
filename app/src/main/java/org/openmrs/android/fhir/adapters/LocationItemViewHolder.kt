package org.openmrs.android.fhir.adapters

import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.LocationListItemViewBinding
import org.openmrs.android.fhir.viewmodel.LocationViewModel

class LocationItemViewHolder(binding: LocationListItemViewBinding) :
    RecyclerView.ViewHolder(binding.root){
        private val locationNameView: TextView = binding.locationText
        private val favoriteStarIcon: ImageView = binding.locationFavoriteStarIcon
        fun bindTo(
            locationItem: LocationViewModel.LocationItem,
            onItemClicked: (LocationViewModel.LocationItem, Boolean) -> Unit,
            isFavorite: Boolean
        ) {
            this.locationNameView.text = locationItem.name
            this.favoriteStarIcon.setOnClickListener { onItemClicked(locationItem, true) }
            this.itemView.setOnClickListener{ onItemClicked(locationItem, false)}
            if (isFavorite){
                favoriteStarIcon.setImageResource(R.drawable.ic_star_filled)
            }
        }
}