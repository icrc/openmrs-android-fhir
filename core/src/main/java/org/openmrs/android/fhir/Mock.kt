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
import org.hl7.fhir.r4.model.Reference

data class Form(
  val display: String,
  val code: String,
  val resource: String,
) {
  constructor(display: String, code: String) : this(display, code, "")
}

data class Patient(
  val display: String,
  val code: String,
  val resource: String,
) {
  constructor(display: String, code: String) : this(display, code, "")
}

data class User(
  val uuid: String,
  val display: String,
  val providerUuid: String,
)

object MockConstants {
  val DATE24_FORMATTER = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
  val LOCATION = createMockLocationReference()
  val AUTHENTICATED_USER = getAuthenticatedUser()
  val VISIT_TYPE_UUID = "7b0f5697-27e3-40c4-8bae-f4049abfb4ed"
  val MOCK_FORMS =
    listOf(
      Form(
        display = "Assessment Form",
        code = "0c63150d-ff39-42e1-9048-834mh76p2s72",
        resource = "assessment.json",
      ),
      Form(
        display = "Followup Form",
        code = "07a7dd1c-7280-483a-a3bc-01be995293ac",
        resource = "assessment.json",
      ),
      Form(
        display = "Closure Form",
        code = "95458795-3o06-4l59-9508-c217aa21ea26",
        resource = "assessment.json",
      ),
    )
  val WRAP_ENCOUNTER = true
}

fun createMockLocationReference(): Reference {
  val locationReference =
    Reference().apply {
      reference = "Location/8d6c993e-c2cc-11de-8d13-0010c6dffd0f"
      type = "Location"
      display = "Unknown Location"
    }
  return locationReference
}

fun getAuthenticatedUser(): User {
  val authenticatedUser =
    User(
      uuid = "1c3db49d-440a-11e6-a65c-00e04c680037",
      display = "Admin",
      providerUuid = "f9badd80-ab76-11e2-9e96-0800200c9a66",
    )
  return authenticatedUser
}
