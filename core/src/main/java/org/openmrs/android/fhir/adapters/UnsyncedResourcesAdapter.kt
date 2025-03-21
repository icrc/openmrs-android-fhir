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
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.UnsyncedEncounter
import org.openmrs.android.fhir.data.database.model.UnsyncedObservation
import org.openmrs.android.fhir.data.database.model.UnsyncedPatient
import org.openmrs.android.fhir.data.database.model.UnsyncedResource

class UnsyncedResourcesAdapter(
  private val onTogglePatientExpand: (String) -> Unit,
  private val onToggleEncounterExpand: (String) -> Unit,
  private val onDelete: (UnsyncedResource) -> Unit,
  private val onDownload: (UnsyncedResource) -> Unit,
) : ListAdapter<UnsyncedResource, RecyclerView.ViewHolder>(UnsyncedResourceDiffCallback()) {

  companion object {
    private const val VIEW_TYPE_PATIENT = 0
    private const val VIEW_TYPE_ENCOUNTER = 1
    private const val VIEW_TYPE_OBSERVATION = 2
  }

  override fun getItemViewType(position: Int): Int {
    return when (getItem(position)) {
      is UnsyncedResource.PatientItem -> VIEW_TYPE_PATIENT
      is UnsyncedResource.EncounterItem -> VIEW_TYPE_ENCOUNTER
      is UnsyncedResource.ObservationItem -> VIEW_TYPE_OBSERVATION
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    return when (viewType) {
      VIEW_TYPE_PATIENT -> {
        val view =
          LayoutInflater.from(parent.context)
            .inflate(R.layout.unsynced_patient_list_item_view, parent, false)
        PatientViewHolder(view, onTogglePatientExpand, onDelete, onDownload)
      }
      VIEW_TYPE_ENCOUNTER -> {
        val view =
          LayoutInflater.from(parent.context)
            .inflate(R.layout.unsynced_encounter_list_item_view, parent, false)
        EncounterViewHolder(view, onToggleEncounterExpand, onDelete, onDownload)
      }
      VIEW_TYPE_OBSERVATION -> {
        val view =
          LayoutInflater.from(parent.context)
            .inflate(R.layout.unsynced_observation_list_item_view, parent, false)
        ObservationViewHolder(view, onDelete, onDownload)
      }
      else -> throw IllegalArgumentException("Invalid view type")
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val item = getItem(position)) {
      is UnsyncedResource.PatientItem -> (holder as PatientViewHolder).bind(item.patient)
      is UnsyncedResource.EncounterItem -> (holder as EncounterViewHolder).bind(item.encounter)
      is UnsyncedResource.ObservationItem ->
        (holder as ObservationViewHolder).bind(item.observation)
    }
  }

  class PatientViewHolder(
    itemView: View,
    private val onToggleExpand: (String) -> Unit,
    private val onDelete: (UnsyncedResource) -> Unit,
    private val onDownload: (UnsyncedResource) -> Unit,
  ) : RecyclerView.ViewHolder(itemView) {
    private val tvPatientName: TextView = itemView.findViewById(R.id.tvPatientName)
    private val ivExpandCollapse: ImageView = itemView.findViewById(R.id.ivExpandCollapse)
    private val btnDeletePatient: ImageButton = itemView.findViewById(R.id.btnDeletePatient)
    private val btnDownloadPatient: ImageButton = itemView.findViewById(R.id.btnDownloadPatient)

    fun bind(patient: UnsyncedPatient) {
      tvPatientName.text = patient.name

      // Set expand/collapse icon
      ivExpandCollapse.visibility =
        if (patient.encounters.isNotEmpty()) View.VISIBLE else View.INVISIBLE

      // Set click listeners
      ivExpandCollapse.setOnClickListener { onToggleExpand(patient.logicalId) }

      if (patient.isSynced) {
        btnDeletePatient.setImageResource(R.drawable.ic_check_decagram_green)
      } else {
        btnDeletePatient.setOnClickListener { onDelete(UnsyncedResource.PatientItem(patient)) }
      }

      btnDownloadPatient.setOnClickListener { onDownload(UnsyncedResource.PatientItem(patient)) }
    }
  }

  class EncounterViewHolder(
    itemView: View,
    private val onToggleExpand: (String) -> Unit,
    private val onDelete: (UnsyncedResource) -> Unit,
    private val onDownload: (UnsyncedResource) -> Unit,
  ) : RecyclerView.ViewHolder(itemView) {
    private val tvEncounterTitle: TextView = itemView.findViewById(R.id.tvEncounterTitle)
    private val ivExpandCollapse: ImageView = itemView.findViewById(R.id.ivExpandCollapse)
    private val btnDeleteEncounter: ImageButton = itemView.findViewById(R.id.btnDeleteEncounter)
    private val btnDownloadEncounter: ImageButton = itemView.findViewById(R.id.btnDownloadEncounter)

    fun bind(encounter: UnsyncedEncounter) {
      tvEncounterTitle.text = encounter.title

      ivExpandCollapse.visibility =
        if (encounter.observations.isNotEmpty()) View.VISIBLE else View.INVISIBLE

      // Set visibility based on whether there are observations
      ivExpandCollapse.visibility =
        if (encounter.observations.isNotEmpty()) View.VISIBLE else View.INVISIBLE

      // Set click listeners
      ivExpandCollapse.setOnClickListener {
        if (encounter.observations.isNotEmpty()) {
          onToggleExpand(encounter.logicalId)
        }
      }

      if (encounter.isSynced) {
        btnDeleteEncounter.setImageResource(R.drawable.ic_check_decagram_green)
      } else {
        btnDeleteEncounter.setOnClickListener {
          onDelete(UnsyncedResource.EncounterItem(encounter))
        }
      }

      btnDownloadEncounter.setOnClickListener {
        onDownload(UnsyncedResource.EncounterItem(encounter))
      }
    }
  }

  class ObservationViewHolder(
    itemView: View,
    private val onDelete: (UnsyncedResource) -> Unit,
    private val onDownload: (UnsyncedResource) -> Unit,
  ) : RecyclerView.ViewHolder(itemView) {
    private val tvObservationTitle: TextView = itemView.findViewById(R.id.tvObservationTitle)
    private val btnDeleteObservation: ImageButton = itemView.findViewById(R.id.btnDeleteObservation)
    private val btnDownloadObservation: ImageButton =
      itemView.findViewById(R.id.btnDownloadObservation)

    fun bind(observation: UnsyncedObservation) {
      tvObservationTitle.text = observation.title

      if (observation.isSynced) {
        btnDeleteObservation.setImageResource(R.drawable.ic_check_decagram_green)
      } else {
        btnDeleteObservation.setOnClickListener {
          onDelete(UnsyncedResource.ObservationItem(observation))
        }
      }

      btnDownloadObservation.setOnClickListener {
        onDownload(UnsyncedResource.ObservationItem(observation))
      }
    }
  }

  class UnsyncedResourceDiffCallback : DiffUtil.ItemCallback<UnsyncedResource>() {
    override fun areItemsTheSame(oldItem: UnsyncedResource, newItem: UnsyncedResource): Boolean {
      return when {
        oldItem is UnsyncedResource.PatientItem && newItem is UnsyncedResource.PatientItem ->
          oldItem.patient.logicalId == newItem.patient.logicalId
        oldItem is UnsyncedResource.EncounterItem && newItem is UnsyncedResource.EncounterItem ->
          oldItem.encounter.logicalId == newItem.encounter.logicalId
        oldItem is UnsyncedResource.ObservationItem &&
          newItem is UnsyncedResource.ObservationItem ->
          oldItem.observation.logicalId == newItem.observation.logicalId
        else -> false
      }
    }

    override fun areContentsTheSame(oldItem: UnsyncedResource, newItem: UnsyncedResource): Boolean {
      return when {
        oldItem is UnsyncedResource.PatientItem && newItem is UnsyncedResource.PatientItem ->
          oldItem.patient == newItem.patient
        oldItem is UnsyncedResource.EncounterItem && newItem is UnsyncedResource.EncounterItem ->
          oldItem.encounter == newItem.encounter
        oldItem is UnsyncedResource.ObservationItem &&
          newItem is UnsyncedResource.ObservationItem -> oldItem.observation == newItem.observation
        else -> false
      }
    }
  }
}
