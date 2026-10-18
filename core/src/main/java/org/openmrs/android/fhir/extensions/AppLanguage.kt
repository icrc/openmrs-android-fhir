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
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The languages the app is translated into. The choice is the app's own (it does not follow the
 * system language once made) and is kept by AppCompat, which also recreates the open screens so the
 * new language shows at once.
 */
enum class AppLanguage(val tag: String, val flag: String, val nativeName: String) {
  ENGLISH("en", "🇬🇧", "English"),
  FRENCH("fr", "🇫🇷", "Français"),
  SPANISH("es", "🇪🇸", "Español"),
  RUSSIAN("ru", "🇷🇺", "Русский"),
  ARABIC("ar", "🇸🇦", "العربية"),
  ;

  companion object {
    /** The language in use: the one chosen in the app, else the one the device runs in. */
    fun current(context: Context): AppLanguage {
      val chosen = AppCompatDelegate.getApplicationLocales()
      val language =
        if (chosen.isEmpty) {
          context.resources.configuration.locales[0].language
        } else {
          chosen[0]?.language
        }
      return entries.firstOrNull { it.tag == language } ?: ENGLISH
    }

    /** The language code in use (`en`, `fr`, ...), whether or not the app is translated into it. */
    fun currentTag(context: Context): String {
      val chosen = AppCompatDelegate.getApplicationLocales()
      return (if (chosen.isEmpty) null else chosen[0]?.language)
        ?: context.resources.configuration.locales[0].language
    }

    /** Back to the language of the device, if the user had chosen another. */
    fun resetToDevice() {
      if (!AppCompatDelegate.getApplicationLocales().isEmpty) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
      }
    }

    fun apply(context: Context, language: AppLanguage) {
      val locale = Locale.forLanguageTag(language.tag)
      Locale.setDefault(locale)
      updateApplicationResources(context, locale)
      // Recreates the running activities; their resources follow the new locale.
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    /**
     * Texts read from the application context (view models, notifications) have to follow too;
     * before Android 13 only the activities do on their own.
     */
    @Suppress("DEPRECATION")
    private fun updateApplicationResources(context: Context, locale: Locale) {
      val resources = context.applicationContext.resources
      val configuration = android.content.res.Configuration(resources.configuration)
      configuration.setLocale(locale)
      resources.updateConfiguration(configuration, resources.displayMetrics)
    }
  }
}
