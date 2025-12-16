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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.PatientDetailsHeaderBinding
import org.openmrs.android.fhir.ui.components.EncounterListItemRow
import org.openmrs.android.fhir.ui.components.PatientDetailsHeaderRow
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
  private val onCreateEncountersClick: () -> Unit,
  private val onEditEncounterClick: (String, String, String) -> Unit,
  private val onEditVisitClick: (String) -> Unit,
) :
  ListAdapter<PatientDetailData, PatientDetailItemViewHolder>(
    PatientDetailsVisitItemViewHolder.PatientDetailDiffUtil(),
  ) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientDetailItemViewHolder {
    return when (PatientDetailsVisitItemViewHolder.ViewTypes.from(viewType)) {
      PatientDetailsVisitItemViewHolder.ViewTypes.HEADER ->
        PatientDetailsHeaderItemViewHolder(
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          },
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT ->
        PatientOverviewItemViewHolder(
          PatientDetailsHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
          onCreateEncountersClick,
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_UNSYNCED ->
        PatientDetailsUnsyncedItemViewHolder(
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          },
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_PROPERTY ->
        PatientPropertyItemViewHolder(
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          },
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.OBSERVATION ->
        PatientDetailsObservationItemViewHolder(
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          },
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.CONDITION ->
        PatientDetailsVisitItemViewHolder.PatientDetailsConditionItemViewHolder(
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          },
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.ENCOUNTER ->
        PatientDetailsEncounterItemViewHolder(
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          },
          onEditEncounterClick,
        )
      PatientDetailsVisitItemViewHolder.ViewTypes.VISIT ->
        PatientDetailsVisitItemViewHolder(
          ComposeView(parent.context).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
              )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          },
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
}

abstract class PatientDetailItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
  abstract fun bind(data: PatientDetailData)
}

class PatientOverviewItemViewHolder(
  private val binding: PatientDetailsHeaderBinding,
  val onCreateEncountersClick: () -> Unit,
) : PatientDetailItemViewHolder(binding.root) {
  override fun bind(data: PatientDetailData) {
    (data as PatientDetailOverview).let {
      binding.title.text = it.patient.name
      binding.identifiersContainer.removeAllViews()
      it.patient.identifiers.forEach { identifier ->
        if (!identifier.type?.text.equals("unsynced")) {
          val textView =
            TextView(binding.root.context).apply {
              layoutParams =
                LinearLayout.LayoutParams(
                  LinearLayout.LayoutParams.WRAP_CONTENT,
                  LinearLayout.LayoutParams.WRAP_CONTENT,
                )
              textSize = 16f
              typeface = ResourcesCompat.getFont(context, R.font.inter)
              text = "${identifier.type.text}: ${identifier.value}"
            }
          binding.identifiersContainer.addView(textView)
        }
      }
    }
  }
}

class PatientPropertyItemViewHolder(private val composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  override fun bind(data: PatientDetailData) {
    val property = data as PatientDetailProperty
    composeView.setContent {
      MaterialTheme {
        PatientPropertyRow(
          header = property.patientProperty.header,
          value = property.patientProperty.value,
          showSyncIcon = true,
        )
      }
    }
  }
}

class PatientDetailsHeaderItemViewHolder(private val composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  override fun bind(data: PatientDetailData) {
    val header = (data as PatientDetailHeader).header
    composeView.setContent { MaterialTheme { PatientDetailsHeaderRow(title = header) } }
  }
}

class PatientDetailsUnsyncedItemViewHolder(private val composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  override fun bind(data: PatientDetailData) {
    composeView.setContent { MaterialTheme { PatientUnsyncedCard() } }
  }
}

class PatientDetailsObservationItemViewHolder(private val composeView: ComposeView) :
  PatientDetailItemViewHolder(composeView) {
  override fun bind(data: PatientDetailData) {
    val observation = (data as PatientDetailObservation).observation
    composeView.setContent {
      MaterialTheme {
        PatientPropertyRow(
          header = observation.code,
          value = observation.value,
          showSyncIcon = true,
        )
      }
    }
  }
}

class PatientDetailsEncounterItemViewHolder(
  private val composeView: ComposeView,
  private val onEditEncounterClick: (String, String, String) -> Unit,
) : PatientDetailItemViewHolder(composeView) {
  override fun bind(data: PatientDetailData) {
    val encounter = (data as PatientDetailEncounter).encounter
    composeView.setContent {
      MaterialTheme {
        EncounterListItemRow(
          encounterType = encounter.type,
          encounterDate = encounter.dateTime,
          showSyncIcon = encounter.isSynced?.not() ?: true,
          onTitleClick = {
            onEditEncounterClick(
              encounter.encounterId ?: "",
              encounter.formDisplay ?: "",
              encounter.encounterType ?: "",
            )
          },
        )
      }
    }
  }
}

class PatientDetailsVisitItemViewHolder(
  private val composeView: ComposeView,
  private val onEditVisitClick: (String) -> Unit,
) : PatientDetailItemViewHolder(composeView) {

  override fun bind(data: PatientDetailData) {
    val visit = (data as PatientDetailVisit).visit
    composeView.setContent {
      MaterialTheme {
        VisitListItemRow(
          encounterType = visit.code,
          encounterDate = visit.getPeriods(),
          onDateClick = { onEditVisitClick(visit.id) },
        )
      }
    }
  }

  class PatientDetailsConditionItemViewHolder(private val composeView: ComposeView) :
    PatientDetailItemViewHolder(composeView) {
    override fun bind(data: PatientDetailData) {
      val condition = (data as PatientDetailCondition).condition
      composeView.setContent {
        MaterialTheme {
          PatientPropertyRow(header = condition.code, value = condition.value, showSyncIcon = true)
        }
      }
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
        return values()[ordinal]
      }
    }
  }

  class PatientDetailDiffUtil : DiffUtil.ItemCallback<PatientDetailData>() {
    override fun areItemsTheSame(o: PatientDetailData, n: PatientDetailData) = o == n

    override fun areContentsTheSame(o: PatientDetailData, n: PatientDetailData) =
      areItemsTheSame(o, n)
  }
}
