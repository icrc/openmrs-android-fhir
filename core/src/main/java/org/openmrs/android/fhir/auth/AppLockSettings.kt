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

/**
 * How the app lock behaves once an offline login is set up: how long the app may be out of sight
 * before it asks again, whether turning the screen off locks at once, and whether the Recents
 * preview (and screenshots) hide the patient data.
 */
object AppLockSettings {
  private const val PREFS_NAME = "app_lock"
  private const val KEY_GRACE_SECONDS = "grace_seconds"
  private const val KEY_LOCK_ON_SCREEN_OFF = "lock_on_screen_off"
  private const val KEY_HIDE_IN_RECENTS = "hide_in_recents"

  /** What the user can choose for the delay, in seconds (0: ask every time). */
  val GRACE_OPTIONS_SECONDS = listOf(0, 30, 60, 300)
  const val DEFAULT_GRACE_SECONDS = 30

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun graceSeconds(context: Context): Int =
    prefs(context).getInt(KEY_GRACE_SECONDS, DEFAULT_GRACE_SECONDS)

  fun setGraceSeconds(context: Context, seconds: Int) {
    prefs(context).edit().putInt(KEY_GRACE_SECONDS, seconds).apply()
  }

  fun lockOnScreenOff(context: Context): Boolean =
    prefs(context).getBoolean(KEY_LOCK_ON_SCREEN_OFF, false)

  fun setLockOnScreenOff(context: Context, lock: Boolean) {
    prefs(context).edit().putBoolean(KEY_LOCK_ON_SCREEN_OFF, lock).apply()
  }

  fun hideInRecents(context: Context): Boolean =
    prefs(context).getBoolean(KEY_HIDE_IN_RECENTS, false)

  fun setHideInRecents(context: Context, hide: Boolean) {
    prefs(context).edit().putBoolean(KEY_HIDE_IN_RECENTS, hide).apply()
  }

  /** Back to what the app starts with. */
  fun reset(context: Context) {
    prefs(context).edit().clear().apply()
  }
}
