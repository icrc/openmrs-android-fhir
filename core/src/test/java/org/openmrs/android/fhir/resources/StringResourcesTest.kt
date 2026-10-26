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
package org.openmrs.android.fhir.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The translations the app ships in: every string exists in all five languages, none is left
 * without its English text, and a translation uses the same placeholders as the English text, so
 * formatting it cannot crash.
 */
class StringResourcesTest {
  private val languages = listOf("values-fr", "values-es", "values-ru", "values-ar")
  private val resources = File("src/main/res")

  private fun strings(directory: String): Map<String, String> {
    val file = File(resources, "$directory/strings.xml")
    assertTrue(file.exists(), "${file.path} is missing")
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val nodes = document.getElementsByTagName("string")
    return (0 until nodes.length)
      .map { nodes.item(it) as Element }
      .filter { it.getAttribute("translatable") != "false" }
      .associate { it.getAttribute("name") to it.textContent }
  }

  private val english by lazy { strings("values") }

  private val placeholder = Regex("%(\\d+\\$)?[sdf]")

  @Test
  fun everyStringIsTranslated() {
    assertTrue(english.isNotEmpty(), "no strings found: is the test running in core/?")
    languages.forEach { language ->
      val missing = english.keys - strings(language).keys
      assertTrue(missing.isEmpty(), "$language lacks: ${missing.sorted()}")
    }
  }

  @Test
  fun noTranslationOutlivesItsEnglishText() {
    languages.forEach { language ->
      val orphans = strings(language).keys - english.keys
      assertTrue(
        orphans.isEmpty(),
        "$language has translations without an English text: ${orphans.sorted()}",
      )
    }
  }

  @Test
  fun everyTourStepHasATitleAndABody() {
    val titles = english.keys.filter { it.startsWith("help_") && it.endsWith("_title") }
    titles.forEach { title ->
      val body = title.removeSuffix("_title") + "_body"
      assertTrue(body in english, "$title has no $body")
    }
  }

  @Test
  fun translationsKeepThePlaceholdersOfTheEnglishText() {
    languages.forEach { language ->
      strings(language).forEach { (name, text) ->
        val original = english[name] ?: return@forEach
        assertEquals(
          placeholder
            .findAll(original)
            .map { it.value.replace(Regex("^%\\d+\\$"), "%") }
            .sorted()
            .toList(),
          placeholder
            .findAll(text)
            .map { it.value.replace(Regex("^%\\d+\\$"), "%") }
            .sorted()
            .toList(),
          "$language/$name uses other placeholders than the English text",
        )
      }
    }
  }
}
