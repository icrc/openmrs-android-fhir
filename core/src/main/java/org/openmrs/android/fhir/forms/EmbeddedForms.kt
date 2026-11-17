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
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import org.hl7.fhir.r4.model.Questionnaire

/**
 * Whether the app uses only the forms shipped with it (the default). Then no questionnaire is
 * downloaded by the sync, and none the engine holds is used, except the ones the user took from the
 * server on purpose ([AcceptedFormsStore]). It is read by the sync (which runs outside any screen),
 * so it lives in a plain preference file.
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

/**
 * The forms the user took from the server while the app uses its own (`FormUpdater`): their ids.
 * The server's copy of these is used instead of the app's, and stays the one in use until the user
 * goes back to the app's version; the other forms are not touched by what the server publishes.
 */
object AcceptedFormsStore {
  private const val PREFS_NAME = "app_forms"
  private const val KEY = "accepted_server_forms"

  @Volatile private var cached: Set<String>? = null

  fun ids(context: Context): Set<String> =
    cached
      ?: context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(KEY, emptySet())
        .orEmpty()
        .toSet()
        .also { cached = it }

  fun add(context: Context, ids: Collection<String>) = save(context, ids(context) + ids)

  fun remove(context: Context, id: String) = save(context, ids(context) - id)

  fun clear(context: Context) = save(context, emptySet())

  private fun save(context: Context, ids: Set<String>) {
    cached = ids
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putStringSet(KEY, ids)
      .apply()
  }
}

/** Whether only the questionnaires shipped with the app may be used. */
fun Context.useEmbeddedFormsOnly(): Boolean = EmbeddedFormsStore.get(this)

/**
 * Whether the form [id] is read from the FHIR engine (the server's copy): every form is when the
 * app uses the server's forms, and otherwise only the ones the user took from the server.
 */
fun usesStoredForm(embeddedOnly: Boolean, accepted: Set<String>, id: String): Boolean =
  !embeddedOnly || id in accepted

fun Context.usesStoredForm(id: String): Boolean =
  usesStoredForm(useEmbeddedFormsOnly(), AcceptedFormsStore.ids(this), id)

/**
 * The questionnaires the FHIR engine holds (downloaded from the server) that are in use, made
 * usable: all of them, or when the app uses its own forms only the ones taken from the server.
 */
suspend fun FhirEngine.storedQuestionnaires(
  context: Context,
  parser: IParser,
): List<Questionnaire> = formsInUse(context).map { it.orBundledIfAliased(context, parser) }

/**
 * The forms of the engine that are in use. With the app's own forms only those taken from the
 * server are read, one by one, so a device that once synced every form does not parse them all.
 */
internal suspend fun FhirEngine.formsInUse(context: Context): List<Questionnaire> =
  if (context.useEmbeddedFormsOnly()) {
    AcceptedFormsStore.ids(context).mapNotNull { id ->
      try {
        get<Questionnaire>(id)
      } catch (e: ResourceNotFoundException) {
        null
      }
    }
  } else {
    search<Questionnaire> {}.map { it.resource }
  }
