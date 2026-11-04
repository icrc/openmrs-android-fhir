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
package org.openmrs.android.fhir.forms.diagnosis

import java.io.Reader

/**
 * Reads the CSV of the diagnoses a picker offers:
 * `uuid,name:en,name:fr,name:es,name:ru,name:ar,code`, the columns in any order (others are
 * ignored):
 * - `uuid`: the uuid of the OpenMRS concept, the only thing the server needs.
 * - one `name:<language>` column per language (`name:en`, `name:fr`...), what the user reads; the
 *   name in the language of the app is shown when there is one, else `name:en`, else the first
 *   language of the file. A column `name`, with no language, is the default name instead of
 *   `name:en`. A language with no name for a concept is left empty.
 * - `code`: the ICD-10 code of the concept, what the user searches by and sees next to the name; it
 *   may be empty.
 *
 * A concept with several codes has one row for each code (the same uuid, the names repeated); the
 * rows of a concept become one [Diagnosis] (the first five codes, in the order of the file).
 */
object DiagnosisCsv {
  private const val MAX_CODES = 5
  private const val UUID_COLUMN = "uuid"
  private const val CODE_COLUMN = "code"
  private const val NAME_COLUMN = "name"
  private const val NAME_LANGUAGE_PREFIX = "name:"
  private val WHITESPACE = Regex("\\s+")

  /**
   * The name with its spaces trimmed and runs of white space made one (a regex only when needed).
   */
  private fun tidy(raw: String): String {
    val text = raw.trim()
    var odd = false
    for (i in text.indices) {
      val c = text[i]
      if (c < ' ' || (c == ' ' && text[i - 1] == ' ')) {
        odd = true
        break
      }
    }
    return if (odd) text.replace(WHITESPACE, " ") else text
  }

  private class Concept {
    val names = LinkedHashMap<String, String>(2) // "" is the name without a language
    val codes = LinkedHashSet<String>(2)
  }

  /** @throws InvalidDiagnosisCatalogException when the file is not that CSV */
  fun parse(reader: Reader, id: String = DiagnosisCatalogs.DEFAULT_ID): DiagnosisCatalog {
    val records = CsvRecords(reader)
    val header = records.next()?.map { it.removePrefix("\uFEFF").trim().lowercase() }.orEmpty()
    val uuidColumn = header.indexOf(UUID_COLUMN)
    val codeColumn = header.indexOf(CODE_COLUMN)
    // The column of each name: "" for the one without a language, else the language tag.
    val nameColumns =
      header.withIndex().mapNotNull { (index, title) ->
        when {
          title == NAME_COLUMN -> index to ""
          title.startsWith(NAME_LANGUAGE_PREFIX) ->
            title
              .removePrefix(NAME_LANGUAGE_PREFIX)
              .trim()
              .replace('_', '-')
              .takeIf { DiagnosisCatalogs.LANGUAGE_TAG.matches(it) }
              ?.let { index to it }
          else -> null
        }
      }
    if (uuidColumn < 0 || codeColumn < 0 || nameColumns.isEmpty()) {
      throw InvalidDiagnosisCatalogException(
        "The CSV needs the columns uuid, code and a name per language (name:en, name:es...)",
      )
    }
    val concepts = LinkedHashMap<String, Concept>(60_000)
    val widest = maxOf(uuidColumn, codeColumn, nameColumns.maxOf { it.first })
    while (true) {
      val row = records.next() ?: break
      if (row.size <= widest) continue
      val uuid = row[uuidColumn].trim()
      if (uuid.isEmpty()) continue
      val concept = concepts.getOrPut(uuid) { Concept() }
      nameColumns.forEach { (index, language) ->
        val text = tidy(row[index])
        if (text.isNotEmpty()) concept.names.putIfAbsent(language, text)
      }
      row[codeColumn].trim().takeIf { it.isNotEmpty() }?.let { concept.codes.add(it) }
    }
    val diagnoses =
      concepts
        .mapNotNull { (uuid, concept) ->
          val name =
            concept.names[""] ?: concept.names["en"] ?: concept.names.values.firstOrNull().orEmpty()
          if (name.isEmpty()) return@mapNotNull null
          val icd = concept.codes.take(MAX_CODES)
          val translations =
            concept.names
              .filter { (language, text) -> language.isNotEmpty() && text != name }
              .mapKeys { it.key.lowercase() }
          Diagnosis(uuid, name, icd, translations)
        }
        .sortedBy { it.name.lowercase() }
    if (diagnoses.isEmpty()) throw InvalidDiagnosisCatalogException("The CSV has no diagnoses")
    return DiagnosisCatalog(id, diagnoses)
  }
}

/** The records of a CSV (RFC 4180): quoted fields, doubled quotes, line breaks inside quotes. */
internal class CsvRecords(private val reader: Reader) {
  private var pushedBack = -2

  private fun read(): Int =
    if (pushedBack != -2) pushedBack.also { pushedBack = -2 } else reader.read()

  /** The next record, or null at the end of the file. A blank line is a record with one field. */
  fun next(): List<String>? {
    var c = read()
    if (c == -1) return null
    val fields = ArrayList<String>(8)
    val field = StringBuilder()
    var quoted = false
    while (true) {
      when {
        c == -1 -> {
          fields.add(field.toString())
          return fields
        }
        quoted -> {
          if (c == '"'.code) {
            val after = read()
            if (after == '"'.code) {
              field.append('"')
            } else {
              quoted = false
              pushedBack = after
            }
          } else {
            field.append(c.toChar())
          }
        }
        c == '"'.code && field.isEmpty() -> quoted = true
        c == ','.code -> {
          fields.add(field.toString())
          field.setLength(0)
        }
        c == '\n'.code -> {
          fields.add(field.toString())
          return fields
        }
        c == '\r'.code -> Unit
        else -> field.append(c.toChar())
      }
      c = read()
    }
  }
}
