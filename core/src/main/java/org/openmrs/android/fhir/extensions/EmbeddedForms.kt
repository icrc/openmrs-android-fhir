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
import ca.uhn.fhir.parser.IParser
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import org.hl7.fhir.r4.model.Questionnaire

/**
 * Whether the app uses only the forms shipped with it (the default). Then no questionnaire is
 * downloaded from the server, none the engine already holds is used, and the "fetch questionnaires"
 * action is not offered. It is read by the sync (which runs outside any screen), so it lives in a
 * plain preference file.
 */
object EmbeddedFormsStore {
  private const val PREFS_NAME = "app_forms"
  private const val KEY = "embedded_forms_only"

  @Volatile private var cached: Boolean? = null

  fun get(context: Context): Boolean =
    cached
      ?: context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY, true)
        .also { cached = it }

  fun set(context: Context, embeddedOnly: Boolean) {
    cached = embeddedOnly
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY, embeddedOnly)
      .apply()
  }
}

/** Whether only the questionnaires shipped with the app may be used. */
fun Context.useEmbeddedFormsOnly(): Boolean = EmbeddedFormsStore.get(this)

/**
 * The questionnaires the FHIR engine holds (downloaded from the server), made usable; none when the
 * app is set to embedded forms only.
 */
suspend fun FhirEngine.storedQuestionnaires(
  context: Context,
  parser: IParser
): List<Questionnaire> =
  if (context.useEmbeddedFormsOnly()) {
    emptyList()
  } else {
    search<Questionnaire> {}.map { it.resource.orBundledIfAliased(context, parser) }
  }
