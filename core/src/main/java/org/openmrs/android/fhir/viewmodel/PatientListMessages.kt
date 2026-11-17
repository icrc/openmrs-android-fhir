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
package org.openmrs.android.fhir.viewmodel

import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.patientlists.PatientListState
import org.openmrs.android.fhir.patientlists.SendResult

data class PatientListMessage(
  val res: Int,
  val count: Int = 0,
  val plural: Boolean = false,
  val isError: Boolean = false,
)

internal fun PatientListEntry.stateLabel(): Int? =
  when {
    error != null -> R.string.plist_state_refused
    state == PatientListState.NEW -> R.string.plist_state_new
    state == PatientListState.EDITED -> R.string.plist_state_edited
    state == PatientListState.DELETING -> R.string.plist_state_deleting
    pendingChanges > 0 -> R.string.plist_state_edited
    else -> null
  }

internal fun PatientListEntry.matches(text: String): Boolean {
  val wanted = text.trim()
  if (wanted.isEmpty()) return true
  return wanted.split(Regex("\\s+")).all { word ->
    name.contains(word, ignoreCase = true) ||
      description.orEmpty().contains(word, ignoreCase = true) ||
      locationName.orEmpty().contains(word, ignoreCase = true)
  }
}

internal fun SendResult.toMessage(): PatientListMessage =
  when {
    unreachable -> PatientListMessage(R.string.plists_send_unreachable, isError = true)
    refused > 0 ->
      PatientListMessage(R.plurals.plists_refused, refused, plural = true, isError = true)
    dropped > 0 ->
      PatientListMessage(R.plurals.plists_dropped, dropped, plural = true, isError = true)
    sent > 0 -> PatientListMessage(R.plurals.plists_sent, sent, plural = true)
    else -> PatientListMessage(R.string.plists_send_nothing)
  }
