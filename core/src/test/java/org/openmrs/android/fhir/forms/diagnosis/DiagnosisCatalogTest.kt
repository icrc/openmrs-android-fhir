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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class DiagnosisCatalogTest {
  // The shape of the bundled file: a name per language and a row per ICD-10 code of a concept.
  private val csv =
    """
    uuid,name:en,name:fr,name:es,name:ru,name:ar,code
    117789AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Glaucoma,Glaucome,Glaucoma,Глаукома,,H40.9
    117789AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Glaucoma,Glaucome,Glaucoma,Глаукома,,H40
    d17f710c-645f-4b72-9606-2a8414841a19,TRANSFUSION REACTION,,,,,T80.9
    116125AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Plasmodium falciparum malaria,,,,,B50.9
    150AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Unspecified malaria,,,,,B54
    10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Acute bronchitis,Bronchite aiguë,bronquitis aguda,,,J20.9
    10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Acute bronchitis,Bronchite aiguë,bronquitis aguda,,,J20
    145AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Mild depressive episode,,,,,F32.0
    146AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Mixed anxiety and depressive disorder,,,,,F41.2
    99AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Callus without a code,,,,,
    """
      .trimIndent()

  private val catalog = DiagnosisCsv.parse(StringReader(csv))

  @Test
  fun everyConceptOfTheCsvIsOneDiagnosisWithItsCodes() {
    assertEquals(8, catalog.size)
    val glaucoma = catalog.find("117789AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
    assertEquals("Glaucoma", glaucoma?.name)
    // One row per code, in the order of the file.
    assertEquals(listOf("H40.9", "H40"), glaucoma?.icd)
    assertEquals(listOf("J20.9", "J20"), catalog.find("10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")?.icd)
    // A concept with no code is still offered, by its name.
    assertEquals(emptyList(), catalog.find("99AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")?.icd)
  }

  @Test
  fun aDiagnosisIsFoundByItsUuidWhateverTheCase() {
    val reaction = catalog.find("D17F710C-645F-4B72-9606-2A8414841A19")

    assertNotNull(reaction)
    assertEquals("d17f710c-645f-4b72-9606-2a8414841a19", reaction.code)
  }

  @Test
  fun theCsvNeedsItsColumns() {
    assertFailsWith<InvalidDiagnosisCatalogException> {
      DiagnosisCsv.parse(StringReader("a,b,c\n1,2,3\n"))
    }
    // No code column.
    assertFailsWith<InvalidDiagnosisCatalogException> {
      DiagnosisCsv.parse(StringReader("uuid,name:en\nx,A\n"))
    }
    // No name column.
    assertFailsWith<InvalidDiagnosisCatalogException> {
      DiagnosisCsv.parse(StringReader("uuid,code\nx,A00\n"))
    }
    // The columns of the dictionary export are not this format.
    assertFailsWith<InvalidDiagnosisCatalogException> {
      DiagnosisCsv.parse(StringReader("uuid,name,code_source,icd10_code\nx,A,ICD-10,A00\n"))
    }
    // No row at all.
    assertFailsWith<InvalidDiagnosisCatalogException> {
      DiagnosisCsv.parse(StringReader("uuid,name:en,code\n"))
    }
    val minimal = "uuid,name:en,code\nabc,Cholera,A00\n"
    assertEquals(listOf("A00"), DiagnosisCsv.parse(StringReader(minimal)).find("abc")?.icd)
  }

  @Test
  fun anIdColumnAndTheOrderOfTheColumnsDoNotMatter() {
    val reordered = "code,concept_id,name:en,uuid\nA00,1,Cholera,abc\n"

    val cholera = DiagnosisCsv.parse(StringReader(reordered)).find("abc")

    assertEquals("Cholera", cholera?.name)
    assertEquals(listOf("A00"), cholera?.icd)
  }

  @Test
  fun aNameCanBeQuotedWithCommasAndQuotes() {
    val quoted = "uuid,name:en,code\n" + "abc,\"Callus, \"\"hard\"\"\",L84\n"

    assertEquals("Callus, \"hard\"", DiagnosisCsv.parse(StringReader(quoted)).find("abc")?.name)
  }

  @Test
  fun theNamesOfEachLanguageAreKeptAndShownInThatLanguage() {
    val bronchitis = catalog.find("10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")!!

    assertEquals("Acute bronchitis", bronchitis.name)
    assertEquals("bronquitis aguda", bronchitis.nameIn("es"))
    assertEquals("bronquitis aguda", bronchitis.nameIn("es-MX"))
    assertEquals("Bronchite aiguë", bronchitis.nameIn("fr"))
    // No name in that language: the English one.
    assertEquals("Acute bronchitis", bronchitis.nameIn("ru"))
    assertEquals("Acute bronchitis", bronchitis.nameIn("ar"))
    assertEquals("Глаукома", catalog.find("117789AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")!!.nameIn("ru"))
    // A name that is the same in every language is kept once.
    assertEquals(
      mapOf("fr" to "Glaucome", "ru" to "Глаукома"),
      catalog.find("117789AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")!!.translations,
    )
  }

  @Test
  fun aColumnWithNoLanguageIsTheDefaultNameBesideThoseWithOne() {
    val mixed =
      """
      uuid,name,name:ru,code
      10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Acute bronchitis,Острый бронхит,J20.9
      """
        .trimIndent()

    val diagnosis = DiagnosisCsv.parse(StringReader(mixed)).diagnoses.single()

    assertEquals("Acute bronchitis", diagnosis.name)
    assertEquals("Острый бронхит", diagnosis.nameIn("ru"))
  }

  @Test
  fun aNameInAnyLanguageIsSearchable() {
    assertEquals(listOf("Acute bronchitis"), catalog.search("bronquitis").items.map { it.name })
    assertEquals(listOf("Acute bronchitis"), catalog.search("bronchite").items.map { it.name })
    assertEquals(listOf("Glaucoma"), catalog.search("глаукома").items.map { it.name })
    assertEquals(listOf("Acute bronchitis"), catalog.search("acute").items.map { it.name })
  }

  @Test
  fun theSearchIgnoresAccentsAndMarks() {
    val accented =
      DiagnosisCsv.parse(
        StringReader(
          """
          uuid,name:en,name:fr,name:es,name:ru,name:ar,code
          a,Hypertension,Hypertension,Hipertensión,Гипертензия,ارتفاع ضغط الدم,I10
          b,Acute bronchitis,Bronchite aiguë,Bronquitis aguda,Острый бронхит,التهاب القصبات الحاد,J20.9
          c,Pregnancy,Grossesse,Embarazo,Ёлка,حَمْل,Z33
          """
            .trimIndent(),
        ),
      )

    assertEquals(listOf("Hypertension"), accented.search("hipertension").items.map { it.name })
    assertEquals(listOf("Hypertension"), accented.search("HIPERTENSIÓN").items.map { it.name })
    assertEquals(
      listOf("Acute bronchitis"),
      accented.search("bronchite aigue").items.map { it.name },
    )
    assertEquals(
      listOf("Acute bronchitis"),
      accented.search("острыи бронхит").items.map { it.name },
    )
    // The marks of an Arabic name (here the vowel signs of "حَمْل") are not needed to find it.
    assertEquals(listOf("Pregnancy"), accented.search("حمل").items.map { it.name })
    assertEquals(listOf("Pregnancy"), accented.search("елка").items.map { it.name })
  }

  @Test
  fun theQuickFoldGivesWhatFullNormalizationGives() {
    fun reference(text: String): String =
      java.text.Normalizer.normalize(
          text.lowercase(java.util.Locale.ROOT),
          java.text.Normalizer.Form.NFD,
        )
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    val file = java.io.File("src/main/assets/diagnoses/${DiagnosisCatalogs.DEFAULT_ID}.csv")
    val bundled = file.bufferedReader(Charsets.UTF_8).use { DiagnosisCsv.parse(it) }
    val samples =
      bundled.diagnoses.flatMap { listOf(it.name) + it.translations.values } +
        listOf("Ёлка", "Йод", "ÀÉÎõü", "حَمْل", "أإآؤئ", "İstanbul", "ǅ", "naïve café")

    val different = samples.filter { foldForSearch(it) != reference(it) }

    assertEquals(emptyList(), different.take(5))
  }

  @Test
  fun everyWordHasToMatch() {
    assertEquals(
      listOf("Mixed anxiety and depressive disorder"),
      catalog.search("depressive mixed").items.map { it.name },
    )
  }

  @Test
  fun anIcdCodeIsFoundWithOrWithoutTheDot() {
    assertEquals("Acute bronchitis", catalog.search("j20.9").items.first().name)
    assertEquals("Acute bronchitis", catalog.search("J209").items.first().name)
    assertEquals("Mild depressive episode", catalog.search("f32").items.first().name)
  }

  @Test
  fun namesThatStartWithTheTextComeBeforeTheOthers() {
    val names = catalog.search("depressive").items.map { it.name }

    assertEquals(
      listOf("Mixed anxiety and depressive disorder", "Mild depressive episode"),
      names.reversed(),
    )
  }

  @Test
  fun theCaseOfTheSearchDoesNotMatter() {
    assertEquals(
      catalog.search("MALARIA").items.map { it.uuid },
      catalog.search("malaria").items.map { it.uuid },
    )
    assertEquals(2, catalog.search("malaria").total)
  }

  @Test
  fun theResultIsCutAndSaysHowManyMatched() {
    val result = catalog.search("a", limit = 3)

    assertEquals(3, result.items.size)
    assertTrue(result.total > 3)
  }

  @Test
  fun withNoTextTheListStartsFromTheTop() {
    val result = catalog.search("  ", limit = 2)

    assertEquals(
      listOf("Acute bronchitis", "Callus without a code"),
      result.items.map { it.name },
    )
    assertEquals(8, result.total)
  }

  @Test
  fun nothingMatchingIsAnEmptyResult() {
    val result = catalog.search("zzz")

    assertEquals(0, result.total)
    assertTrue(result.items.isEmpty())
  }

  @Test
  fun theCsvBundledWithTheAppIsAValidList() {
    val file = java.io.File("src/main/assets/diagnoses/${DiagnosisCatalogs.DEFAULT_ID}.csv")

    assertEquals(
      "uuid,name:en,name:fr,name:es,name:ru,name:ar,code",
      file.bufferedReader(Charsets.UTF_8).use { it.readLine() },
    )
    val bundled = file.bufferedReader(Charsets.UTF_8).use { DiagnosisCsv.parse(it) }

    assertTrue(bundled.size > 40_000)
    // Every diagnosis of the file has an ICD-10 code.
    assertTrue(bundled.diagnoses.all { it.icd.isNotEmpty() })
    val bronchitis = bundled.find("10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
    assertEquals("Acute bronchitis", bronchitis?.name)
    assertEquals("J20.9", bronchitis?.icdLabel)
    assertEquals("Bronchite aiguë", bronchitis?.nameIn("fr"))
    assertTrue(bundled.search("bronchitis").total > 0)
    // Nearly every name has its translation in each language of the app (the few that read the
    // same as in English, such as a Latin term, are not kept twice).
    listOf("fr", "es", "ru", "ar").forEach { language ->
      val translated = bundled.diagnoses.count { language in it.translations }
      assertTrue(translated > bundled.size * 0.9, "$language: $translated of ${bundled.size}")
    }
    assertEquals(
      "Острый бронхит",
      bundled.search("острый бронхит").items.firstOrNull()?.nameIn("ru"),
    )
  }
}
