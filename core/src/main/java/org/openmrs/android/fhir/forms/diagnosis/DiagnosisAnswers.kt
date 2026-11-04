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
package org.openmrs.android.fhir.forms.diagnosis

import android.content.Context
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.IntegerType
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.codesystems.ConditionCategory
import org.openmrs.android.fhir.Constants

/** Item control of the question that picks diagnoses from a list (see [DiagnosisPickerFactory]). */
const val DIAGNOSIS_PICKER_CONTROL = "diagnosis-picker"

/** Names the list a picker offers (`valueString`: the id of a CSV, see [DiagnosisCatalogs]). */
const val DIAGNOSIS_CATALOG_EXTENSION_URL = "http://fhir.openmrs.org/ext/diagnosis-catalog"

/** The rank of a diagnosis: the server refuses a diagnosis without it. */
const val DIAGNOSIS_RANK_EXTENSION_URL =
  "http://fhir.openmrs.org/R4/StructureDefinition/diagnosis-rank"

/**
 * Fixes the rank of what a picker holds: `primary` or `secondary` (`valueCode`), so a form can have
 * one question for the primary diagnosis and one for the secondary ones. A picker without it takes
 * the rank from the order: the first diagnosis ticked is the primary one.
 */
const val DIAGNOSIS_PICKER_RANK_EXTENSION_URL = "http://fhir.openmrs.org/ext/diagnosis-picker-rank"

/** The main diagnosis of an encounter. */
const val DIAGNOSIS_RANK_PRIMARY = 1

/** Any other diagnosis of an encounter. */
const val DIAGNOSIS_RANK_SECONDARY = 2

private const val ITEM_CONTROL_URL_END = "/questionnaire-itemControl"
private const val VERIFICATION_STATUS_SYSTEM =
  "http://terminology.hl7.org/CodeSystem/condition-ver-status"

/** A question the user answers by searching a list of diagnoses and ticking several. */
fun Questionnaire.QuestionnaireItemComponent.isDiagnosisPicker(): Boolean =
  extension.any { extension ->
    extension.url.endsWith(ITEM_CONTROL_URL_END) &&
      (extension.value as? CodeableConcept)?.coding?.any { it.code == DIAGNOSIS_PICKER_CONTROL } ==
        true
  }

/** The id of the list this picker offers. */
fun Questionnaire.QuestionnaireItemComponent.diagnosisCatalogId(): String =
  extension
    .firstOrNull { it.url == DIAGNOSIS_CATALOG_EXTENSION_URL }
    ?.value
    ?.primitiveValue()
    ?.takeIf { it.isNotBlank() }
    ?: DiagnosisCatalogs.DEFAULT_ID

/** The rank this picker fixes ([DIAGNOSIS_RANK_PRIMARY] or [DIAGNOSIS_RANK_SECONDARY]), if any. */
fun Questionnaire.QuestionnaireItemComponent.diagnosisRank(): Int? =
  when (
    extension
      .firstOrNull { it.url == DIAGNOSIS_PICKER_RANK_EXTENSION_URL }
      ?.value
      ?.primitiveValue()
      ?.lowercase(Locale.ROOT)
  ) {
    "primary" -> DIAGNOSIS_RANK_PRIMARY
    "secondary" -> DIAGNOSIS_RANK_SECONDARY
    else -> null
  }

/** Every diagnosis picker of the form, wherever it is in it. */
fun Questionnaire.diagnosisPickers(): List<Questionnaire.QuestionnaireItemComponent> {
  val found = mutableListOf<Questionnaire.QuestionnaireItemComponent>()

  fun walk(items: List<Questionnaire.QuestionnaireItemComponent>) {
    items.forEach {
      if (it.isDiagnosisPicker()) found.add(it)
      walk(it.item)
    }
  }
  walk(item)
  return found
}

private fun QuestionnaireResponse.itemsWithLinkId(
  linkId: String,
): List<QuestionnaireResponse.QuestionnaireResponseItemComponent> {
  val found = mutableListOf<QuestionnaireResponse.QuestionnaireResponseItemComponent>()

  fun walk(items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>) {
    items.forEach { item ->
      if (item.linkId == linkId) found.add(item)
      walk(item.item)
      item.answer.forEach { walk(it.item) }
    }
  }
  walk(item)
  return found
}

/** A diagnosis ticked in a picker, and the rank that picker fixes (null: the order decides). */
class PickedDiagnosis(val coding: Coding, val fixedRank: Int?)

/**
 * What the user ticked in every picker of the form, once each: the primary question first, then the
 * ones with no rank, then the secondary ones, each in the order they were ticked. A diagnosis
 * ticked in two questions counts in the first.
 */
