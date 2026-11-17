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

package org.openmrs.android.fhir.patientlists

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PatientListExportTest {
  private val headers = listOf("Name", "Sex", "Birth date", "Identifier", "Patient id", "Status")

  private fun member(
    id: String,
    name: String,
    pending: MemberPending? = null,
    identifier: String? = "ID-$id",
  ) = PatientListMember(id, name, "female", "1990-01-01", identifier, pending, error = null)

  private fun csv(vararg members: PatientListMember) =
    PatientListExport.csv(members.toList(), headers, "In the list", "To be added")

  @Test
  fun everyMemberIsOneLineAfterTheTitlesSortedByName() {
    val text = csv(member("2", "Zoe"), member("1", "Ann"))

    assertThat(text.removePrefix(PatientListExport.BYTE_ORDER_MARK).trimEnd().lines())
      .containsExactly(
        "Name,Sex,Birth date,Identifier,Patient id,Status",
        "Ann,female,1990-01-01,ID-1,1,In the list",
        "Zoe,female,1990-01-01,ID-2,2,In the list",
      )
      .inOrder()
    assertThat(text).startsWith(PatientListExport.BYTE_ORDER_MARK)
  }

  @Test
  fun aPatientRemovedAndNotSentIsLeftOutAndOneAddedIsMarked() {
    val text = csv(member("1", "Ann", MemberPending.REMOVE), member("2", "Bea", MemberPending.ADD))

    assertThat(text).doesNotContain("Ann")
    assertThat(text).contains("Bea,female,1990-01-01,ID-2,2,To be added")
  }

  @Test
  fun commasQuotesAndLineBreaksAreQuoted() {
    assertThat(PatientListExport.cell("Doe, Ann")).isEqualTo("\"Doe, Ann\"")
    assertThat(PatientListExport.cell("Ann \"Nan\" Doe")).isEqualTo("\"Ann \"\"Nan\"\" Doe\"")
    assertThat(PatientListExport.cell("a\nb")).isEqualTo("\"a\nb\"")
  }

  @Test
  fun aNameThatAFormulaCouldUseIsMadePlainText() {
    assertThat(PatientListExport.cell("=1+1")).isEqualTo("'=1+1")
    assertThat(PatientListExport.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)")
    assertThat(PatientListExport.cell("-2")).isEqualTo("'-2")
    assertThat(PatientListExport.cell("Ann")).isEqualTo("Ann")
  }

  @Test
  fun theFileNameKeepsLettersAndDigitsOfTheName() {
    assertThat(PatientListExport.fileName("Mothers / North clinic!", "20261113"))
      .isEqualTo("patient-list-mothers-north-clinic-20261113.csv")
    assertThat(PatientListExport.fileName("***", "20261113"))
      .isEqualTo("patient-list-list-20261113.csv")
  }
}
