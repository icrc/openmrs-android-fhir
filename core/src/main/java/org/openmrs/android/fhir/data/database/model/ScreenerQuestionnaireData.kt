package org.openmrs.android.fhir.data.database.model

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ScreenerQuestionnaireData(
    val specificQuestions: List<FormQuestionData>,
)

@JsonClass(generateAdapter = true)
data class FormQuestionData(
    val forms: List<String>,
    val questionId: String,
)