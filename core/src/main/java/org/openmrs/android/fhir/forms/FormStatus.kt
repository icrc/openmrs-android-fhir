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

import ca.uhn.fhir.parser.IParser
import java.security.MessageDigest
import org.hl7.fhir.r4.model.Questionnaire

/** How the form in use compares with what the server publishes. */
enum class FormState {
  /** The server was not asked yet. */
  NOT_CHECKED,

  /** The form in use says what the server's copy says. */
  UP_TO_DATE,

  /** The server's copy is different from the one in use. */
  UPDATE_AVAILABLE,

  /** The server has a form that is not on this device. */
  NEW_ON_SERVER,

  /** The device has a form the server does not publish. */
  NOT_ON_SERVER,

  /** The server's copy cannot be used: it is written with the app's short codes. */
  NOT_USABLE,
}

/** Where the form that is opened comes from. */
enum class FormSource {
  /** The copy that ships with the app. */
  APP,

  /** The copy taken from the server. */
  SERVER,

  /** There is none: the form is only on the server. */
  NONE,
}

data class FormStatus(
  val id: String,
  val title: String,
  val state: FormState,
  val inUse: FormSource,
  /** The version label the server gives its copy, if it does. */
  val serverVersion: String? = null,
)

/**
 * What is known about one form, as fingerprints of what each copy says (`null`: there is no such
 * copy).
 *
 * @property app the copy that ships with the app
 * @property held the copy of the device that comes from the server and is in use
 * @property server the copy the server publishes
 * @property serverUsable false for a server copy written with the app's own short codes: it has
 *   lost the translations and the form library cannot read it, so the app's copy stands in for it
 */
class FormVersions(
  val id: String,
  val title: String,
  val app: String?,
  val held: String?,
  val server: String?,
  val serverVersion: String? = null,
  val serverUsable: Boolean = true,
  val checked: Boolean = true,
) {
  /** The fingerprint of the copy that is opened. */
  val inUseFingerprint: String?
    get() = held ?: app

  val inUse: FormSource
    get() =
      when {
        held != null -> FormSource.SERVER
        app != null -> FormSource.APP
        else -> FormSource.NONE
      }

  fun status(): FormStatus =
    FormStatus(
      id = id,
      title = title,
      state =
        when {
          !checked -> FormState.NOT_CHECKED
          server == null -> FormState.NOT_ON_SERVER
          !serverUsable -> FormState.NOT_USABLE
          inUseFingerprint == null -> FormState.NEW_ON_SERVER
          inUseFingerprint == server -> FormState.UP_TO_DATE
          else -> FormState.UPDATE_AVAILABLE
        },
      inUse = inUse,
      serverVersion = serverVersion,
    )
}

/**
 * What a form says, as a fingerprint: the same for the copy that ships with the app and the one the
 * server serves when they say the same, whatever the server or the engine wrote around it (the
 * version and time of the resource, the narrative). The text is written the way the FHIR library
 * writes it, so the order of the keys and the spelling of numbers do not matter.
 */
fun Questionnaire.fingerprint(parser: IParser): String {
  val normalized =
    copy().also {
      it.id = idElement.idPart
      it.meta = null
      it.text = null
    }
  val digest =
    MessageDigest.getInstance("SHA-256")
      .digest(parser.encodeResourceToString(normalized).toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it) }
}
