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
import java.util.concurrent.ConcurrentHashMap
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent

/** An extension url or coding system written as `@h/...`: see [expandAliasedUrls]. */
private fun String?.isAlias() = this != null && length > 2 && this[0] == '@' && this[2] == '/'

/** Whether this copy of a questionnaire carries the compact aliases (see [expandAliasedUrls]). */
fun Questionnaire.hasCompactAliases(): Boolean =
  extension.any { it.url.isAlias() } || item.any { it.hasCompactAliases() }

private fun QuestionnaireItemComponent.hasCompactAliases(): Boolean =
  extension.any { it.url.isAlias() } || item.any { it.hasCompactAliases() }

/** The same questionnaire with the aliases turned into full urls. */
fun Questionnaire.withAliasesExpanded(parser: IParser): Questionnaire =
  parser.parseResource(
    Questionnaire::class.java,
    expandAliasedUrls(parser.encodeResourceToString(this)),
  )

/** Bundled questionnaires parsed once: they are large. Each caller gets its own copy to edit. */
private val bundledCopies = ConcurrentHashMap<String, Questionnaire>()

/** The bundled questionnaire with this id, or `null` if the app does not ship it. */
fun Context.bundledQuestionnaire(id: String, parser: IParser): Questionnaire? {
  bundledCopies[id]?.let {
    return it.copy()
  }
  val json = readBundledQuestionnaire(id)?.takeIf { it.isNotEmpty() } ?: return null
  val parsed = parser.parseResource(Questionnaire::class.java, json)
  bundledCopies[id] = parsed
  return parsed.copy()
}

/**
 * What to open for a questionnaire the engine has. A copy with compact aliases is not usable as it
 * is, and it has lost its translations, so the bundled version of the same questionnaire replaces
 * it; one the app does not ship gets its aliases expanded.
 */
fun Questionnaire.orBundledIfAliased(context: Context, parser: IParser): Questionnaire =
  if (!hasCompactAliases()) {
    this
  } else {
    context.bundledQuestionnaire(idElement.idPart, parser) ?: withAliasesExpanded(parser)
  }
