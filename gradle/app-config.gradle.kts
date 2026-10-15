import groovy.lang.GroovyObject
import java.util.Properties

/*
 * Single place that turns `local.properties` into the generated Android resources the app reads.
 *
 * Applied by every application module (app, dev3-app) so the list of supported settings lives in
 * one file instead of being copied between modules.
 *
 * Minimal setup: set `openmrs_server_url` (e.g. http://10.0.2.2:8080/openmrs). The FHIR, REST and
 * connectivity-check URLs are derived from it; any of them can still be set explicitly.
 */
val localProperties =
  Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
  }

// Settings are generated as string / bool / integer resources with the same name as the property.
val stringSettings =
  listOf(
    // server
    "fhir_base_url",
    "fhir_sync_urls",
    "first_fhir_sync_url",
    "openmrs_rest_url",
    "check_server_url",
    "cohort_type",
    "encounter_type_system_url",
    "questionnaires",
    "registration_questionnaire_name",
    "support_email",
    "diagnostics_password",
    // authentication
    "auth_method",
    "auth_authorization_scope",
    "auth_client_id",
    "auth_redirect_uri_host",
    "auth_redirect_uri_path",
    "auth_discovery_uri",
    "auth_end_session_endpoint",
    "auth_authorization_endpoint_uri",
    "auth_token_endpoint_uri",
    "auth_user_info_endpoint_uri",
    "auth_registration_endpoint_uri",
  )
val booleanSettings =
  listOf(
    "auth_https_required",
    "auth_replace_localhost_by_10_0_2_2",
    "show_review_page_before_submit",
    "filter_patient_lists_by_group",
    "fetch_identifiers",
  )
val integerSettings = listOf("basic_auth_expiry_hours", "server_connectivity_timeout_seconds")

// Resource name -> property key, where they differ (documented as `cohort-type`).
val propertyKeyFor = mapOf("cohort_type" to "cohort-type")

// One server URL instead of three.
val serverUrl = localProperties.getProperty("openmrs_server_url")?.trim()?.trimEnd('/')
val derivedSettings: Map<String, String> =
  if (serverUrl.isNullOrEmpty()) {
    emptyMap()
  } else {
    mapOf(
      "fhir_base_url" to "$serverUrl/ws/fhir2/R4/",
      "openmrs_rest_url" to "$serverUrl/ws/rest/v1/",
      "check_server_url" to "$serverUrl/ws/rest/v1/session",
    )
  }

fun settingValue(resourceName: String): String? =
  localProperties.getProperty(propertyKeyFor[resourceName] ?: resourceName)
    ?: derivedSettings[resourceName]

// Typos in local.properties used to be ignored silently; say so once per build.
if (!rootProject.extra.has("appConfigChecked")) {
  rootProject.extra["appConfigChecked"] = true
  val known =
    (stringSettings + booleanSettings + integerSettings).map { propertyKeyFor[it] ?: it }.toSet() +
      setOf("openmrs_server_url", "sdk.dir", "ndk.dir", "gpr.user", "gpr.key")
  localProperties
    .stringPropertyNames()
    .filter { it !in known }
    .sorted()
    .forEach {
      logger.warn("local.properties: unknown setting '$it' is not used by the app (typo?)")
    }
}

// The Android Gradle Plugin's types are not visible to script plugins, so resValue is called
// dynamically on the `android.defaultConfig` object.
pluginManager.withPlugin("com.android.application") {
  val android = extensions.getByName("android") as GroovyObject
  val defaultConfig = android.getProperty("defaultConfig") as GroovyObject
  fun generate(type: String, names: List<String>) =
    names.forEach { name ->
      settingValue(name)?.let { defaultConfig.invokeMethod("resValue", arrayOf(type, name, it)) }
    }
  generate("string", stringSettings)
  generate("bool", booleanSettings)
  generate("integer", integerSettings)
}
