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

import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.IntegerType
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.junit.Test

class DiagnosisAnswersTest {
  private val patient = Reference("Patient/p1")
  private val encounter = Reference("Encounter/e1")
  private val catalog =
    DiagnosisCsv.parse(
      StringReader(
        """
        uuid,name:en,name:es,code
        116125AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Plasmodium falciparum malaria,Paludismo por P. falciparum,B50.9
        10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Acute bronchitis,Bronquitis aguda,J20.9
        d17f710c-645f-4b72-9606-2a8414841a19,Custom diagnosis,Diagnóstico propio,Z99
        """
          .trimIndent(),
      ),
    )
  private val catalogs = mapOf(DiagnosisCatalogs.DEFAULT_ID to catalog)
  private val malaria = catalog.find("116125AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")!!
  private val bronchitis = catalog.find("10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")!!
  private val custom = catalog.find("d17f710c-645f-4b72-9606-2a8414841a19")!!

  private fun picker(linkId: String = "diagnoses", rank: String? = null) =
    Questionnaire.QuestionnaireItemComponent().apply {
      this.linkId = linkId
      type = Questionnaire.QuestionnaireItemType.CHOICE
      repeats = rank != "primary"
      if (rank != null) addExtension(DIAGNOSIS_PICKER_RANK_EXTENSION_URL, CodeType(rank))
      addExtension()
        .setUrl("http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl")
        .setValue(
          CodeableConcept()
            .addCoding(Coding("http://example.org/item-control", DIAGNOSIS_PICKER_CONTROL, null)),
        )
    }

  /** The picker sits inside a page and a section, as in a real form. */
  private fun form(picker: Questionnaire.QuestionnaireItemComponent = picker()) =
    Questionnaire().apply {
      addItem().apply {
        linkId = "page"
        addItem().apply {
          linkId = "section"
          addItem(picker)
        }
      }
    }

  /** A form with a question for the primary diagnosis and one for the secondary ones. */
  private fun rankedForm() =
    Questionnaire().apply {
      addItem().apply {
        linkId = "page"
        addItem(picker("primary", rank = "primary"))
        addItem(picker("secondary", rank = "secondary"))
      }
    }

  private fun answeredRanked(primary: List<Diagnosis>, secondary: List<Diagnosis>) =
    QuestionnaireResponse().apply {
      addItem().apply {
        linkId = "page"
        addItem().apply {
          linkId = "primary"
          primary.forEach { addAnswer().value = it.toAnswerCoding() }
        }
        addItem().apply {
          linkId = "secondary"
          secondary.forEach { addAnswer().value = it.toAnswerCoding() }
        }
      }
    }

  private fun answered(vararg picked: Diagnosis) =
    QuestionnaireResponse().apply {
      addItem().apply {
        linkId = "page"
        addItem().apply {
          linkId = "section"
          addItem().apply {
            linkId = "diagnoses"
            picked.forEach { addAnswer().value = it.toAnswerCoding() }
          }
        }
      }
    }

  private fun condition(diagnosis: Diagnosis, rank: Int = DIAGNOSIS_RANK_SECONDARY) =
    newDiagnosisCondition(diagnosis.toAnswerCoding(), diagnosis, patient, encounter, rank)

  private fun Condition.rank() =
    (getExtensionByUrl(DIAGNOSIS_RANK_EXTENSION_URL).value as IntegerType).value

  private fun Condition.uuid() = code.codingFirstRep.code

  @Test
  fun aPickerIsFoundByItsItemControl() {
    assertTrue(picker().isDiagnosisPicker())
    assertFalse(Questionnaire.QuestionnaireItemComponent().isDiagnosisPicker())
    assertEquals(listOf("diagnoses"), form().diagnosisPickers().map { it.linkId })
  }

  @Test
  fun aPickerNamesItsListOrUsesTheDefaultOne() {
    assertEquals(DiagnosisCatalogs.DEFAULT_ID, picker().diagnosisCatalogId())

    val named =
      picker().apply { addExtension(DIAGNOSIS_CATALOG_EXTENSION_URL, StringType("other")) }

    assertEquals("other", named.diagnosisCatalogId())
  }

