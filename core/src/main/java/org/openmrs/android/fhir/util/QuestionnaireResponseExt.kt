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

import org.hl7.fhir.r4.model.Attachment
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Type

/**
 * A stable, order-preserving summary of the *answered* questions in the response, used to tell
 * whether a user changed a form.
 *
 * Unanswered items are ignored on purpose: the questionnaire engine adds and drops empty items as
 * it re-renders, so they would make an untouched form look modified. Two responses with the same
 * answers produce the same signature regardless of generated ids or metadata, and clearing an
 * answer returns the signature to its original value.
 */
fun QuestionnaireResponse.answerSignature(): String = buildString { appendAnswers(item) }

private fun StringBuilder.appendAnswers(
  items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>,
) {
  for (item in items) {
    for (answer in item.answer) {
      val value = answer.value.signature()
      if (value.isNotEmpty()) append(item.linkId).append('=').append(value).append(';')
      appendAnswers(answer.item)
    }
    appendAnswers(item.item)
  }
}

private fun Type?.signature(): String =
  when (this) {
    null -> ""
    is Coding -> if (code.isNullOrEmpty()) "" else "$system|$code"
    is Quantity -> if (value == null) "" else "$value $unit"
    is Reference -> reference.orEmpty()
    is Attachment -> url.orEmpty() + title.orEmpty()
    else -> primitiveValue().orEmpty()
  }
