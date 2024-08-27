package org.openmrs.android.fhir.viewmodel

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.ItemEncounterBinding

class EncounterItemViewHolder(private val binding: ItemEncounterBinding) :
    RecyclerView.ViewHolder(binding.root) {
    private val encounterTypeTextView: TextView = binding.encounterType
    private val encounterDateTextView: TextView = binding.encounterDate

    fun bindTo(encounterItem: PatientListViewModel.EncounterItem) {
        this.encounterTypeTextView.text =
            itemView.resources.getString(
                R.string.observation_brief_text,
                encounterItem.encounterId,
                encounterItem.type,
                encounterItem.dateTime
            )
        this.encounterDateTextView.text =
            itemView.resources.getString(
                R.string.observation_brief_text,
                encounterItem.encounterId,
                encounterItem.type,
                encounterItem.dateTime
            )
    }
}
