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

import org.json.JSONArray
import org.json.JSONObject

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
  return expandObject(JSONObject(json), prefixes).toString()
}

fun expandCompactQuestionnaire(json: String): String {
  if (!json.trimStart().startsWith("{\"$COMPACT_MARKER\"")) return json
  val root = JSONObject(json)
  val prefixes = root.getJSONObject(COMPACT_MARKER)
  root.remove(COMPACT_MARKER)
  return expandObject(root, prefixes).toString()
}

private fun expandValue(value: Any?, prefixes: JSONObject): Any? =
  when (value) {
    is JSONObject -> expandObject(value, prefixes)
    is JSONArray ->
      JSONArray().also { out ->
        for (i in 0 until value.length()) out.put(expandValue(value.get(i), prefixes))
      }
    else -> value
  }

private fun expandObject(source: JSONObject, prefixes: JSONObject): JSONObject {
  val out = JSONObject()
  val keys = source.keys()
  while (keys.hasNext()) {
    val key = keys.next()
    val value = source.get(key)
    when {
      key.endsWith(I18N_SUFFIX) && value is JSONObject ->
        out.put("_" + key.removeSuffix(I18N_SUFFIX), translations(value))
      (key == "url" || key == "system") && value is String && value.startsWith("@") -> {
        val slash = value.indexOf('/')
        val prefix = if (slash > 1) prefixes.optString(value.substring(1, slash)) else ""
        if (prefix.isEmpty()) {
          out.put(key, value)
        } else {
          out.put(key, prefix + value.substring(slash + 1))
        }
      }
      else -> out.put(key, expandValue(value, prefixes))
    }
  }
  return out
}

/** `{"fr": "Père"}` as the `extension` list the FHIR translation extension uses. */
private fun translations(byLanguage: JSONObject): JSONObject {
  val extensions = JSONArray()
  val languages = byLanguage.keys()
  while (languages.hasNext()) {
    val language = languages.next()
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
  return JSONObject().put("extension", extensions)
}
