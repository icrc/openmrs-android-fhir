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

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.hl7.fhir.r4.model.Questionnaire
import org.junit.Test

/**
 * The group session hands the form to its screen as a copy and the JSON it was written to, instead
 * of reading that JSON back into a form. That is only the same thing if a form, a copy of it and
 * the form read back from its JSON all write out identically: checked on every form of the app, in
 * the base language and with the translations of one language only.
 */
class QuestionnaireCopyTest {
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
  private val assets = File("src/main/assets")

  private fun bundledForms(): List<File> =
    listOf(assets, File(assets, BUNDLED_QUESTIONNAIRES_DIR)).flatMap { folder ->
      folder.listFiles { file -> file.extension == "json" }.orEmpty().toList()
    }

  @Test
  fun aCopyAndAFormReadBackFromItsJsonWriteOutLikeTheForm() {
    val forms = bundledForms()
    assertTrue(forms.size >= 20, "no forms found: is the test running in core/?")

    listOf(null, Locale.FRENCH, Locale("ar")).forEach { locale ->
      forms.forEach { file ->
        val json = expandCompactQuestionnaire(file.readText(), locale)
        val form = parser.parseResource(Questionnaire::class.java, json)
        val written = parser.encodeResourceToString(form)

        val readBack =
          parser.encodeResourceToString(parser.parseResource(Questionnaire::class.java, written))
        val copied = parser.encodeResourceToString(form.copy())

        assertEquals(written, readBack, "${file.name} read back ($locale)")
        assertEquals(written, copied, "${file.name} copied ($locale)")
      }
    }
  }
}
