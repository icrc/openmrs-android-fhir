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

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosisCatalogCacheTest {
  @get:Rule val folder = TemporaryFolder()

  private val diagnoses =
    listOf(
      Diagnosis(
        "10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "Acute bronchitis",
        listOf("J20.9", "J20"),
        mapOf(
          "fr" to "Bronchite aiguë",
          "es" to "Bronquitis aguda",
          "ar" to "التهاب القصبات الحاد",
        ),
      ),
      Diagnosis("d17f710c-645f-4b72-9606-2a8414841a19", "Transfusion reaction", listOf("T80.9")),
      Diagnosis("99AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "No code", emptyList()),
    )

  @Test
  fun whatIsWrittenIsReadBackWithEverything() {
    val file = folder.newFile("list.bin")

    DiagnosisCatalogCache.write(file, "diagnoses/1", diagnoses)
    val read = DiagnosisCatalogCache.read(file, "diagnoses/1")

    assertNotNull(read)
    assertEquals(diagnoses.map { it.uuid }, read.map { it.uuid })
    assertEquals(diagnoses.map { it.name }, read.map { it.name })
    assertEquals(diagnoses.map { it.icd }, read.map { it.icd })
    assertEquals(diagnoses.map { it.translations }, read.map { it.translations })
    // A list read from the copy is searched like one read from the CSV.
    assertEquals(
      listOf("Acute bronchitis"),
      DiagnosisCatalog("diagnoses", read).search("bronquitis").items.map { it.name },
    )
  }

  @Test
  fun aCopyMadeForAnotherBuildIsNotUsed() {
    val file = folder.newFile("list.bin")
    DiagnosisCatalogCache.write(file, "diagnoses/1", diagnoses)

    assertNull(DiagnosisCatalogCache.read(file, "diagnoses/2"))
  }

  @Test
  fun aMissingOrDamagedCopyIsNoCopy() {
    val missing = File(folder.root, "missing.bin")
    assertNull(DiagnosisCatalogCache.read(missing, "k"))

    val file = folder.newFile("list.bin")
    DiagnosisCatalogCache.write(file, "k", diagnoses)
    val bytes = file.readBytes()

    // Cut short (a write that was interrupted), and with other bytes in the middle.
    file.writeBytes(bytes.copyOf(bytes.size / 2))
    assertNull(DiagnosisCatalogCache.read(file, "k"))
    file.writeBytes(bytes.copyOf(bytes.size - 4))
    assertNull(DiagnosisCatalogCache.read(file, "k"))
    file.writeBytes(ByteArray(64) { it.toByte() })
    assertNull(DiagnosisCatalogCache.read(file, "k"))
    file.writeBytes(ByteArray(0))
    assertNull(DiagnosisCatalogCache.read(file, "k"))
  }

  @Test
  fun writingLeavesNoHalfWrittenFileAndReplacesAnOlderCopy() {
    val file = folder.newFile("list.bin")
    DiagnosisCatalogCache.write(file, "old", diagnoses)

    DiagnosisCatalogCache.write(file, "new", diagnoses.take(1))

    assertNull(DiagnosisCatalogCache.read(file, "old"))
    assertEquals(1, DiagnosisCatalogCache.read(file, "new")?.size)
    assertFalse(File(folder.root, "list.bin.tmp").exists())
  }

  @Test
  fun theBundledListGoesThroughTheCopyUnchanged() {
    val csv = File("src/main/assets/diagnoses/${DiagnosisCatalogs.DEFAULT_ID}.csv")
    val catalog = csv.bufferedReader(Charsets.UTF_8).use { DiagnosisCsv.parse(it) }
    val file = folder.newFile("bundled.bin")

    DiagnosisCatalogCache.write(file, "k", catalog.diagnoses)
    val read = DiagnosisCatalogCache.read(file, "k")

    assertNotNull(read)
    assertEquals(catalog.size, read.size)
    assertTrue(
      read.indices.all {
        catalog.diagnoses[it].run {
          uuid == read[it].uuid &&
            name == read[it].name &&
            icd == read[it].icd &&
            translations == read[it].translations
        }
      },
    )
  }
}