private fun Questionnaire.picks(response: QuestionnaireResponse): List<PickedDiagnosis> =
  diagnosisPickers()
    .sortedBy {
      when (it.diagnosisRank()) {
        DIAGNOSIS_RANK_PRIMARY -> 0
        null -> 1
        else -> 2
      }
    }
    .flatMap { picker ->
      response
        .itemsWithLinkId(picker.linkId)
        .flatMap { it.answer }
        .mapNotNull { it.value as? Coding }
        .filter { it.hasCode() }
        .map { PickedDiagnosis(it, picker.diagnosisRank()) }
    }
    .distinctBy { it.coding.code.lowercase(Locale.ROOT) }

/** What the user ticked in every picker of the form, once each (see [picks]). */
fun Questionnaire.pickedDiagnoses(response: QuestionnaireResponse): List<Coding> =
  picks(response).map { it.coding }

/** True when a question of the form takes the primary diagnosis, so the order decides nothing. */
private fun Questionnaire.hasPrimaryPicker(): Boolean =
  diagnosisPickers().any { it.diagnosisRank() == DIAGNOSIS_RANK_PRIMARY }

/** The category every diagnosis of a form has: an encounter diagnosis, not a problem-list item. */
fun encounterDiagnosisCategory(): CodeableConcept =
  CodeableConcept().apply {
    addCoding(
      Coding().apply {
        system = Constants.CONDITION_CATEGORY_SYSTEM_URL
        code = ConditionCategory.ENCOUNTERDIAGNOSIS.toCode()
        display = ConditionCategory.ENCOUNTERDIAGNOSIS.display
      },
    )
  }

/**
 * The coding of an answer for [diagnosis]: its concept uuid, named in the language of the user when
 * the list has the name in it.
 */
fun Diagnosis.toAnswerCoding(languageTag: String = ""): Coding =
  Coding(null, uuid, if (languageTag.isEmpty()) name else nameIn(languageTag))

/**
 * A diagnosis of the patient made in the encounter, the way the server's Condition resource takes
 * it: the concept by its uuid (a coding with no system), the category, a provisional certainty and
 * the [rank] ([DIAGNOSIS_RANK_PRIMARY] or [DIAGNOSIS_RANK_SECONDARY]).
 */
fun newDiagnosisCondition(
  picked: Coding,
  diagnosis: Diagnosis?,
  patient: Reference,
  encounter: Reference,
  rank: Int,
  recorded: Date = Date(),
): Condition =
  Condition().apply {
    id = UUID.randomUUID().toString()
    subject = patient
    this.encounter = encounter
    category = listOf(encounterDiagnosisCategory())
    verificationStatus =
      CodeableConcept().apply {
        addCoding(Coding(VERIFICATION_STATUS_SYSTEM, "provisional", "Provisional"))
      }
    val name = diagnosis?.name ?: picked.display
    code =
      CodeableConcept().apply {
        addCoding(Coding(null, diagnosis?.uuid ?: picked.code, name))
        text = name
      }
    addExtension(DIAGNOSIS_RANK_EXTENSION_URL, IntegerType(rank))
    recordedDateElement = DateTimeType(recorded)
  }

/** The lists the pickers of the form offer, looked up by id. */
suspend fun Questionnaire.diagnosisCatalogs(context: Context): Map<String, DiagnosisCatalog> =
  diagnosisPickers()
    .map { it.diagnosisCatalogId() }
    .distinct()
    .mapNotNull { id -> DiagnosisCatalogs.get(context, id)?.let { id to it } }
    .toMap()

private fun Map<String, DiagnosisCatalog>.find(uuid: String): Diagnosis? =
  values.firstNotNullOfOrNull { it.find(uuid) }

/**
 * One diagnosis per tick, ready to be saved with the encounter. The rank is the one the question
 * fixes; in a question that fixes none, the first diagnosis is the primary (unless another question
 * takes the primary diagnosis).
 */
suspend fun Questionnaire.diagnosisConditions(
  context: Context,
  response: QuestionnaireResponse,
  patient: Reference,
  encounter: Reference,
): List<Condition> {
  val picks = picks(response)
  if (picks.isEmpty()) return emptyList()
  RecentDiagnoses.record(context, picks.map { it.coding.code })
  val catalogs = diagnosisCatalogs(context)
  var primaryOpen = !hasPrimaryPicker()
  return picks.map { pick ->
    val rank =
      pick.fixedRank ?: if (primaryOpen) DIAGNOSIS_RANK_PRIMARY else DIAGNOSIS_RANK_SECONDARY
    if (pick.fixedRank == null) primaryOpen = false
    newDiagnosisCondition(pick.coding, catalogs.find(pick.coding.code), patient, encounter, rank)
  }
}

/** The concept uuid of a diagnosis: its coding with no system (the others are other systems'). */
private fun Condition.conceptUuid(): String? =
  code.coding.firstOrNull { it.hasCode() && !it.hasSystem() }?.code

private fun Condition.rank(): Int? =
  (getExtensionByUrl(DIAGNOSIS_RANK_EXTENSION_URL)?.value as? IntegerType)?.value