  @Test
  fun anAnswerIsTheConceptUuidNamedInTheLanguageOfTheUser() {
    assertNull(malaria.toAnswerCoding().system)
    assertEquals(malaria.uuid, malaria.toAnswerCoding().code)
    assertEquals("Plasmodium falciparum malaria", malaria.toAnswerCoding().display)
    assertEquals("Paludismo por P. falciparum", malaria.toAnswerCoding("es").display)
  }

  @Test
  fun aDiagnosisIsSentTheWayTheServerTakesIt() {
    val condition = condition(malaria, rank = DIAGNOSIS_RANK_PRIMARY)

    // The concept by its uuid, as a coding with no system: all the server needs.
    assertEquals(1, condition.code.coding.size)
    assertNull(condition.code.codingFirstRep.system)
    assertEquals(malaria.uuid, condition.code.codingFirstRep.code)
    assertEquals("Plasmodium falciparum malaria", condition.code.text)
    assertEquals("encounter-diagnosis", condition.categoryFirstRep.codingFirstRep.code)
    assertEquals("provisional", condition.verificationStatus.codingFirstRep.code)
    // The server refuses a diagnosis without a rank.
    assertEquals(1, condition.rank())
    assertEquals("Patient/p1", condition.subject.reference)
    assertEquals("Encounter/e1", condition.encounter.reference)
  }

  @Test
  fun anAnswerNotInTheListIsSentAsItIs() {
    val coding = Coding(null, "999", "Not in this list")

    val condition = newDiagnosisCondition(coding, null, patient, encounter, 2)

    assertEquals("999", condition.code.codingFirstRep.code)
    assertEquals("Not in this list", condition.code.text)
  }

  @Test
  fun aDiagnosisTickedTwiceIsSavedOnce() {
    val response = answered(malaria, malaria, bronchitis)

    assertEquals(
      listOf(malaria.uuid, bronchitis.uuid),
      form().pickedDiagnoses(response).map { it.code },
    )
  }

  @Test
  fun aFormWithNoPickerPicksNothing() {
    val plain = Questionnaire().apply { addItem().linkId = "other" }

    assertTrue(plain.pickedDiagnoses(answered(malaria)).isEmpty())
  }

  @Test
  fun editingAddsWhatIsNewAndRemovesWhatWasTakenOut() {
    val existing = listOf(condition(malaria), condition(bronchitis))

    val changes = form().diagnosisChanges(answered(malaria, custom), existing)

    assertEquals(listOf(custom.uuid), changes.toAdd.map { it.code })
    assertEquals(listOf(bronchitis.uuid), changes.toRemove.map { it.code.codingFirstRep.code })
  }

  @Test
  fun theFirstDiagnosisAddedIsThePrimaryOnlyWhenNoneStaysPrimary() {
    // The primary stays: what is added is secondary.
    val withPrimary = listOf(condition(malaria, DIAGNOSIS_RANK_PRIMARY))
    val kept = form().diagnosisChanges(answered(malaria, bronchitis, custom), withPrimary)
    val keptRanks = kept.newConditions(catalogs, patient, encounter).map { it.rank() }
    assertEquals(listOf(DIAGNOSIS_RANK_SECONDARY, DIAGNOSIS_RANK_SECONDARY), keptRanks)

    // The primary is taken out: the first one added takes its place.
    val replaced = form().diagnosisChanges(answered(bronchitis, custom), withPrimary)
    val replacedRanks = replaced.newConditions(catalogs, patient, encounter).map { it.rank() }
    assertEquals(listOf(DIAGNOSIS_RANK_PRIMARY, DIAGNOSIS_RANK_SECONDARY), replacedRanks)

    // An encounter with no diagnosis yet: same.
    val first = form().diagnosisChanges(answered(malaria, bronchitis), emptyList())
    val firstRanks = first.newConditions(catalogs, patient, encounter).map { it.rank() }
    assertEquals(listOf(DIAGNOSIS_RANK_PRIMARY, DIAGNOSIS_RANK_SECONDARY), firstRanks)
  }

