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
import android.util.JsonReader
import android.util.JsonToken
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import java.io.IOException
import java.io.StringReader
import java.util.Locale
import org.hl7.fhir.r4.model.Questionnaire
import timber.log.Timber

/**
 * What the lists of the app need to know about a form: its name in every language and the encounter
 * types it is for. Reading that from the questionnaires themselves means parsing all of them, each
 * a hundred kilobytes of questions and translations, every time a list opens.
 */
class FormSummary(
  val id: String,
  val title: String?,
  /** The title in each language that has one (lower case language codes). */
  val titlesByLanguage: Map<String, String>,
  /** The codes of the encounter types this form is for. */
  val encounterTypeCodes: List<String>,
) {
  /** The title in [language] (`fr`, ...), or the base title when it has no translation. */
  fun titleIn(language: String): String? = titlesByLanguage[language] ?: title
}

private const val ENCOUNTER_TYPE_SYSTEM = "http://fhir.openmrs.org/code-system/encounter-type"
private const val TRANSLATION_URL = "http://hl7.org/fhir/StructureDefinition/translation"

/** The summary of a questionnaire that is already in memory. */
fun Questionnaire.toFormSummary(): FormSummary =
  FormSummary(
    id = idElement.idPart,
    title = title,
    titlesByLanguage =
      titleElement
        ?.extension
        .orEmpty()
        .filter { it.url == TRANSLATION_URL }
        .mapNotNull { translation ->
          val lang =
            translation.getExtensionByUrl("lang")?.value?.primitiveValue()?.lowercase(Locale.ROOT)
          val text = translation.getExtensionByUrl("content")?.value?.primitiveValue()
          if (lang != null && text != null) lang to text else null
        }
        .toMap(),
    encounterTypeCodes =
      code
        .filter { it.hasSystem() && it.system == ENCOUNTER_TYPE_SYSTEM && it.hasCode() }
        .map { it.code },
  )

/** Summaries of the bundled forms, read once: the files do not change while the app runs. */
@Volatile private var bundledSummaries: List<FormSummary>? = null

/**
 * The summaries of the questionnaires shipped with the app: the JSON files in the assets root and
 * the bundled folder. Only the few fields above are read, skipping the questions, so all of them
 * together cost a fraction of parsing one form.
 */
fun Context.bundledFormSummaries(): List<FormSummary> =
  bundledSummaries
    ?: synchronized(FormSummary::class.java) {
      bundledSummaries
        ?: (getJsonFileNames().map { it to it } +
            bundledQuestionnaireIds().sorted().map { it to "$BUNDLED_QUESTIONNAIRES_DIR/$it.json" })
          .mapNotNull { (name, path) -> readBundledSummary(name, path) }
          .also { bundledSummaries = it }
    }

private fun Context.readBundledSummary(name: String, path: String): FormSummary? =
  try {
    assets.open(path).bufferedReader().use {
      parseSummary(name.removeSuffix(".json"), it.readText())
    }
  } catch (e: IOException) {
    Timber.w(e, "Could not read the summary of $path")
    null
  } catch (e: IllegalStateException) {
    Timber.w(e, "Could not read the summary of $path")
    null
  }

internal fun parseSummary(fallbackId: String, json: String): FormSummary? {
  val prefixes = mutableMapOf<String, String>()
  var id: String? = null
  var title: String? = null
  val titles = mutableMapOf<String, String>()
  val codes = mutableListOf<String>()
  JsonReader(StringReader(json)).use { reader ->
    if (reader.peek() != JsonToken.BEGIN_OBJECT) return null
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "\$compact" -> {
          reader.beginObject()
          while (reader.hasNext()) prefixes[reader.nextName()] = reader.nextString()
          reader.endObject()
        }
        "resourceType" -> if (reader.nextString() != "Questionnaire") return null
        "id" -> id = reader.nextString()
        "title" -> title = reader.nextString()
        "title:i18n" -> {
          reader.beginObject()
          while (reader.hasNext()) titles[reader.nextName().lowercase(Locale.ROOT)] =
            reader.nextString()
          reader.endObject()
        }
        "code" -> {
          reader.beginArray()
          while (reader.hasNext()) {
            var system: String? = null
            var value: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
              when (reader.nextName()) {
                "system" -> system = expandPrefix(reader.nextString(), prefixes)
                "code" -> value = reader.nextString()
                else -> reader.skipValue()
              }
            }
            reader.endObject()
            if (system == ENCOUNTER_TYPE_SYSTEM && value != null) codes.add(value)
          }
          reader.endArray()
        }
        else -> reader.skipValue()
      }
    }
  }
  return FormSummary(id ?: fallbackId, title, titles, codes)
}

private fun expandPrefix(value: String, prefixes: Map<String, String>): String {
  if (!value.startsWith("@")) return value
  val slash = value.indexOf('/')
  val prefix = if (slash > 1) prefixes[value.substring(1, slash)] else null
  return if (prefix == null) value else prefix + value.substring(slash + 1)
}

/**
 * Summaries of every form the app can open: the engine's (when the app uses the server's forms) and
 * the bundled ones it lacks.
 */
suspend fun FhirEngine.allFormSummaries(context: Context): List<FormSummary> {
  val bundled = context.bundledFormSummaries().associateBy { it.id }
  val stored =
    if (context.useEmbeddedFormsOnly()) {
      emptyList()
    } else {
      search<Questionnaire> {}
        .map { result ->
          val questionnaire = result.resource
          // A copy with compact aliases has lost its translations: the bundled summary stands in.
          if (questionnaire.hasCompactAliases()) {
            bundled[questionnaire.idElement.idPart] ?: questionnaire.toFormSummary()
          } else {
            questionnaire.toFormSummary()
          }
        }
    }
  val storedIds = stored.map { it.id }.toSet()
  return stored + context.bundledFormSummaries().filter { it.id !in storedIds }
}
