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
package org.openmrs.android.fhir

import java.text.SimpleDateFormat
import java.util.Locale

object Constants {
  val DATE24_FORMATTER = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
  val VISIT_TYPE_UUID = "7b0f5697-27e3-40c4-8bae-f4049abfb4ed"
  val VISIT_TYPE_CODE_SYSTEM = "http://fhir.openmrs.org/code-system/visit-type"
  val WRAP_ENCOUNTER = true

  // FHIR URLs
  val PATIENT_LOCATION_IDENTIFIER_URL = "http://fhir.openmrs.org/ext/patient/identifier#location"
  val PATIENT_IDENTIFIER_DEFINITION_URL =
    "http://hl7.org/fhir/StructureDefinition/Patient#Patient.identifier"
  val PERSON_ATTRIBUTE_LINK_ID_PREFIX = "PersonAttribute#"
  val OPENMRS_PERSON_ATTRIBUTE_URL = "http://fhir.openmrs.org/ext/person-attribute"
  val OPENMRS_PERSON_ATTRIBUTE_TYPE_URL = "http://fhir.openmrs.org/ext/person-attribute-type"
  val OPENMRS_PERSON_ATTRIBUTE_VALUE_URL = "http://fhir.openmrs.org/ext/person-attribute-value"
  val SHOW_SCREENER_EXTENSION_URL = "https://openmrs.org/ext/show-screener"

  // Screener questionnaire internal observation ids.
  val SESSION_IDENTIFIER_UUID = "6a803908-8a5b-4598-adea-19358c83529a"
  val COHORT_IDENTIFIER_UUID = "5461f231-7e59-4be8-93a4-6d49fd13c00a"
  val COHORT_NAME_UUID = "6029f289-92a6-4a68-80f1-3078d0152449"
  val SESSION_DATE_UUID = "ceaca505-6dff-4940-8a43-8c060a0924d7"
}
