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
package org.openmrs.android.fhir.extensions

import android.content.Context
import ca.uhn.fhir.parser.IParser
import com.google.android.fhir.FhirEngine
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.ResourceType

suspend fun FhirEngine.getQuestionnaireOrFromAssets(
  questionnaireId: String,
  applicationContext: Context,
  parser: IParser,
): Questionnaire? {
  return try {
    this.get(ResourceType.Questionnaire, questionnaireId) as Questionnaire
  } catch (e: Exception) {
    try {
      val questionnaireString = applicationContext.readFileFromAssets("$questionnaireId.json")
      parser.parseResource(Questionnaire::class.java, questionnaireString)
    } catch (e: Exception) {
      null
    }
  }
}

suspend fun FhirEngine.getQuestionnaireOrFromAssetsAsString(
  questionnaireId: String,
  applicationContext: Context,
  parser: IParser,
): String {
  return try {
    val questionnaire = this.get(ResourceType.Questionnaire, questionnaireId) as Questionnaire
    parser.encodeResourceToString(questionnaire)
  } catch (e: Exception) {
    try {
      applicationContext.readFileFromAssets("$questionnaireId.json")
    } catch (e: Exception) {
      ""
    }
  }
}
