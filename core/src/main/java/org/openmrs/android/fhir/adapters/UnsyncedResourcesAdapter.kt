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

import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.data.database.model.UnsyncedEncounter
import org.openmrs.android.fhir.data.database.model.UnsyncedObservation
import org.openmrs.android.fhir.data.database.model.UnsyncedPatient
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.ui.components.UnsyncedEncounterRow
import org.openmrs.android.fhir.ui.components.UnsyncedObservationRow
import org.openmrs.android.fhir.ui.components.UnsyncedPatientRow

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
        val composeView =
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          }
        PatientViewHolder(composeView, onTogglePatientExpand, onDelete, onDownload)
      }
      VIEW_TYPE_ENCOUNTER -> {
        val composeView =
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          }
        EncounterViewHolder(composeView, onToggleEncounterExpand, onDelete, onDownload)
      }
      VIEW_TYPE_OBSERVATION -> {
        val composeView =
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          }
        ObservationViewHolder(composeView, onDelete, onDownload)
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
    private val composeView: ComposeView,
    private val onToggleExpand: (String) -> Unit,
    private val onDelete: (UnsyncedResource) -> Unit,
    private val onDownload: (UnsyncedResource) -> Unit,
  ) : RecyclerView.ViewHolder(composeView) {

    fun bind(patient: UnsyncedPatient) {
      composeView.setContent {
        MaterialTheme {
          UnsyncedPatientRow(
            name = patient.name,
            onToggleExpand = {
              if (patient.encounters.isNotEmpty()) onToggleExpand(patient.logicalId)
            },
            onDownload = { onDownload(UnsyncedResource.PatientItem(patient)) },
            onDelete = { onDelete(UnsyncedResource.PatientItem(patient)) },
            showExpand = patient.encounters.isNotEmpty(),
            isSynced = patient.isSynced,
          )
        }
      }
    }
  }

  class EncounterViewHolder(
    private val composeView: ComposeView,
    private val onToggleExpand: (String) -> Unit,
    private val onDelete: (UnsyncedResource) -> Unit,
    private val onDownload: (UnsyncedResource) -> Unit,
  ) : RecyclerView.ViewHolder(composeView) {

    fun bind(encounter: UnsyncedEncounter) {
      composeView.setContent {
        MaterialTheme {
          UnsyncedEncounterRow(
            title = encounter.title,
            hasObservations = encounter.observations.isNotEmpty(),
            onToggleExpand = {
              if (encounter.observations.isNotEmpty()) {
                onToggleExpand(encounter.logicalId)
              }
            },
            onDownload = { onDownload(UnsyncedResource.EncounterItem(encounter)) },
            onDelete = { onDelete(UnsyncedResource.EncounterItem(encounter)) },
            isSynced = encounter.isSynced,
          )
        }
      }
    }
  }

  class ObservationViewHolder(
    private val composeView: ComposeView,
    private val onDelete: (UnsyncedResource) -> Unit,
    private val onDownload: (UnsyncedResource) -> Unit,
  ) : RecyclerView.ViewHolder(composeView) {

    fun bind(observation: UnsyncedObservation) {
      composeView.setContent {
        MaterialTheme {
          UnsyncedObservationRow(
            title = observation.title,
            onDownload = { onDownload(UnsyncedResource.ObservationItem(observation)) },
            onDelete = { onDelete(UnsyncedResource.ObservationItem(observation)) },
            isSynced = observation.isSynced,
          )
        }
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
