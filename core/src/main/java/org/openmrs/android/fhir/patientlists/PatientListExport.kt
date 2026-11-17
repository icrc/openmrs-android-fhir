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

/**
 * A patient list as a CSV file (RFC 4180, UTF-8 with a byte order mark so that spreadsheets read
 * the accents), one line per member. A patient the user removed and did not send yet is left out,
 * one added and not sent yet is in, marked.
 */
object PatientListExport {
  val BYTE_ORDER_MARK: String = 0xFEFF.toChar().toString()

  fun csv(
    members: List<PatientListMember>,
    headers: List<String>,
    statusIn: String,
    statusAdding: String,
  ): String {
    require(headers.size == COLUMNS) { "Expected $COLUMNS column titles" }
    val lines = mutableListOf(headers.joinToString(",") { cell(it) })
    members
      .filter { it.pending != MemberPending.REMOVE }
      .sortedBy { it.name.lowercase() }
      .forEach { member ->
        lines +=
          listOf(
              member.name,
              member.gender.orEmpty(),
              member.birthDate.orEmpty(),
              member.identifier.orEmpty(),
              member.patientId,
              if (member.pending == MemberPending.ADD) statusAdding else statusIn,
            )
            .joinToString(",") { cell(it) }
      }
    return BYTE_ORDER_MARK + lines.joinToString("\r\n") + "\r\n"
  }

  fun fileName(listName: String, date: String): String {
    val slug =
      listName
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .take(40)
        .ifEmpty { "list" }
    return "patient-list-$slug-$date.csv"
  }

  /**
   * One cell. A text a spreadsheet would run as a formula (it starts with `=`, `+`, `-`, `@` or a
   * tab or return) is made plain text with a leading quote, so a name cannot do anything but be
   * read.
   */
  internal fun cell(value: String): String {
    val safe = if (value.firstOrNull() in FORMULA_STARTS) "'$value" else value
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
      "\"" + safe.replace("\"", "\"\"") + "\""
    } else {
      safe
    }
  }

  const val COLUMNS = 6
  private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
