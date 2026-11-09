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
import android.net.Uri
import com.google.android.fhir.datacapture.QuestionnaireFragment
import java.io.File
import java.security.MessageDigest
import timber.log.Timber

/**
 * The form library keeps the questionnaire it is given as a string in the arguments of its fragment
 * and again in the state of its view model, and Android saves both when the app goes to the
 * background. A large form (the assessment form is 330 KB) made that saved state over the size
 * Android accepts (`TransactionTooLargeException`), so the app crashed when it was left and
 * resumed. The questionnaire is therefore kept in a file and the library is given its address: the
 * saved state holds a few bytes. A questionnaire is the definition of a form, never patient data.
 */
internal object QuestionnaireFiles {
  private const val DIRECTORY = "questionnaires"

  /** The keys of the library's own arguments. */
  private const val STRING_ARGUMENT = "questionnaire"
  private const val URI_ARGUMENT = "questionnaire-uri"
  private const val KEEP_MILLIS = 14L * 24 * 60 * 60 * 1000

  /** The address of the file holding [json]. */
  fun uriOf(context: Context, json: String): Uri =
    Uri.fromFile(fileFor(File(context.filesDir, DIRECTORY), json))

  /**
   * The file holding [json] in [directory], named after its content: the same form is written once.
   */
  fun fileFor(directory: File, json: String): File {
    directory.mkdirs()
    val file = File(directory, sha256(json) + ".json")
    if (!file.exists()) {
      val partial = File(directory, file.name + ".tmp")
      partial.writeText(json, Charsets.UTF_8)
      if (!partial.renameTo(file)) {
        partial.delete()
        error("Could not write the questionnaire file")
      }
    } else {
      file.setLastModified(System.currentTimeMillis())
    }
    removeOld(directory, keep = file)
    return file
  }

  /** The questionnaire a form fragment was built with, whichever way it was given. */
  fun jsonOf(context: Context, fragment: QuestionnaireFragment): String {
    val arguments = fragment.arguments ?: return ""
    arguments.getString(STRING_ARGUMENT)?.let {
      return it
    }
    @Suppress("DEPRECATION") val uri = arguments.getParcelable<Uri>(URI_ARGUMENT)
    return try {
      uri
        ?.let { context.contentResolver.openInputStream(it)?.use { s -> s.reader().readText() } }
        .orEmpty()
    } catch (e: Exception) {
      Timber.w(e, "Could not read the questionnaire file")
      ""
    }
  }

  private fun removeOld(directory: File, keep: File) {
    val limit = System.currentTimeMillis() - KEEP_MILLIS
    directory.listFiles()?.forEach { if (it != keep && it.lastModified() < limit) it.delete() }
  }

  private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
      "%02x".format(it)
    }
}
