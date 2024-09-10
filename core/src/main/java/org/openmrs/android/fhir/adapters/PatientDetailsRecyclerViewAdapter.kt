/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.android.fhir.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.databinding.PatientDetailsCardViewBinding
import org.openmrs.android.fhir.databinding.PatientDetailsHeaderBinding
import org.openmrs.android.fhir.databinding.PatientDetailsUnsyncedBinding
import org.openmrs.android.fhir.databinding.PatientPropertyItemViewBinding
import org.openmrs.android.fhir.viewmodel.PatientDetailCondition
import org.openmrs.android.fhir.viewmodel.PatientDetailData
import org.openmrs.android.fhir.viewmodel.PatientDetailHeader
import org.openmrs.android.fhir.viewmodel.PatientDetailObservation
import org.openmrs.android.fhir.viewmodel.PatientDetailOverview
import org.openmrs.android.fhir.viewmodel.PatientDetailProperty
import org.openmrs.android.fhir.viewmodel.PatientUnsynced
import org.openmrs.android.fhir.databinding.VisitListItemBinding
import org.openmrs.android.fhir.viewmodel.PatientDetailEncounter
import org.openmrs.android.fhir.viewmodel.PatientDetailVisit

class PatientDetailsRecyclerViewAdapter(
    private val onCreateEncountersClick: () -> Unit,
    private val onEditEncounterClick: (String, String, String) -> Unit,
    private val onEditVisitClick: (String) -> Unit,
) : ListAdapter<PatientDetailData, PatientDetailItemViewHolder>(PatientDetailsVisitItemViewHolder.PatientDetailDiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientDetailItemViewHolder {
        return when (PatientDetailsVisitItemViewHolder.ViewTypes.from(viewType)) {
            PatientDetailsVisitItemViewHolder.ViewTypes.HEADER ->
                PatientDetailsHeaderItemViewHolder(
                    PatientDetailsCardViewBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                )

            PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT ->
                PatientOverviewItemViewHolder(
                    PatientDetailsHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    onCreateEncountersClick
                )

            PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_UNSYNCED ->
                PatientDetailsUnsyncedItemViewHolder(
                    PatientDetailsUnsyncedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                )

            PatientDetailsVisitItemViewHolder.ViewTypes.PATIENT_PROPERTY ->
                PatientPropertyItemViewHolder(
                    PatientPropertyItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                )

            PatientDetailsVisitItemViewHolder.ViewTypes.OBSERVATION ->
                PatientDetailsObservationItemViewHolder(
                    PatientPropertyItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                )

            PatientDetailsVisitItemViewHolder.ViewTypes.CONDITION ->
                PatientDetailsVisitItemViewHolder.PatientDetailsConditionItemViewHolder(
                    PatientPropertyItemViewBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ),
                )

            PatientDetailsVisitItemViewHolder.ViewTypes.ENCOUNTER ->
                PatientDetailsEncounterItemViewHolder(
                    PatientPropertyItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    onEditEncounterClick
                )

            PatientDetailsVisitItemViewHolder.ViewTypes.VISIT ->
                PatientDetailsVisitItemViewHolder(
                    VisitListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    onEditVisitClick
                )
        }
    }

    override fun onBindViewHolder(holder: PatientDetailItemViewHolder, position: Int) {
        val model = getItem(position)
        holder.bind(model)
        if (holder is PatientDetailsHeaderItemViewHolder) return
        if (holder is PatientDetailsEncounterItemViewHolder) {
            holder.bind(getItem(position) as PatientDetailEncounter)
        }
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

    companion object {
        private const val STROKE_WIDTH = 2f
        private const val CORNER_RADIUS = 10f

        @ColorInt
        private const val FILL_COLOR = Color.TRANSPARENT

        @ColorInt
        private const val STROKE_COLOR = Color.GRAY

        fun allCornersRounded(): MaterialShapeDrawable {
            return MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setAllCornerSizes(CORNER_RADIUS)
                    .setAllCorners(RoundedCornerTreatment())
                    .build(),
            ).applyStrokeColor()
        }

        fun topCornersRounded(): MaterialShapeDrawable {
            return MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setTopLeftCornerSize(CORNER_RADIUS)
                    .setTopRightCornerSize(CORNER_RADIUS)
                    .setTopLeftCorner(RoundedCornerTreatment())
                    .setTopRightCorner(RoundedCornerTreatment())
                    .build(),
            ).applyStrokeColor()
        }

        fun bottomCornersRounded(): MaterialShapeDrawable {
            return MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setBottomLeftCornerSize(CORNER_RADIUS)
                    .setBottomRightCornerSize(CORNER_RADIUS)
                    .setBottomLeftCorner(RoundedCornerTreatment())
                    .setBottomRightCorner(RoundedCornerTreatment())
                    .build(),
            ).applyStrokeColor()
        }

        fun noCornersRounded(): MaterialShapeDrawable {
            return MaterialShapeDrawable(ShapeAppearanceModel.builder().build()).applyStrokeColor()
        }

        private fun MaterialShapeDrawable.applyStrokeColor(): MaterialShapeDrawable {
            strokeWidth = STROKE_WIDTH
            fillColor = ColorStateList.valueOf(FILL_COLOR)
            strokeColor = ColorStateList.valueOf(STROKE_COLOR)
            return this
        }
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
                    val textView = TextView(binding.root.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
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

class PatientPropertyItemViewHolder(private val binding: PatientPropertyItemViewBinding) :
    PatientDetailItemViewHolder(binding.root) {
    override fun bind(data: PatientDetailData) {
        (data as PatientDetailProperty).let {
            binding.name.text = it.patientProperty.header
            binding.fieldName.text = it.patientProperty.value
        }
    }
}

class PatientDetailsHeaderItemViewHolder(private val binding: PatientDetailsCardViewBinding) :
    PatientDetailItemViewHolder(binding.root) {
    override fun bind(data: PatientDetailData) {
        (data as PatientDetailHeader).let { binding.header.text = it.header }
    }
}

class PatientDetailsUnsyncedItemViewHolder(private val binding: PatientDetailsUnsyncedBinding) :
    PatientDetailItemViewHolder(binding.root) {
    override fun bind(data: PatientDetailData) {
    }
}

class PatientDetailsObservationItemViewHolder(private val binding: PatientPropertyItemViewBinding) :
    PatientDetailItemViewHolder(binding.root) {
    override fun bind(data: PatientDetailData) {
        (data as PatientDetailObservation).let {
            binding.name.text = it.observation.code
            binding.fieldName.text = it.observation.value
        }
    }
}

class PatientDetailsEncounterItemViewHolder(
    private val binding: PatientPropertyItemViewBinding,
    private val onEditEncounterClick: (String, String, String) -> Unit
) : PatientDetailItemViewHolder(binding.root) {
    override fun bind(data: PatientDetailData) {
        (data as PatientDetailEncounter).let {
            val encounter = it.encounter;
            binding.name.text = encounter.type
            binding.fieldName.text = encounter.dateTime
            binding.name.setOnClickListener {
                onEditEncounterClick(
                    encounter.encounterId ?: "",
                    encounter.formDisplay ?: "",
                    encounter.formResource ?: ""
                )
            }
        }
    }
}

class PatientDetailsVisitItemViewHolder(
    private val binding: VisitListItemBinding, // Update to the correct binding class
    private val onEditVisitClick: (String) -> Unit
) : PatientDetailItemViewHolder(binding.root) {

    override fun bind(data: PatientDetailData) {
        (data as PatientDetailVisit).let {
            val visit = it.visit
            binding.encounterType.text = visit.code
            binding.encounterDate.text = visit.getPeriods()
            binding.encounterDate.setOnClickListener {
                onEditVisitClick(visit.id)
            }
        }
    }


    class PatientDetailsConditionItemViewHolder(private val binding: PatientPropertyItemViewBinding) :
        PatientDetailItemViewHolder(binding.root) {
        override fun bind(data: PatientDetailData) {
            (data as PatientDetailCondition).let {
                binding.name.text = it.condition.code
                binding.fieldName.text = it.condition.value
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
        VISIT;

        companion object {
            fun from(ordinal: Int): ViewTypes {
                return values()[ordinal]
            }
        }
    }

    class PatientDetailDiffUtil : DiffUtil.ItemCallback<PatientDetailData>() {
        override fun areItemsTheSame(o: PatientDetailData, n: PatientDetailData) = o == n

        override fun areContentsTheSame(o: PatientDetailData, n: PatientDetailData) = areItemsTheSame(o, n)
    }
}
