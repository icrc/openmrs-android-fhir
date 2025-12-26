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

import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.ui.components.EncounterListItemRow
import org.openmrs.android.fhir.ui.components.PatientDetailsHeaderRow
import org.openmrs.android.fhir.ui.components.PatientDetailsOverviewHeader
import org.openmrs.android.fhir.ui.components.PatientPropertyRow
import org.openmrs.android.fhir.ui.components.PatientUnsyncedCard
import org.openmrs.android.fhir.ui.components.VisitListItemRow
import org.openmrs.android.fhir.viewmodel.PatientDetailCondition
import org.openmrs.android.fhir.viewmodel.PatientDetailData
import org.openmrs.android.fhir.viewmodel.PatientDetailEncounter
import org.openmrs.android.fhir.viewmodel.PatientDetailHeader
import org.openmrs.android.fhir.viewmodel.PatientDetailObservation
import org.openmrs.android.fhir.viewmodel.PatientDetailOverview
import org.openmrs.android.fhir.viewmodel.PatientDetailProperty
import org.openmrs.android.fhir.viewmodel.PatientDetailVisit
import org.openmrs.android.fhir.viewmodel.PatientUnsynced

class PatientDetailsRecyclerViewAdapter(
  private val onEditEncounterClick: (String, String) -> Unit,
  private val onEditVisitClick: (String) -> Unit,
) :
  ListAdapter<PatientDetailData, PatientDetailItemViewHolder>(
    PatientDetailsVisitItemViewHolder.PatientDetailDiffUtil(),
  ) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientDetailItemViewHolder {
    return when (PatientDetailsVisitItemViewHolder.ViewTypes.from(viewType)) {
      PatientDetailsVisitItemViewHolder.ViewTypes.HEADER ->
        PatientDetailsHeaderItemViewHolder(
          createComposeView(parent),
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT ->
        PatientOverviewItemViewHolder(createComposeView(parent))
      PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_UNSYNCED ->
        PatientDetailsUnsyncedItemViewHolder(createComposeView(parent))
      PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_PROPERTY ->
        PatientPropertyItemViewHolder(createComposeView(parent))
      PatientDetailsVisitItemViewHolder.ViewTypes.OBSERVATION ->
        PatientDetailsObservationItemViewHolder(createComposeView(parent))
      PatientDetailsVisitItemViewHolder.ViewTypes.CONDITION ->
        PatientDetailsVisitItemViewHolder.PatientDetailsConditionItemViewHolder(
          createComposeView(parent),
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.ENCOUNTER ->
        PatientDetailsEncounterItemViewHolder(
          createComposeView(parent),
          onEditEncounterClick,
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.VISIT ->
        PatientDetailsVisitItemViewHolder(
          createComposeView(parent),
          onEditVisitClick,
        )
    }
  }

  override fun onBindViewHolder(holder: PatientDetailItemViewHolder, position: Int) {
    val model = getItem(position)
    holder.bind(model)
  }

  override fun getItemViewType(position: Int): Int {
    val item = getItem(position)
    return when (item) {
      is PatientDetailHeader -> PatientDetailsVisitItemViewHolder.ViewTypes.HEADER
      is PatientDetailOverview -> PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT
      is PatientDetailProperty -> PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_PROPERTY
      is PatientDetailObservation -> PatientDetailsVisitItemViewHolder.ViewTypes.OBSERVATION
      is PatientDetailCondition -> PatientDetailsVisitItemViewHolder.ViewTypes.CONDITION
      is PatientUnsynced -> PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_UNSYNCED
      is PatientDetailEncounter -> PatientDetailsVisitItemViewHolder.ViewTypes.ENCOUNTER
      is PatientDetailVisit -> PatientDetailsVisitItemViewHolder.ViewTypes.VISIT
      else -> {
        throw IllegalArgumentException("Undefined Item type")
      }
    }.ordinal
  }

  private fun createComposeView(parent: ViewGroup): ComposeView {
    return ComposeView(parent.context).apply {
      layoutParams =
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT,
        )
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
  }
}

abstract class PatientDetailItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
  abstract fun bind(data: PatientDetailData)
}

class PatientOverviewItemViewHolder(composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  private val overviewState = mutableStateOf<PatientDetailOverview?>(null)

  init {
    composeView.setContent {
      MaterialTheme {
        overviewState.value?.let { overview ->
          PatientDetailsOverviewHeader(
            name = overview.patient.name,
            identifiers = overview.patient.identifiers,
          )
        }
      }
    }
  }

  override fun bind(data: PatientDetailData) {
    overviewState.value = data as PatientDetailOverview
  }
}

class PatientPropertyItemViewHolder(composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  private val propertyState = mutableStateOf<PatientDetailProperty?>(null)

  init {
    composeView.setContent {
      MaterialTheme {
        propertyState.value?.let { property ->
          PatientPropertyRow(
            header = property.patientProperty.header,
            value = property.patientProperty.value,
            showSyncIcon = true,
          )
        }
      }
    }
  }

  override fun bind(data: PatientDetailData) {
    propertyState.value = data as PatientDetailProperty
  }
}

class PatientDetailsHeaderItemViewHolder(composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  private val headerState = mutableStateOf("")

  init {
    composeView.setContent { MaterialTheme { PatientDetailsHeaderRow(title = headerState.value) } }
  }

  override fun bind(data: PatientDetailData) {
    headerState.value = (data as PatientDetailHeader).header
  }
}

class PatientDetailsUnsyncedItemViewHolder(composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  init {
    composeView.setContent { MaterialTheme { PatientUnsyncedCard() } }
  }

  override fun bind(data: PatientDetailData) {
    // No-op: static card content.
  }
}

class PatientDetailsObservationItemViewHolder(composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  private val observationState = mutableStateOf<PatientDetailObservation?>(null)

  init {
    composeView.setContent {
      MaterialTheme {
        observationState.value?.let { observation ->
          PatientPropertyRow(
            header = observation.observation.code,
            value = observation.observation.value,
            showSyncIcon = true,
          )
        }
      }
    }
  }

  override fun bind(data: PatientDetailData) {
    observationState.value = data as PatientDetailObservation
  }
}

class PatientDetailsEncounterItemViewHolder(
  composeView: ComposeView,
  private val onEditEncounterClick: (String, String) -> Unit,
) : PatientDetailItemViewHolder(composeView) {
  private val encounterState = mutableStateOf<PatientDetailEncounter?>(null)

  init {
    composeView.setContent {
      MaterialTheme {
        encounterState.value?.let { encounter ->
          EncounterListItemRow(
            encounterType = encounter.encounter.type,
            encounterDate = encounter.encounter.dateTime,
            showSyncIcon = encounter.encounter.isSynced?.not() ?: true,
            onTitleClick = {
              val currentEncounter = encounterState.value?.encounter
              if (currentEncounter != null) {
                onEditEncounterClick(
                  currentEncounter.encounterId ?: "",
                  currentEncounter.encounterType ?: "",
                )
              }
            },
          )
        }
      }
    }
  }

  override fun bind(data: PatientDetailData) {
    encounterState.value = data as PatientDetailEncounter
  }
}

class PatientDetailsVisitItemViewHolder(
  composeView: ComposeView,
  private val onEditVisitClick: (String) -> Unit,
) : PatientDetailItemViewHolder(composeView) {
  private val visitState = mutableStateOf<PatientDetailVisit?>(null)

  init {
    composeView.setContent {
      MaterialTheme {
        visitState.value?.let { visit ->
          VisitListItemRow(
            encounterType = visit.visit.code,
            encounterDate = visit.visit.getPeriods(),
            onDateClick = { onEditVisitClick(visit.visit.id) },
          )
        }
      }
    }
  }

  override fun bind(data: PatientDetailData) {
    visitState.value = data as PatientDetailVisit
  }

  class PatientDetailsConditionItemViewHolder(composeView: ComposeView) :
    PatientDetailItemViewHolder(composeView) {
    private val conditionState = mutableStateOf<PatientDetailCondition?>(null)

    init {
      composeView.setContent {
        MaterialTheme {
          conditionState.value?.let { condition ->
            PatientPropertyRow(
              header = condition.condition.code,
              value = condition.condition.value,
              showSyncIcon = true,
            )
          }
        }
      }
    }

    override fun bind(data: PatientDetailData) {
      conditionState.value = data as PatientDetailCondition
    }
  }

  enum class ViewTypes {
    HEADER,
    PATIENT,
    PATIENT_UNSYNCED,
    PATIENT_PROPERTY,
    OBSERVATION,
    CONDITION,
    ENCOUNTER,
    VISIT,
    ;

    companion object {
      fun from(ordinal: Int): ViewTypes {
        return entries[ordinal]
      }
    }
  }

  class PatientDetailDiffUtil : DiffUtil.ItemCallback<PatientDetailData>() {
    override fun areItemsTheSame(o: PatientDetailData, n: PatientDetailData) = o == n

    override fun areContentsTheSame(o: PatientDetailData, n: PatientDetailData) =
      areItemsTheSame(o, n)
  }
}