  @Test
  fun aDiagnosisThatCameFromTheServerIsRecognisedByItsUuid() {
    // As the server writes it: the uuid, and the CIEL id and the SNOMED code it adds.
    val fromServer =
      Condition().apply {
        id = "c1"
        addCategory(encounterDiagnosisCategory())
        code =
          CodeableConcept().apply {
            addCoding(Coding(null, malaria.uuid, "Plasmodium falciparum malaria"))
            addCoding(Coding("https://cielterminology.org", "116125", null))
            addCoding(Coding("http://snomed.info/sct/", "62676008", null))
          }
      }

    val changes = form().diagnosisChanges(answered(malaria), listOf(fromServer))

    assertTrue(changes.toAdd.isEmpty())
    assertTrue(changes.toRemove.isEmpty())
  }

  @Test
  fun editingLeavesAloneTheConditionsThatAreNotDiagnoses() {
    val problem =
      condition(malaria).apply {
        category = listOf(CodeableConcept().addCoding(Coding("x", "problem-list-item", null)))
      }

    val changes = form().diagnosisChanges(answered(bronchitis), listOf(problem))

    assertEquals(listOf(bronchitis.uuid), changes.toAdd.map { it.code })
    assertTrue(changes.toRemove.isEmpty())
  }

  @Test
  fun aDiagnosisWrittenAsFreeTextIsNotTouched() {
    val freeText =
      Condition().apply {
        id = "c2"
        addCategory(encounterDiagnosisCategory())
        code = CodeableConcept().setText("Some free text")
      }

    val changes = form().diagnosisChanges(answered(), listOf(freeText))

    assertTrue(changes.toRemove.isEmpty())
  }

  @Test
  fun anEditedEncounterStartsWithItsDiagnosesTickedPrimaryFirst() {
    val response = answered()
    val existing =
      listOf(
        condition(custom, DIAGNOSIS_RANK_SECONDARY),
        condition(malaria, DIAGNOSIS_RANK_PRIMARY),
      )

    form().populateDiagnosisAnswers(response, existing, catalogs)

    assertEquals(
      listOf(malaria.uuid, custom.uuid),
      form().pickedDiagnoses(response).map { it.code },
    )
  }

  @Test
  fun aQuestionCanFixTheRankOfItsDiagnoses() {
    assertEquals(DIAGNOSIS_RANK_PRIMARY, picker(rank = "primary").diagnosisRank())
    assertEquals(DIAGNOSIS_RANK_SECONDARY, picker(rank = "Secondary").diagnosisRank())
    assertNull(picker().diagnosisRank())
    assertEquals(listOf("primary", "secondary"), rankedForm().diagnosisPickers().map { it.linkId })
  }

  @Test
  fun theRankIsTheOneOfTheQuestionWhateverTheOrder() {
    val response = answeredRanked(primary = listOf(bronchitis), secondary = listOf(custom, malaria))

    val changes = rankedForm().diagnosisChanges(response, emptyList())
    val ranks =
      changes.newConditions(catalogs, patient, encounter).associate { it.uuid() to it.rank() }

    // The primary one comes first, the secondary ones follow in the order they were ticked.
    assertEquals(
      listOf(bronchitis.uuid, custom.uuid, malaria.uuid),
      changes.toAdd.map { it.code },
    )
    assertEquals(DIAGNOSIS_RANK_PRIMARY, ranks[bronchitis.uuid])
    assertEquals(DIAGNOSIS_RANK_SECONDARY, ranks[custom.uuid])
    assertEquals(DIAGNOSIS_RANK_SECONDARY, ranks[malaria.uuid])
  }

