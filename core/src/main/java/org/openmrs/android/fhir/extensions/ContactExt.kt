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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind

/**
 * Opens the phone app with [number] ready to call. It only dials once the user presses the call
 * button there (no permission needed, and nothing is called by mistake).
 */
fun Context.dialNumber(number: String) {
  val digits = number.trim().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
  if (digits.isEmpty()) return
  startSafely(
    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(digits))),
  )
}

/** Opens the mail app with a message to [address]. */
fun Context.composeEmail(address: String) {
  startSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address))))
}

private fun Context.startSafely(intent: Intent) {
  try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: ActivityNotFoundException) {
    AppMessage.show(this, R.string.no_app_for_action, MessageKind.Error)
  }
}
