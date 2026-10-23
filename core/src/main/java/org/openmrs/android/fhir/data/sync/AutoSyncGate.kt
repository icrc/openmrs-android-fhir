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
package org.openmrs.android.fhir.data.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager

/**
 * When the automatic sync may start: on any connection (the default) or only on one that is not
 * metered (Wi-Fi), and not while the battery is low. A sync the user starts is never held back.
 */
object AutoSyncGate {
  private const val PREFS_NAME = "auto_sync_conditions"
  private const val KEY_UNMETERED_ONLY = "unmetered_only"
  private const val KEY_SKIP_LOW_BATTERY = "skip_low_battery"

  /** Below this level (percent) and not charging, the automatic sync waits. */
  const val LOW_BATTERY_PERCENT = 15

  enum class Blocked {
    METERED_NETWORK,
    LOW_BATTERY,
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun unmeteredOnly(context: Context): Boolean =
    prefs(context).getBoolean(KEY_UNMETERED_ONLY, false)

  fun setUnmeteredOnly(context: Context, only: Boolean) {
    prefs(context).edit().putBoolean(KEY_UNMETERED_ONLY, only).apply()
  }

  fun skipOnLowBattery(context: Context): Boolean =
    prefs(context).getBoolean(KEY_SKIP_LOW_BATTERY, true)

  fun setSkipOnLowBattery(context: Context, skip: Boolean) {
    prefs(context).edit().putBoolean(KEY_SKIP_LOW_BATTERY, skip).apply()
  }

  /** Why the automatic sync should not start now, or `null` when it may. */
  fun blockedReason(context: Context): Blocked? {
    if (unmeteredOnly(context) && isActiveNetworkMetered(context)) return Blocked.METERED_NETWORK
    if (skipOnLowBattery(context) && isBatteryLow(context)) return Blocked.LOW_BATTERY
    return null
  }

  private fun isActiveNetworkMetered(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    return manager?.isActiveNetworkMetered ?: false
  }

  private fun isBatteryLow(context: Context): Boolean {
    val status: Intent =
      context.applicationContext.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
      )
        ?: return false
    val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return false
    val charging =
      status.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
        it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
      }
    return !charging && level * 100 / scale < LOW_BATTERY_PERCENT
  }
}
