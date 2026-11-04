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

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers whether the answers of each owner (a patient of a group session) were valid when they
 * were last checked. Checking walks the whole form, and the same answers are checked again and
 * again while a session goes on (when the tabs are marked, on every switch, at the final check):
 * answers that did not change since, against the same form, are not walked again.
 *
 * Only an exact repeat is a hit: the same form object and the very same answers (as JSON). A form
 * that was replaced, or any change to the answers, is checked again.
 */
class AnswersValidationMemo {
  private class Entry(val form: Any, val answers: String, val valid: Boolean)

  private val entries = ConcurrentHashMap<String, Entry>()

  suspend fun isValid(
    owner: String,
    form: Any,
    answers: String,
    check: suspend () -> Boolean,
  ): Boolean {
    val known = entries[owner]
    if (known != null && known.form === form && known.answers == answers) return known.valid
    return check().also { entries[owner] = Entry(form, answers, it) }
  }

  fun clear() = entries.clear()
}
