package org.openmrs.android.fhir.adapters

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.databinding.IdentifierTypeItemViewBinding

class IdentifierTypeViewHolder(private val binding: IdentifierTypeItemViewBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val identifierTypeNameView: TextView = binding.identifierText
    private val selectedIcon: ImageView = binding.selectedIcon
    private var selectedIdentifierTypeId: String? = null

    fun bindTo(
        identifierTypeItem: IdentifierType,
        onItemClicked: (IdentifierType, Boolean) -> Unit,
        isSelected: Boolean
    ) {
        identifierTypeNameView.text = identifierTypeItem.display ?: "unknown"
        if(!isSelected) {
            selectedIcon.visibility = View.GONE
        } else {
            selectedIcon.visibility = View.VISIBLE
        }
        itemView.setOnClickListener { onItemClicked(identifierTypeItem, isSelected) }

        selectedIdentifierTypeId = identifierTypeItem.uuid
    }
}
