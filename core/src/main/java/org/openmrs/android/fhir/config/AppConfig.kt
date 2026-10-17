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
package org.openmrs.android.fhir.config

import android.content.Context
import org.openmrs.android.fhir.R
import org.xmlpull.v1.XmlPullParser

/** Single sign-on provider (OpenID Connect). */
data class SsoConfig(
  /** Shown on the login button: "Sign in with <name>". Blank: "Sign in with SSO". */
  val providerName: String,
  val discoveryUrl: String,
  val clientId: String,
)

/** What the app downloads. */
data class SyncConfig(
  /** Tag of the locations offered at login (and downloaded). */
  val locationTag: String,
  /** Optional cohort type (UUID) that limits which patient lists are downloaded. */
  val cohortType: String,
  /** Only download the patient lists of the location the user picked. */
  val filterPatientListsByLocation: Boolean,
  /** Optional ISO date: do not download patients that were last updated before it. */
  val patientsSince: String,
)

/** A group of encounter forms on the Create Encounter screen. */
data class FormSectionConfig(
  private val defaultName: String,
  private val names: Map<String, String>,
  /** Encounter type codes of the forms, in display order. */
  val forms: List<String>,
) {
  /** The section name in [language] (`en`, `fr`, ...), else the plain name. */
  fun nameFor(language: String): String =
    names[language.lowercase()] ?: defaultName.ifBlank { names.values.firstOrNull().orEmpty() }
}

/** How much of the user's trail the audit log keeps. */
class AuditConfig(val maxActions: Int)

/**
 * The app's build-time configuration, read from `res/xml/app_config.xml`. Each application module
 * ships its own copy (it replaces the one in `core`), so one file describes one app.
 */
class AppConfig
internal constructor(
  /** Base URL of the OpenMRS server, e.g. `http://10.0.2.2:8080/openmrs`. */
  val serverUrl: String,
  /** `basic`, `openid` or `both`: what the login page offers. */
  val authMethod: String,
  /** Offer "Continue without signing in". */
  val guestMode: Boolean,
  val sso: SsoConfig?,
  val sync: SyncConfig,
  /** Resource id (synced) or file name (assets) of the patient registration questionnaire. */
  val registrationForm: String,
  /** Fetch auto-generated identifiers on the device and embed them in new patients. */
  val fetchIdentifiers: Boolean,
  val supportEmail: String,
  /** Optional: protects the diagnostics archive. */
  val diagnosticsPassword: String,
  /** Empty: every available form is listed in one section. */
  val formSections: List<FormSectionConfig>,
  val audit: AuditConfig = AuditConfig(DEFAULT_AUDIT_ACTIONS),
) {
  companion object {
    /** Audit entries kept when the file does not say. */
    const val DEFAULT_AUDIT_ACTIONS = 500

    @Volatile private var instance: AppConfig? = null

    fun get(context: Context): AppConfig =
      instance ?: synchronized(this) { instance ?: read(context).also { instance = it } }

    private fun read(context: Context): AppConfig {
      val parser = context.applicationContext.resources.getXml(R.xml.app_config)
      try {
        return parse(parser)
      } finally {
        parser.close()
      }
    }

    /** Reads the configuration; a missing or contradictory setting fails with a clear message. */
    fun parse(parser: XmlPullParser): AppConfig {
      var serverUrl: String? = null
      var authMethod = "basic"
      var guestMode = false
      var sso: SsoConfig? = null
      var sync = SyncConfig("Login Location", "", true, "")
      var registrationForm = "new-patient-registration-paginated"
      var fetchIdentifiers = true
      var supportEmail = ""
      var diagnosticsPassword = ""
      var auditActions = DEFAULT_AUDIT_ACTIONS
      val sections = mutableListOf<FormSectionConfig>()

      var sectionName = ""
      var sectionNames = mutableMapOf<String, String>()
      var sectionForms = mutableListOf<String>()
      var inSection = false

      fun attr(name: String): String? = parser.getAttributeValue(null, name)?.trim()

      var event = parser.eventType
      while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
          when (parser.name) {
            "server" -> serverUrl = attr("url")
            "auth" -> {
              authMethod = attr("method") ?: authMethod
              guestMode = attr("guest-mode") == "true"
            }
            "sso" ->
              sso =
                SsoConfig(
                  providerName = attr("provider-name").orEmpty(),
                  discoveryUrl = attr("discovery-url").orEmpty(),
                  clientId = attr("client-id") ?: "openmrs-fhir",
                )
            "sync" ->
              sync =
                SyncConfig(
                  locationTag = attr("location-tag") ?: sync.locationTag,
                  cohortType = attr("cohort-type").orEmpty(),
                  filterPatientListsByLocation =
                    attr("filter-patient-lists-by-location")?.let { it == "true" } ?: true,
                  patientsSince = attr("patients-since").orEmpty(),
                )
            "patients" -> {
              registrationForm = attr("registration-form") ?: registrationForm
              fetchIdentifiers = attr("fetch-identifiers")?.let { it == "true" } ?: true
            }
            "support" -> {
              supportEmail = attr("email").orEmpty()
              diagnosticsPassword = attr("diagnostics-password").orEmpty()
            }
            "audit" ->
              auditActions = (attr("max-actions")?.toIntOrNull() ?: auditActions).coerceIn(50, 5000)
            "section" -> {
              inSection = true
              sectionName = attr("name").orEmpty()
              sectionNames = mutableMapOf()
              sectionForms = mutableListOf()
            }
            "name" ->
              if (inSection) {
                val language = attr("lang")?.lowercase().orEmpty()
                sectionNames[language] = parser.nextText().trim()
              }
            "form" ->
              if (inSection) attr("code")?.takeIf { it.isNotEmpty() }?.let(sectionForms::add)
          }
        } else if (event == XmlPullParser.END_TAG && parser.name == "section") {
          inSection = false
          if (sectionForms.isNotEmpty()) {
            sections += FormSectionConfig(sectionName, sectionNames, sectionForms)
          }
        }
        event = parser.next()
      }

      val server =
        requireNotNull(serverUrl?.takeIf { it.isNotEmpty() }) {
          "app_config.xml: <server url=\"...\"/> is required"
        }
      val methods =
        authMethod.split(',', ' ').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
      val needsSso = "openid" in methods || "both" in methods
      require(!needsSso || !sso?.discoveryUrl.isNullOrEmpty()) {
        "app_config.xml: auth method '$authMethod' needs <sso discovery-url=\"...\"/>"
      }
      return AppConfig(
        serverUrl = server.trimEnd('/'),
        authMethod = authMethod,
        guestMode = guestMode,
        sso = sso,
        sync = sync,
        registrationForm = registrationForm,
        fetchIdentifiers = fetchIdentifiers,
        supportEmail = supportEmail,
        diagnosticsPassword = diagnosticsPassword,
        formSections = sections,
        audit = AuditConfig(auditActions),
      )
    }
  }
}
