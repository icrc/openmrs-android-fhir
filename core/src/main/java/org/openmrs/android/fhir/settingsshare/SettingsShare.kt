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
package org.openmrs.android.fhir.settingsshare

import android.content.Context
import androidx.annotation.StringRes
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.SettingsRepository
import org.openmrs.android.fhir.data.remote.ServerSettings
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.ClearDataOnLogoutStore
import org.openmrs.android.fhir.extensions.ColorVisionMode
import org.openmrs.android.fhir.extensions.ColorVisionStore
import org.openmrs.android.fhir.extensions.EmbeddedFormsStore
import org.openmrs.android.fhir.extensions.ThemeMode
import org.openmrs.android.fhir.extensions.ThemeModeStore
import org.openmrs.android.fhir.ui.screens.SettingsDefaults

/** The settings of the app that can be passed to another device, each with its name in the code. */
enum class SharedSetting(val key: String, @StringRes val label: Int) {
  NOTIFICATIONS("notifications", R.string.enable_notifications),
  NETWORK_STATUS("network_status", R.string.check_network_connectivity),
  TOKEN_DELAY("token_delay", R.string.token_check_delay_in_minutes),
  PERIODIC_DELAY("periodic_delay", R.string.periodic_sync_delay_in_minutes),
  AUTO_SYNC("auto_sync", R.string.settings_auto_sync),
  LIVE_MODE("live_mode", R.string.settings_live_mode),
  KEEP_LIVE_PATIENTS("keep_live_patients", R.string.settings_live_keep_patients),
  AUTOMATIC_VISITS("automatic_visits", R.string.settings_automatic_visits),
  THEME("theme", R.string.settings_section_appearance),
  COLOR_VISION("color_vision", R.string.color_vision_title),
  EMBEDDED_FORMS("embedded_forms_only", R.string.embedded_forms_only),
  CLEAR_ON_LOGOUT("clear_data_on_logout", R.string.clear_on_logout_title),
  LANGUAGE("language", R.string.settings_language),
  SERVER("server", R.string.server_settings_title),
  ;

  /** How the value is written in the code and in the review. */
  val isBoolean: Boolean
    get() =
      this in
        setOf(
          NOTIFICATIONS,
          NETWORK_STATUS,
          AUTO_SYNC,
          LIVE_MODE,
          KEEP_LIVE_PATIENTS,
          AUTOMATIC_VISITS,
          EMBEDDED_FORMS,
          CLEAR_ON_LOGOUT,
        )
}

/** A setting whose value in a received code differs from this device's. */
data class SettingChange(val setting: SharedSetting, val current: String, val proposed: String)

/**
 * Passing the app's settings to another device as a QR code.
 *
 * What is shared is only the settings of the app (the ones of the Settings page, the theme, the
 * language and the server): never patients, passwords, tokens or anything of the user's account,
 * and not the choices that belong to a user (sharing, remember my location). A code is a short JSON
 * text; a received one is checked value by value (unknown or out-of-range values are dropped),
 * compared with this device's settings, and nothing changes until the user approves each
 * difference.
 */
