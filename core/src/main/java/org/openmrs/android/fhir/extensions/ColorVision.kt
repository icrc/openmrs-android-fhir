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

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.openmrs.android.fhir.R

/** Which color palette the app uses. */
enum class ColorVisionMode(val value: String) {
  STANDARD("standard"),

  /**
   * For color-blind users (red-green and blue-yellow): blue and orange carry the meaning that green
   * and red carry in the standard palette, and the tints differ in lightness too, so a state is
   * never told apart by hue alone.
   */
  COLOR_BLIND("color_blind"),
}

/** Persists the chosen [ColorVisionMode] and applies it to the XML and Compose screens. */
object ColorVisionStore {
  private const val PREFS_NAME = "app_appearance"
  private const val KEY = "color_vision"

  @Volatile private var cached: ColorVisionMode? = null

  fun get(context: Context): ColorVisionMode {
    cached?.let {
      return it
    }
    val saved =
      context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY, null)
    return (ColorVisionMode.entries.firstOrNull { it.value == saved } ?: ColorVisionMode.STANDARD)
      .also { cached = it }
  }

  /** Saves the mode; screens that are open pick it up when they are recreated. */
  fun set(context: Context, mode: ColorVisionMode) {
    cached = mode
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY, mode.value)
      .apply()
  }

  /** Puts the palette on every activity's theme before it builds any view. */
  fun install(application: Application) {
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
          if (get(activity) == ColorVisionMode.COLOR_BLIND) {
            activity.theme.applyStyle(R.style.ThemeOverlay_OpenMrs_ColorBlind, true)
          }
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      },
    )
  }
}

/** Whether Compose screens use the color-blind palette. */
@Composable
fun isColorBlindPalette(): Boolean =
  ColorVisionStore.get(LocalContext.current) == ColorVisionMode.COLOR_BLIND
