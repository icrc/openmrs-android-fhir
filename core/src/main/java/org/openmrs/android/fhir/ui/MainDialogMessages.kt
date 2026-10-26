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
package org.openmrs.android.fhir.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.google.android.material.color.MaterialColors
import org.openmrs.android.fhir.R

/** The danger colour of the theme, red when the theme has none. */
fun Context.errorColor(): Int =
  MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError, Color.RED)

/**
 * What logging out does with the data, as set in Settings: deleted (and then, in red, how many
 * resources were never uploaded and are lost with it) or kept.
 */
fun Context.logoutMessage(
  clearData: Boolean,
  pending: Int,
  canEndProviderSession: Boolean,
  toIdentify: Int,
): CharSequence {
  val message = SpannableStringBuilder()
  message.append(
    getString(if (clearData) R.string.logout_message_delete else R.string.logout_message_keep),
  )
  if (pending > 0) {
    message.append("\n\n")
    val start = message.length
    message.append(
      resources.getQuantityString(
        if (clearData) R.plurals.logout_pending_lost else R.plurals.logout_pending_kept,
        pending,
        pending,
      ),
    )
    message.setSpan(
      StyleSpan(Typeface.BOLD),
      start,
      message.length,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    if (clearData) {
      message.setSpan(
        ForegroundColorSpan(errorColor()),
        start,
        message.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }
  }
  if (!canEndProviderSession) {
    message.append("\n\n")
    message.append(getString(R.string.logout_offline_note))
  }
  // Records for patients "to identify after" are not in the upload count: say what happens.
  if (toIdentify > 0) {
    message.append("\n\n")
    message.append(
      getString(
        if (clearData) R.string.identify_logout_lost else R.string.identify_logout_kept,
        toIdentify,
      ),
    )
  }
  return message
}

/** What "Delete patient data" is about to remove, the part never uploaded in bold. */
fun Context.deletePatientDataMessage(patients: Int, notUploaded: Int): CharSequence {
  val message = SpannableStringBuilder(getString(R.string.delete_patient_data_message, patients))
  if (notUploaded > 0) {
    message.append("\n\n")
    val start = message.length
    message.append(getString(R.string.delete_patient_data_unsynced, notUploaded))
    message.setSpan(
      StyleSpan(Typeface.BOLD),
      start,
      message.length,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
  }
  return message
}
