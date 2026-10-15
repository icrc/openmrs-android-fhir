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
package org.openmrs.android.fhir.util

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.StringType
import org.junit.Test

class QuestionnaireResponseSignatureTest {

  private fun response(vararg answers: Pair<String, String>) =
    QuestionnaireResponse().apply {
      answers.forEach { (linkId, value) ->
        addItem().apply {
          this.linkId = linkId
          addAnswer().value = StringType(value)
        }
      }
    }

  private fun genderResponse(code: String) =
    QuestionnaireResponse().apply {
      addItem().apply {
        linkId = "group"
        addItem().apply {
          linkId = "gender"
          addAnswer().value = Coding("http://hl7.org/fhir/administrative-gender", code, null)
        }
      }
    }

  @Test
  fun sameAnswers_produceSameSignature_evenWithDifferentIds() {
    val a = response("given" to "Jane", "family" to "Doe").apply { id = "one" }
    val b = response("given" to "Jane", "family" to "Doe").apply { id = "two" }
    assertEquals(a.answerSignature(), b.answerSignature())
  }

  @Test
  fun changedAnswer_changesSignature() {
    assertNotEquals(
      response("given" to "Jane").answerSignature(),
      response("given" to "Janet").answerSignature(),
    )
  }

  @Test
  fun addedAnswer_changesSignature() {
    assertNotEquals(
      response("given" to "Jane").answerSignature(),
      response("given" to "Jane", "family" to "Doe").answerSignature(),
    )
  }

  @Test
  fun emptyResponse_hasEmptySignature() {
    assertEquals("", QuestionnaireResponse().answerSignature())
  }

  @Test
  fun nestedCodedAnswers_areIncluded() {
    assertNotEquals(
      genderResponse("female").answerSignature(),
      genderResponse("male").answerSignature(),
    )
  }
}
