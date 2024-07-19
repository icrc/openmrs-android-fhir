package org.openmrs.android.fhir.adapters

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.databinding.IdentifierTypeItemViewBinding
import org.openmrs.android.fhir.R

class IdentifierTypeViewHolder(binding: IdentifierTypeItemViewBinding) :
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
        if(identifierTypeItem.required){
            selectedIcon.setImageResource(R.drawable.ic_check_decagram_green)
        } else {
            itemView.setOnClickListener { onItemClicked(identifierTypeItem, isSelected) }
        }

        selectedIdentifierTypeId = identifierTypeItem.uuid
    }
}
