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
package org.openmrs.android.fhir.data

import android.content.Context
import ca.uhn.fhir.context.FhirContext
import java.text.DateFormat
import java.util.Date
import org.hl7.fhir.r4.model.BooleanType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.InstantType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.Type
import org.openmrs.android.fhir.R

/**
 * A form built from what an encounter holds, for the encounters whose own form is not on the device
 * (it was never downloaded, or the server does not have it). Nothing about the questionnaire is
 * needed: the encounter's own facts and every observation are listed, labelled by their concept, in
 * the same read-only viewer the real forms use. Observations that group others (the members they
 * refer to) become sections.
 */
object EncounterFallbackForm {
  private const val ENCOUNTER_GROUP = "generic-encounter"
  private const val OBSERVATIONS_GROUP = "generic-observations"

  /** The questionnaire and the response to show, as JSON. */
  fun build(
    context: Context,
    encounter: Encounter,
    observations: List<Observation>,
  ): Pair<String, String> {
    val questionnaire =
      Questionnaire().apply {
        id = "encounter-without-form"
        status = Enumerations.PublicationStatus.ACTIVE
      }
    val response =
      QuestionnaireResponse().apply {
        status = QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED
      }

    questionnaire.addItem(
      Questionnaire.QuestionnaireItemComponent().apply {
        linkId = "generic-note"
        type = Questionnaire.QuestionnaireItemType.DISPLAY
        text = context.getString(R.string.encounter_generic_note)
      },
    )

    val facts =
      listOf(
        R.string.encounter_generic_type to encounter.typeText(),
        R.string.encounter_generic_date to encounter.period?.start?.let { dateTime(it) },
        R.string.encounter_generic_location to encounter.location.firstOrNull()?.location?.display,
        R.string.encounter_generic_provider to
          encounter.participant.firstOrNull()?.individual?.display,
        R.string.encounter_generic_status to
          encounter.status?.takeIf { it != Encounter.EncounterStatus.UNKNOWN }?.display,
      )
    val shownFacts = facts.filter { !it.second.isNullOrBlank() }
    if (shownFacts.isNotEmpty()) {
      val group = group(ENCOUNTER_GROUP, context.getString(R.string.encounter_generic_title))
      val answers = responseGroup(ENCOUNTER_GROUP)
      shownFacts.forEachIndexed { index, (label, value) ->
        addAnswer(group, answers, "generic-fact-$index", context.getString(label), value!!)
      }
      questionnaire.addItem(group)
      response.addItem(answers)
    }

    val byId = observations.associateBy { it.idElement.idPart.orEmpty() }
    val members =
      observations
        .flatMap { if (it.hasHasMember()) it.hasMember else emptyList() }
        .mapNotNull { it.reference?.substringAfterLast('/') }
        .toSet()
    val roots = observations.filter { it.idElement.idPart.orEmpty() !in members }

    val group =
      group(OBSERVATIONS_GROUP, context.getString(R.string.encounter_generic_observations))
    val answers = responseGroup(OBSERVATIONS_GROUP)
    val seen = mutableSetOf<String>()
    roots.forEach { addObservation(context, it, byId, group, answers, seen) }
    if (group.hasItem()) {
      questionnaire.addItem(group)
      response.addItem(answers)
    }

    val parser = FhirContext.forR4Cached().newJsonParser()
    return parser.encodeResourceToString(questionnaire) to parser.encodeResourceToString(response)
  }

  private fun addObservation(
    context: Context,
    observation: Observation,
    byId: Map<String, Observation>,
    parent: Questionnaire.QuestionnaireItemComponent,
    parentAnswers: QuestionnaireResponse.QuestionnaireResponseItemComponent,
    seen: MutableSet<String>,
  ) {
    val id = observation.idElement.idPart.orEmpty().ifBlank { observation.hashCode().toString() }
    if (!seen.add(id)) return
    val label = observation.label(context)
    val children =
      if (observation.hasHasMember()) {
        observation.hasMember.mapNotNull { byId[it.reference?.substringAfterLast('/')] }
      } else {
        emptyList()
      }
    val value = observation.value?.let { display(context, it) }
    if (children.isNotEmpty()) {
      val section = group("generic-obs-$id", label)
      val answers = responseGroup("generic-obs-$id")
      if (!value.isNullOrBlank()) addAnswer(section, answers, "generic-value-$id", label, value)
      children.forEach { addObservation(context, it, byId, section, answers, seen) }
      parent.addItem(section)
      parentAnswers.addItem(answers)
    } else if (!value.isNullOrBlank()) {
      addAnswer(parent, parentAnswers, "generic-obs-$id", label, value)
    }
  }

  private fun group(linkId: String, text: String) =
    Questionnaire.QuestionnaireItemComponent().apply {
      this.linkId = linkId
      this.text = text
      type = Questionnaire.QuestionnaireItemType.GROUP
    }

  private fun responseGroup(linkId: String) =
    QuestionnaireResponse.QuestionnaireResponseItemComponent().apply { this.linkId = linkId }

  /** A read-only text question under [parent] and its answer under [parentAnswers]. */
  private fun addAnswer(
    parent: Questionnaire.QuestionnaireItemComponent,
    parentAnswers: QuestionnaireResponse.QuestionnaireResponseItemComponent,
    linkId: String,
    label: String,
    value: String,
  ) {
    parent.addItem(
      Questionnaire.QuestionnaireItemComponent().apply {
        this.linkId = linkId
        text = label
        type = Questionnaire.QuestionnaireItemType.STRING
        readOnly = true
      },
    )
    parentAnswers.addItem(
      QuestionnaireResponse.QuestionnaireResponseItemComponent().apply {
        this.linkId = linkId
        addAnswer().value = StringType(value)
      },
    )
  }

  private fun Observation.label(context: Context): String =
    code?.coding?.firstOrNull { !it.display.isNullOrBlank() }?.display
      ?: code?.text?.takeIf { it.isNotBlank() } ?: code?.coding?.firstOrNull()?.code
        ?: context.getString(R.string.encounter_generic_unnamed)

  private fun Encounter.typeText(): String? =
    type.firstOrNull()?.let {
      it.text ?: it.coding.firstOrNull()?.display ?: it.coding.firstOrNull()?.code
    }

  /** The value as a person reads it: the concept's name, the number with its unit, a date... */
  private fun display(context: Context, value: Type): String? =
    when (value) {
      is CodeableConcept -> value.coding.firstOrNull { !it.display.isNullOrBlank() }?.display
          ?: value.text ?: value.coding.firstOrNull()?.code
      is Coding -> value.display ?: value.code
      is Quantity ->
        listOfNotNull(value.value?.stripTrailingZeros()?.toPlainString(), value.unit ?: value.code)
          .joinToString(" ")
      is BooleanType -> context.getString(if (value.value == true) R.string.yes else R.string.no)
      is DateTimeType -> value.value?.let { dateTime(it) }
      is InstantType -> value.value?.let { dateTime(it) }
      is DateType -> value.value?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) }
      else -> value.primitiveValue()
    }

  private fun dateTime(date: Date): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
}
