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

import android.content.Context
import ca.uhn.fhir.parser.IParser
import java.io.IOException
import java.util.Locale
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.extensions.getJsonFileNames
import org.openmrs.android.fhir.extensions.readFileFromAssets

/**
 * Folder under `assets/` holding the questionnaires that ship with the app, one `<questionnaire
 * id>.json` file each. They are the offline fallback for forms that have not been synced yet, and
 * what "fetch more questionnaires" in the settings compares against.
 */
const val BUNDLED_QUESTIONNAIRES_DIR = "questionnaires"

/** Ids of the questionnaires bundled in the app. */
fun Context.bundledQuestionnaireIds(): Set<String> =
  try {
    assets
      .list(BUNDLED_QUESTIONNAIRES_DIR)
      .orEmpty()
      .filter { it.endsWith(".json") }
      .map { it.removeSuffix(".json") }
      .toSet()
  } catch (e: IOException) {
    emptySet()
  }

/**
 * The bundled definition of the questionnaire with this id, or `null` if it is not bundled. With a
 * [locale] it only carries the translations of that language (see [expandCompactQuestionnaire]).
 */
fun Context.readBundledQuestionnaire(id: String, locale: Locale? = null): String? =
  try {
    readFileFromAssets("$BUNDLED_QUESTIONNAIRES_DIR/$id.json", locale)
  } catch (e: IOException) {
    null
  }

/**
 * The bundled questionnaire made for this encounter type, in the language of [locale]. Only the
 * files that mention the type are parsed: reading every form of the app to find one is seconds of
 * work on a tablet.
 */
fun Context.readBundledQuestionnaireForEncounterType(
  encounterType: String,
  parser: IParser,
  locale: Locale = Locale.getDefault(),
): Questionnaire? =
  bundledQuestionnaireIds().sorted().firstNotNullOfOrNull { id ->
    val raw =
      try {
        assets.open("$BUNDLED_QUESTIONNAIRES_DIR/$id.json").bufferedReader().use { it.readText() }
      } catch (e: IOException) {
        return@firstNotNullOfOrNull null
      }
    if (!raw.contains(encounterType)) return@firstNotNullOfOrNull null
    parser
      .parseResource(Questionnaire::class.java, expandCompactQuestionnaire(raw, locale))
      .takeIf { candidate ->
        candidate.code.any {
          it.hasSystem() &&
            it.system == "http://fhir.openmrs.org/code-system/encounter-type" &&
            it.hasCode() &&
            it.code == encounterType
        }
      }
  }

/**
 * Every questionnaire shipped with the app: the JSON files in the assets root plus the bundled
 * folder. Bundled ones whose id is in [skipIds] (for example because the FHIR engine already has
 * them) are not even parsed, which keeps this cheap once the forms have been synced.
 */
fun Context.readAssetQuestionnaires(
  parser: IParser,
  skipIds: Set<String> = emptySet(),
): List<Questionnaire> {
  val fromRoot =
    getJsonFileNames().mapNotNull { fileName ->
      readFileFromAssets(fileName)
        .takeIf { it.isNotEmpty() }
        ?.let { parser.parseResource(Questionnaire::class.java, it) }
    }
  val fromFolder =
    (bundledQuestionnaireIds() - skipIds).mapNotNull { id ->
      readBundledQuestionnaire(id)
        ?.takeIf { it.isNotEmpty() }
        ?.let { parser.parseResource(Questionnaire::class.java, it) }
    }
  return fromRoot + fromFolder
}