@Singleton
class SettingsShare
@Inject
constructor(
  private val context: Context,
  private val repository: SettingsRepository,
) {
  /** This device's values, written as they go in a code. */
  suspend fun currentValues(): Map<SharedSetting, String> =
    linkedMapOf(
      SharedSetting.NOTIFICATIONS to repository.getNotificationsEnabled().toString(),
      SharedSetting.NETWORK_STATUS to repository.getNetworkStatusVisible().toString(),
      SharedSetting.TOKEN_DELAY to repository.getTokenExpiryDelayMinutes().toString(),
      SharedSetting.PERIODIC_DELAY to repository.getPeriodicSyncDelayMinutes().toString(),
      SharedSetting.AUTO_SYNC to repository.getAutoSyncEnabled().toString(),
      SharedSetting.LIVE_MODE to repository.getLiveModeEnabled().toString(),
      SharedSetting.KEEP_LIVE_PATIENTS to repository.getKeepLivePatients().toString(),
      SharedSetting.AUTOMATIC_VISITS to repository.getAutomaticVisits().toString(),
      SharedSetting.THEME to ThemeModeStore.get(context).value,
      SharedSetting.COLOR_VISION to ColorVisionStore.get(context).value,
      SharedSetting.EMBEDDED_FORMS to EmbeddedFormsStore.get(context).toString(),
      SharedSetting.CLEAR_ON_LOGOUT to ClearDataOnLogoutStore.get(context).toString(),
      SharedSetting.LANGUAGE to AppLanguage.current(context).tag,
      SharedSetting.SERVER to ServerSettings.activeUrl(context),
    )

  /** The code that holds this device's settings. */
  suspend fun export(): String {
    val settings = JSONObject()
    currentValues().forEach { (setting, value) -> settings.put(setting.key, value) }
    return PREFIX + JSONObject().put("v", VERSION).put("settings", settings).toString()
  }

  /**
   * The settings a code holds, or `null` when it is not a settings code of this app (or of a
   * version this app does not know). Values that are not valid for their setting are left out.
   */
  fun parse(code: String): Map<SharedSetting, String>? {
    val text = code.trim()
    if (!text.startsWith(PREFIX)) return null
    val root = runCatching { JSONObject(text.removePrefix(PREFIX)) }.getOrNull() ?: return null
    if (root.optInt("v", -1) != VERSION) return null
    val settings = root.optJSONObject("settings") ?: return null
    val result = linkedMapOf<SharedSetting, String>()
    SharedSetting.entries.forEach { setting ->
      val raw = settings.optString(setting.key, "")
      validated(setting, raw)?.let { result[setting] = it }
    }
    return result
  }

  private fun validated(setting: SharedSetting, raw: String): String? {
    if (raw.isBlank()) return null
    return when {
      setting.isBoolean -> raw.takeIf { it == "true" || it == "false" }
      setting == SharedSetting.TOKEN_DELAY ->
        raw.toIntOrNull()?.takeIf { it in SettingsDefaults.TokenDelayOptions }?.toString()
      setting == SharedSetting.PERIODIC_DELAY ->
        raw.toIntOrNull()?.takeIf { it in SettingsDefaults.PeriodicSyncDelayOptions }?.toString()
      setting == SharedSetting.THEME -> raw.takeIf { v -> ThemeMode.entries.any { it.value == v } }
      setting == SharedSetting.COLOR_VISION ->
        raw.takeIf { v -> ColorVisionMode.entries.any { it.value == v } }
      setting == SharedSetting.LANGUAGE ->
        raw.takeIf { v -> AppLanguage.entries.any { it.tag == v } }
      setting == SharedSetting.SERVER -> ServerSettings.normalize(raw)
      else -> null
    }
  }

  /** What would change: the settings of [proposed] that differ from this device's. */
  suspend fun changesFrom(proposed: Map<SharedSetting, String>): List<SettingChange> {
    val current = currentValues()
    return proposed.mapNotNull { (setting, value) ->
      val now = current[setting] ?: return@mapNotNull null
      if (now == value) null else SettingChange(setting, now, value)
    }
  }

  /**
   * Applies the approved [changes]. The server is not applied here: changing it signs the user out
   * and restarts the app, which only the screen that asked can do (see ShareSettingsDialog).
   */
  suspend fun apply(changes: List<SettingChange>) {
    val byKind = changes.filter { it.setting != SharedSetting.SERVER }
    // The ones that recreate the screens go last.
    val (recreating, plain) =
      byKind.partition {
        it.setting == SharedSetting.THEME ||
          it.setting == SharedSetting.LANGUAGE ||
          it.setting == SharedSetting.COLOR_VISION
      }
    (plain + recreating).forEach { change ->
      val value = change.proposed
      when (change.setting) {
        SharedSetting.NOTIFICATIONS -> repository.setNotificationsEnabled(value.toBoolean())
        SharedSetting.NETWORK_STATUS -> repository.setNetworkStatusVisible(value.toBoolean())
        SharedSetting.TOKEN_DELAY -> repository.setTokenExpiryDelayMinutes(value.toInt())
        SharedSetting.PERIODIC_DELAY -> repository.setPeriodicSyncDelayMinutes(value.toInt())
        SharedSetting.AUTO_SYNC -> repository.setAutoSyncEnabled(value.toBoolean())
        SharedSetting.LIVE_MODE -> repository.setLiveModeEnabled(value.toBoolean())
        SharedSetting.KEEP_LIVE_PATIENTS -> repository.setKeepLivePatients(value.toBoolean())
        SharedSetting.AUTOMATIC_VISITS -> repository.setAutomaticVisits(value.toBoolean())
        SharedSetting.EMBEDDED_FORMS -> EmbeddedFormsStore.set(context, value.toBoolean())
        SharedSetting.CLEAR_ON_LOGOUT -> ClearDataOnLogoutStore.set(context, value.toBoolean())
        SharedSetting.COLOR_VISION ->
          ColorVisionStore.set(context, ColorVisionMode.entries.first { it.value == value })
        SharedSetting.THEME ->
          ThemeModeStore.set(context, ThemeMode.entries.first { it.value == value })
        SharedSetting.LANGUAGE ->
          AppLanguage.apply(context, AppLanguage.entries.first { it.tag == value })
        SharedSetting.SERVER -> Unit
      }
    }
  }

  companion object {
    private const val PREFIX = "OPENMRS-SETTINGS:"
    private const val VERSION = 1
  }
}
