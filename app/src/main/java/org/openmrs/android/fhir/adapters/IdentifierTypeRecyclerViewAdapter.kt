package org.openmrs.android.fhir.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.databinding.IdentifierTypeItemViewBinding

class IdentifierTypeRecyclerViewAdapter(
    private val onItemClicked: (IdentifierType, Boolean) -> Unit,
    private val selectedIdentifierId: MutableSet<String>
) : ListAdapter<IdentifierType, IdentifierTypeViewHolder>(IdentifierTypeDiffCallback()) {

    class IdentifierTypeDiffCallback : DiffUtil.ItemCallback<IdentifierType>() {
        override fun areItemsTheSame(
            oldItem: IdentifierType,
            newItem: IdentifierType
        ): Boolean = oldItem.uuid == newItem.uuid

        override fun areContentsTheSame(
            oldItem: IdentifierType,
            newItem: IdentifierType
        ): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IdentifierTypeViewHolder {
        val binding = IdentifierTypeItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IdentifierTypeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IdentifierTypeViewHolder, position: Int) {
        val item = getItem(position)
        val isSelected = selectedIdentifierId.contains(item.uuid)
        holder.bindTo(item, onItemClicked, isSelected)
    }
}
