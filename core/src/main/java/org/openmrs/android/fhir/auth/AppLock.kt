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

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.extensions.BiometricUtils

/**
 * What the offline login is for: when the user leaves the app and comes back, they prove who they
 * are again (fingerprint, face or the screen lock, as chosen when it was enabled) before the app
 * shows its data. The check at the start of the app (the splash screen) covers a process that was
 * killed; this covers the one that was only in the background.
 *
 * Short absences do not lock: a photo, the file picker, a phone call, the permission dialogs all
 * take the app out of the foreground for a moment. Only an absence of the chosen delay (30 seconds
 * unless changed in the settings, see [AppLockSettings]) or more does, or the screen turning off
 * when the user asked for that.
 */
object AppLock {
  @Volatile private var leftAt: Long? = null

  @Volatile private var screenWentOff = false

  @Volatile private var lockPending = false

  @Volatile private var locking = false
  private var started = 0

  /**
   * Whether coming back after [awayMillis] asks again: the delay chosen in the settings has passed
   * (0 seconds: always), or the screen went off and the user asked for that to lock.
   */
  internal fun isLockDue(awayMillis: Long, graceSeconds: Int, screenWentOff: Boolean): Boolean =
    screenWentOff || awayMillis >= graceSeconds * 1000L

  /** Follows the activities to know when the app was left and when it is back. */
  fun install(application: Application) {
    application.registerReceiver(
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (AppLockSettings.lockOnScreenOff(context)) screenWentOff = true
        }
      },
      IntentFilter(Intent.ACTION_SCREEN_OFF),
    )
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
          if (started == 0) {
            // The app is back in front. Only the screen with the data asks (not the login page, or
            // the browser of a single sign-on in between), and only after a long enough absence
            // (or the screen having been turned off, when the user chose that).
            val left = leftAt
            val screenOff = screenWentOff
            leftAt = null
            screenWentOff = false
            if (
              left != null &&
                activity is MainActivity &&
                isLockDue(
                  awayMillis = SystemClock.elapsedRealtime() - left,
                  graceSeconds = AppLockSettings.graceSeconds(activity),
                  screenWentOff = screenOff,
                ) &&
                isEnabled(activity)
            ) {
              lockPending = true
            }
          }
          started++
        }

        override fun onActivityStopped(activity: Activity) {
          started = (started - 1).coerceAtLeast(0)
          // The whole app is out of sight (not a screen turning over to another of its own, and not
          // a rotation, which is over in a blink).
          if (started == 0 && !activity.isChangingConfigurations && !locking) {
            leftAt = SystemClock.elapsedRealtime()
          }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
          applyRecentsPrivacy(activity)
        }

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      },
    )
  }

  /**
   * Hides the screen in the Recents list (and from screenshots) when the user asked for that and
   * the app is locked by an offline login. Also called when the choice changes.
   */
  fun applyRecentsPrivacy(activity: Activity) {
    val hide = isEnabled(activity) && AppLockSettings.hideInRecents(activity)
    if (hide) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  /**
   * The user chose an offline login (and works with an account: a guest has nothing to protect).
   */
  fun isEnabled(context: Context): Boolean =
    !GuestSession.isActive(context) && BiometricUtils.getOfflineAuthMethod(context) != null

  /** The app came back after long enough and the offline login is on: ask before showing data. */
  fun shouldLock(): Boolean = lockPending && !locking

  /** The lock screen is up: the app being left again meanwhile does not restart the clock. */
  fun markLocking() {
    lockPending = false
    locking = true
  }

  fun unlocked() {
    locking = false
    lockPending = false
    leftAt = null
  }
}
