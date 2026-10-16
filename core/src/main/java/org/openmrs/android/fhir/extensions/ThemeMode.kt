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

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** How the app picks light or dark colors. */
enum class ThemeMode(val value: String, val nightMode: Int) {
  SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
  LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
  DARK("dark", AppCompatDelegate.MODE_NIGHT_YES),
}

/** Persists the chosen [ThemeMode] and applies it to the XML and Compose screens. */
object ThemeModeStore {
  private const val PREFS_NAME = "app_appearance"
  private const val KEY = "theme_mode"

  fun get(context: Context): ThemeMode {
    val saved =
      context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY, null)
    return ThemeMode.entries.firstOrNull { it.value == saved } ?: ThemeMode.SYSTEM
  }

  /** Saves the mode and applies it; open activities are recreated by AppCompat. */
  fun set(context: Context, mode: ThemeMode) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY, mode.value)
      .apply()
    AppCompatDelegate.setDefaultNightMode(mode.nightMode)
  }

  /** Called once at startup so every screen starts in the saved mode. */
  fun applySaved(context: Context) {
    AppCompatDelegate.setDefaultNightMode(get(context).nightMode)
  }
}

/** Whether Compose screens should use the dark palette, honouring the saved [ThemeMode]. */
@Composable
fun isAppInDarkTheme(): Boolean {
  val context = LocalContext.current
  return when (ThemeModeStore.get(context)) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }
}
