package org.openmrs.android.fhir.viewmodel

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.EncounterListItemBinding

class EncounterItemViewHolder(private val binding: EncounterListItemBinding) :
  RecyclerView.ViewHolder(binding.root) {
  private val encounterTextView: TextView = binding.encounterDetail

  fun bindTo(encounterItem: PatientListViewModel.EncounterItem) {
    this.encounterTextView.text =
      itemView.resources.getString(
        R.string.observation_brief_text,
              encounterItem.id,
              encounterItem.type,
              encounterItem.dateTime
      )
  }
}
