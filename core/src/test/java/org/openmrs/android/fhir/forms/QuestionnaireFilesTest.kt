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
package org.openmrs.android.fhir.forms

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The questionnaire is handed to the form library as a file, so the saved state stays small. */
class QuestionnaireFilesTest {
  @get:Rule val folder = TemporaryFolder()

  private val directory
    get() = File(folder.root, "questionnaires")

  @Test
  fun theFileHoldsTheQuestionnaireExactly() {
    val json = """{"resourceType":"Questionnaire","title":"Évaluation – عربى"}"""

    val file = QuestionnaireFiles.fileFor(directory, json)

    assertEquals(json, file.readText(Charsets.UTF_8))
  }

  @Test
  fun theSameQuestionnaireIsWrittenOnce() {
    val first = QuestionnaireFiles.fileFor(directory, "{\"a\":1}")
    val second = QuestionnaireFiles.fileFor(directory, "{\"a\":1}")

    assertEquals(first, second)
    assertEquals(1, directory.listFiles()!!.size)
  }

  @Test
  fun anotherQuestionnaireGetsAnotherFile() {
    val first = QuestionnaireFiles.fileFor(directory, "{\"a\":1}")
    val second = QuestionnaireFiles.fileFor(directory, "{\"a\":2}")

    assertNotEquals(first, second)
  }

  @Test
  fun aLargeQuestionnaireLeavesNothingBehindButItsFile() {
    val json = "x".repeat(400_000)

    QuestionnaireFiles.fileFor(directory, json)

    assertEquals(listOf(true), directory.listFiles()!!.map { it.name.endsWith(".json") })
  }

  @Test
  fun filesNotUsedForTwoWeeksAreRemoved() {
    val old = QuestionnaireFiles.fileFor(directory, "{\"old\":1}")
    old.setLastModified(System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000)

    val current = QuestionnaireFiles.fileFor(directory, "{\"new\":1}")

    assertFalse(old.exists())
    assertTrue(current.exists())
  }
}
