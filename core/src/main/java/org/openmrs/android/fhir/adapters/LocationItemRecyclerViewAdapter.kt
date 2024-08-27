package org.openmrs.android.fhir.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.openmrs.android.fhir.databinding.LocationListItemViewBinding
import org.openmrs.android.fhir.viewmodel.LocationViewModel

class LocationItemRecyclerViewAdapter(
    private val onItemClicked: (LocationViewModel.LocationItem, Boolean) -> Unit,
    private val isFavorite: Boolean
) : ListAdapter<LocationViewModel.LocationItem, LocationItemViewHolder>(LocationItemDiffCallback()) {

    private var selectedLocationId: String? = null

    class LocationItemDiffCallback : DiffUtil.ItemCallback<LocationViewModel.LocationItem>() {
        override fun areItemsTheSame(
            oldItem: LocationViewModel.LocationItem,
            newItem: LocationViewModel.LocationItem
        ): Boolean = oldItem.resourceId == newItem.resourceId

        override fun areContentsTheSame(
            oldItem: LocationViewModel.LocationItem,
            newItem: LocationViewModel.LocationItem
        ): Boolean = oldItem == newItem
    }

    fun setSelectedLocation(locationId: String?) {
        selectedLocationId = locationId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationItemViewHolder {
        val binding = LocationListItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LocationItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationItemViewHolder, position: Int) {
        val item = getItem(position)
        val isSelected = item.resourceId == selectedLocationId
        holder.bindTo(item, onItemClicked, isFavorite, isSelected)
    }
}