  @Test
  fun aDiagnosisTickedAsPrimaryAndSecondaryIsSavedOnceAsPrimary() {
    val response = answeredRanked(primary = listOf(malaria), secondary = listOf(malaria, custom))

    val conditions =
      rankedForm()
        .diagnosisChanges(response, emptyList())
        .newConditions(catalogs, patient, encounter)

    assertEquals(
      listOf(malaria.uuid to DIAGNOSIS_RANK_PRIMARY, custom.uuid to DIAGNOSIS_RANK_SECONDARY),
      conditions.map { it.uuid() to it.rank() },
    )
  }

  @Test
  fun aFormWithAPrimaryQuestionNeverMakesASecondaryDiagnosisThePrimaryOne() {
    val response = answeredRanked(primary = emptyList(), secondary = listOf(malaria))

    val conditions =
      rankedForm()
        .diagnosisChanges(response, emptyList())
        .newConditions(catalogs, patient, encounter)

    // The unranked rule does not make the first secondary diagnosis the primary one.
    assertEquals(listOf(DIAGNOSIS_RANK_SECONDARY), conditions.map { it.rank() })
  }

  @Test
  fun editingKeepsWhatStaysInItsQuestion() {
    val existing =
      listOf(
        condition(bronchitis, DIAGNOSIS_RANK_PRIMARY),
        condition(malaria, DIAGNOSIS_RANK_SECONDARY),
      )
    val response = answeredRanked(primary = listOf(bronchitis), secondary = listOf(malaria))

    val changes = rankedForm().diagnosisChanges(response, existing)

    assertTrue(changes.toAdd.isEmpty())
    assertTrue(changes.toRemove.isEmpty())
  }

  @Test
  fun aDiagnosisMovedToTheOtherQuestionIsReplacedWithTheNewRank() {
    val bronchitisPrimary = condition(bronchitis, DIAGNOSIS_RANK_PRIMARY)
    val malariaSecondary = condition(malaria, DIAGNOSIS_RANK_SECONDARY)
    // Malaria is now the primary diagnosis and bronchitis a secondary one.
    val response = answeredRanked(primary = listOf(malaria), secondary = listOf(bronchitis))

    val changes =
      rankedForm().diagnosisChanges(response, listOf(bronchitisPrimary, malariaSecondary))

    assertEquals(
      setOf(bronchitisPrimary, malariaSecondary),
      changes.toRemove.toSet(),
    )
    assertEquals(
      mapOf(malaria.uuid to DIAGNOSIS_RANK_PRIMARY, bronchitis.uuid to DIAGNOSIS_RANK_SECONDARY),
      changes.newConditions(catalogs, patient, encounter).associate { it.uuid() to it.rank() },
    )
  }

  @Test
  fun aNewSecondaryDiagnosisNeverReplacesThePrimaryOneThatStays() {
    val existing = listOf(condition(bronchitis, DIAGNOSIS_RANK_PRIMARY))
    val response = answeredRanked(primary = listOf(bronchitis), secondary = listOf(custom))

    val changes = rankedForm().diagnosisChanges(response, existing)

    assertTrue(changes.toRemove.isEmpty())
    assertEquals(
      listOf(DIAGNOSIS_RANK_SECONDARY),
      changes.newConditions(catalogs, patient, encounter).map { it.rank() },
    )
  }

  @Test
  fun anEditedEncounterFillsEachQuestionWithItsRank() {
    val response = answeredRanked(primary = emptyList(), secondary = emptyList())
    val existing =
      listOf(
        condition(custom, DIAGNOSIS_RANK_SECONDARY),
        condition(bronchitis, DIAGNOSIS_RANK_PRIMARY),
        condition(malaria, DIAGNOSIS_RANK_SECONDARY),
      )

    rankedForm().populateDiagnosisAnswers(response, existing, catalogs)

    val answers =
      response.item.single().item.associate { item ->
        item.linkId to item.answer.map { (it.value as Coding).code }
      }
    assertEquals(listOf(bronchitis.uuid), answers["primary"])
    assertEquals(listOf(custom.uuid, malaria.uuid), answers["secondary"])
  }
}
