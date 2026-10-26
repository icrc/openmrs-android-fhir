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
package org.openmrs.android.fhir.auth

import android.content.Context
import org.openmrs.android.fhir.config.AppConfig

/**
 * "Continue without signing in": the user works on this device without a server session. Nothing is
 * downloaded, so they cannot pick a location or patient list; what they enter stays local until
 * they sign in and sync.
 *
 * Only available when the `skip_login` setting is on.
 */
object GuestSession {
  private const val PREFS_NAME = "guest_session"
  private const val KEY_ACTIVE = "active"
  private const val KEY_COMPLETION_PENDING = "completion_pending"

  /** Extra on the login activities: they were opened from a guest session to sign in. */
  const val EXTRA_SIGN_IN_FROM_GUEST = "sign_in_from_guest"

  fun isAllowed(context: Context): Boolean = AppConfig.get(context).guestMode

  fun isActive(context: Context): Boolean =
    isAllowed(context) && prefs(context).getBoolean(KEY_ACTIVE, false)

  fun start(context: Context) {
    set(context, true)
    // Data entered from now on has to be completed (location, identifiers) after sign-in.
    prefs(context).edit().putBoolean(KEY_COMPLETION_PENDING, true).apply()
  }

  /**
   * Something was saved without a location (as a guest, or signed in before choosing one): it is
   * completed once there is a location, like what a guest entered.
   */
  fun markCompletionPending(context: Context) {
    prefs(context).edit().putBoolean(KEY_COMPLETION_PENDING, true).apply()
  }

  /** True until the data entered as a guest has been completed after signing in. */
  fun isCompletionPending(context: Context): Boolean =
    prefs(context).getBoolean(KEY_COMPLETION_PENDING, false)

  fun markCompleted(context: Context) {
    prefs(context).edit().putBoolean(KEY_COMPLETION_PENDING, false).apply()
  }

  /** Called once the user has signed in; the data entered as a guest stays in the database. */
  fun end(context: Context) = set(context, false)

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private fun set(context: Context, active: Boolean) {
    prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply()
  }
}
