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

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.openmrs.android.fhir.extensions.pruneOtherLanguageTranslations

/**
 * The questionnaires bundled in the app are stored in a compact form (see
 * `tools/compact_questionnaires.py`) and turned back into plain FHIR JSON here, before the FHIR
 * parser sees them; a questionnaire downloaded from the server is already plain and is returned
 * untouched. The compact form only differs in two ways:
 * - translations are written `"text:i18n": {"fr": "...", "es": "..."}` instead of the verbose
 *   `"_text": {"extension": [{"url": ".../translation", ...}]}`;
 * - the first key, `"$compact"`, lists short names for the URL prefixes that open `url` and
 *   `system` values: `"@h/questionnaire-itemControl"` stands for
 *   `"http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl"`.
 */
private const val COMPACT_MARKER = "\$compact"
private const val I18N_SUFFIX = ":i18n"
private const val TRANSLATION_URL = "http://hl7.org/fhir/StructureDefinition/translation"

/**
 * A questionnaire copied from the bundled files to a server keeps the aliases in its urls (and
 * loses the translations: the server's parser does not know `:i18n`). The data-capture library does
 * not know the aliases, so such a copy shows none of what they stand for (radio buttons, rules that
 * hide or show a question, hidden fields, limits). This turns them back into the full urls and
 * touches nothing else.
 */
fun expandAliasedUrls(json: String): String {
  val prefixes =
    JSONObject()
      .put("h", "http://hl7.org/fhir/StructureDefinition/")
      .put("s", "http://hl7.org/fhir/uv/sdc/StructureDefinition/")
      .put("o", "http://fhir.openmrs.org/ext/")
      .put("q", "http://hl7.org/fhir/questionnaire-item-control")
  return expandObject(JSONObject(json), prefixes, null).toString()
}

/**
 * The plain FHIR JSON of a bundled questionnaire. With a [locale] only the translations in the
 * language of that locale are written (the same ones [pruneOtherLanguageTranslations] would keep):
 * the form library shows one language, and the others are most of what a form made of translations
 * weighs, to write, parse and hold in memory.
 */
fun expandCompactQuestionnaire(json: String, locale: Locale? = null): String {
  if (!json.trimStart().startsWith("{\"$COMPACT_MARKER\"")) return json
  val root = JSONObject(json)
  val prefixes = root.getJSONObject(COMPACT_MARKER)
  root.remove(COMPACT_MARKER)
  val keep =
    locale?.let {
      LanguageFilter(it.language.lowercase(Locale.ROOT), it.toLanguageTag().lowercase(Locale.ROOT))
    }
  return expandObject(root, prefixes, keep).toString()
}

/** Which translations stay: the language itself or one of its regional variants. */
private class LanguageFilter(private val language: String, private val tag: String) {
  fun keeps(lang: String): Boolean {
    val l = lang.lowercase(Locale.ROOT)
    return l == tag || l.substringBefore('-') == language
  }
}

private fun expandValue(value: Any?, prefixes: JSONObject, keep: LanguageFilter?): Any? =
  when (value) {
    is JSONObject -> expandObject(value, prefixes, keep)
    is JSONArray ->
      JSONArray().also { out ->
        for (i in 0 until value.length()) out.put(expandValue(value.get(i), prefixes, keep))
      }
    else -> value
  }

private fun expandObject(
  source: JSONObject,
  prefixes: JSONObject,
  keep: LanguageFilter?,
): JSONObject {
  val out = JSONObject()
  val keys = source.keys()
  while (keys.hasNext()) {
    val key = keys.next()
    val value = source.get(key)
    when {
      key.endsWith(I18N_SUFFIX) && value is JSONObject -> {
        val kept = translations(value, keep)
        if (kept != null) out.put("_" + key.removeSuffix(I18N_SUFFIX), kept)
      }
      (key == "url" || key == "system") && value is String && value.startsWith("@") -> {
        val slash = value.indexOf('/')
        val prefix = if (slash > 1) prefixes.optString(value.substring(1, slash)) else ""
        if (prefix.isEmpty()) {
          out.put(key, value)
        } else {
          out.put(key, prefix + value.substring(slash + 1))
        }
      }
      key == "extension" && keep != null && value is JSONArray -> {
        // Translations written the long way (`_text.extension`) are filtered like the compact ones.
        val kept = withoutOtherLanguages(expandValue(value, prefixes, keep) as JSONArray, keep)
        if (kept.length() > 0) out.put(key, kept)
      }
      key.startsWith("_") && keep != null && value is JSONObject -> {
        val expanded = expandObject(value, prefixes, keep)
        if (expanded.length() > 0) out.put(key, expanded)
      }
      else -> out.put(key, expandValue(value, prefixes, keep))
    }
  }
  return out
}

/**
 * The extensions without the translations into other languages (a translation without a language
 * stays), as [pruneOtherLanguageTranslations] leaves them.
 */
private fun withoutOtherLanguages(extensions: JSONArray, keep: LanguageFilter): JSONArray {
  val kept = JSONArray()
  for (i in 0 until extensions.length()) {
    val extension = extensions.get(i)
    if (extension is JSONObject && extension.optString("url") == TRANSLATION_URL) {
      val inner = extension.optJSONArray("extension")
      var lang: String? = null
      if (inner != null) {
        for (j in 0 until inner.length()) {
          val part = inner.optJSONObject(j) ?: continue
          if (part.optString("url") == "lang") {
            lang = part.optString("valueCode").takeIf { it.isNotEmpty() }
          }
        }
      }
      if (lang != null && !keep.keeps(lang)) continue
    }
    kept.put(extension)
  }
  return kept
}

/** `{"fr": "Père"}` as the `extension` list the FHIR translation extension uses. */
private fun translations(byLanguage: JSONObject, keep: LanguageFilter?): JSONObject? {
  val extensions = JSONArray()
  val languages = byLanguage.keys()
  while (languages.hasNext()) {
    val language = languages.next()
    if (keep != null && !keep.keeps(language)) continue
    extensions.put(
      JSONObject()
        .put("url", TRANSLATION_URL)
        .put(
          "extension",
          JSONArray()
            .put(JSONObject().put("url", "lang").put("valueCode", language))
            .put(
              JSONObject().put("url", "content").put("valueString", byLanguage.getString(language)),
            ),
        ),
    )
  }
  return if (extensions.length() == 0) null else JSONObject().put("extension", extensions)
}