/**
 * The encounter's diagnoses a diagnosis picker manages: its encounter diagnoses that name a
 * concept. Its other conditions, and a diagnosis written as free text, are not touched.
 */
fun List<Condition>.encounterDiagnoses(): List<Condition> = filter { condition ->
  condition.category.any { category ->
    category.coding.any { it.code == ConditionCategory.ENCOUNTERDIAGNOSIS.toCode() }
  } && condition.conceptUuid() != null
}

/**
 * Fills the pickers of an encounter being edited with the diagnoses it already has: a question for
 * the primary diagnosis gets the primary one, a question for the secondary ones the others, and one
 * with no rank all of them, the primary one first.
 */
fun Questionnaire.populateDiagnosisAnswers(
  response: QuestionnaireResponse,
  conditions: List<Condition>,
  catalogs: Map<String, DiagnosisCatalog>,
) {
  val diagnoses = conditions.encounterDiagnoses().sortedBy { it.rank() ?: DIAGNOSIS_RANK_SECONDARY }
  diagnosisPickers().forEach { picker ->
    val codings =
      diagnoses
        .filter { condition ->
          val primary = condition.rank() == DIAGNOSIS_RANK_PRIMARY
          when (picker.diagnosisRank()) {
            DIAGNOSIS_RANK_PRIMARY -> primary
            DIAGNOSIS_RANK_SECONDARY -> !primary
            else -> true
          }
        }
        .mapNotNull { condition ->
          val uuid = condition.conceptUuid() ?: return@mapNotNull null
          catalogs.find(uuid)?.toAnswerCoding()
            ?: Coding(null, uuid, condition.code.text ?: condition.code.codingFirstRep.display)
        }
        .distinctBy { it.code.lowercase(Locale.ROOT) }
    if (codings.isEmpty()) return@forEach
    response.itemsWithLinkId(picker.linkId).firstOrNull()?.let { item ->
      item.answer.clear()
      codings.forEach { item.addAnswer().value = it }
    }
  }
}

/**
 * What saving an edited encounter does to its diagnoses. A diagnosis moved to the other question is
 * in both lists (the old condition goes, a new one with the new rank comes). [fixedRanks] is the
 * rank of the added ones whose question fixes it (by lower-case code); [firstIsPrimary] is true
 * when no question takes the primary diagnosis and none of those that stay is the primary one, so
 * the first one added without a fixed rank takes that place.
 */
class DiagnosisChanges(
  val toAdd: List<Coding>,
  val toRemove: List<Condition>,
  val firstIsPrimary: Boolean,
  val fixedRanks: Map<String, Int> = emptyMap(),
) {
  fun newConditions(
    catalogs: Map<String, DiagnosisCatalog>,
    patient: Reference,
    encounter: Reference,
  ): List<Condition> =
    mutableListOf<Condition>().also { conditions ->
      var primaryOpen = firstIsPrimary
      toAdd.forEach { coding ->
        val fixed = fixedRanks[coding.code.lowercase(Locale.ROOT)]
        val rank = fixed ?: if (primaryOpen) DIAGNOSIS_RANK_PRIMARY else DIAGNOSIS_RANK_SECONDARY
        if (fixed == null) primaryOpen = false
        conditions.add(
          newDiagnosisCondition(coding, catalogs.find(coding.code), patient, encounter, rank),
        )
      }
    }
}

/**
 * The difference between what the pickers hold now and the diagnoses the encounter has. A form with
 * a picker owns the diagnoses of its encounter; its other conditions are not touched.
 */
fun Questionnaire.diagnosisChanges(
  response: QuestionnaireResponse,
  existing: List<Condition>,
): DiagnosisChanges {
  val picks = picks(response)
  val pickedUuids = picks.map { it.coding.code.lowercase(Locale.ROOT) }.toSet()
  val fixedRanks =
    picks
      .mapNotNull { pick -> pick.fixedRank?.let { pick.coding.code.lowercase(Locale.ROOT) to it } }
      .toMap()
  // A diagnosis stays when it is still ticked, in a question of the rank it has.
  val (kept, removed) =
    existing.encounterDiagnoses().partition { condition ->
      val uuid = condition.conceptUuid()?.lowercase(Locale.ROOT)
      val fixed = fixedRanks[uuid]
      val sameRank =
        fixed == null ||
          (fixed == DIAGNOSIS_RANK_PRIMARY) == (condition.rank() == DIAGNOSIS_RANK_PRIMARY)
      uuid in pickedUuids && sameRank
    }
  val keptUuids = kept.mapNotNull { it.conceptUuid()?.lowercase(Locale.ROOT) }.toSet()
  return DiagnosisChanges(
    toAdd = picks.map { it.coding }.filter { it.code.lowercase(Locale.ROOT) !in keptUuids },
    toRemove = removed,
    firstIsPrimary = !hasPrimaryPicker() && kept.none { it.rank() == DIAGNOSIS_RANK_PRIMARY },
    fixedRanks = fixedRanks,
  )
}
