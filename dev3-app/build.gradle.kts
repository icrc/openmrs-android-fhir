import com.android.build.api.dsl.VariantDimension
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

val composeBomVersion: String by project
val composeCompilerExtensionVersion: String by project
val navigationVersion = "2.9.6"

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("com.google.devtools.ksp")
  id("maven-publish")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "org.openmrs.android.fhir.dev3_app"
  compileSdk = 36
  defaultConfig {
    applicationId = "org.openmrs.android.fhir.dev3_app"
    minSdk = 26
    targetSdk = 34
    manifestPlaceholders["appAuthRedirectScheme"] = applicationId!!
    buildFeatures.buildConfig = true
    versionCode = 1
    versionName = "0.1.1"

    //    server
    setResValue(providers, "fhir_base_url", this)
    setResValue(providers, "fhir_sync_urls", this)
    setResValue(providers, "openmrs_rest_url", this)
    setResValue(providers, "check_server_url", this)
    setResValue(providers, "cohort_type", this, propertyKey = "cohort-type")
    //    oauth
    setResValue(providers, "auth_authorization_scope", this)
    setResValue(providers, "auth_client_id", this)
    setResValue(providers, "auth_redirect_uri_host", this)
    setResValue(providers, "auth_redirect_uri_path", this)
    setResValue(providers, "auth_discovery_uri", this)
    setResValue(providers, "auth_end_session_endpoint", this)
    setResValue(providers, "auth_authorization_endpoint_uri", this)
    setResValue(providers, "auth_token_endpoint_uri", this)
    setResValue(providers, "auth_user_info_endpoint_uri", this)
    setResValue(providers, "auth_registration_endpoint_uri", this)

    setResValue(providers, "auth_https_required", this, "bool")
    setResValue(providers, "auth_replace_localhost_by_10_0_2_2", this, "bool")

    setResValue(providers, "auth_method", this)
    setResValue(providers, "basic_auth_expiry_hours", this, "integer")
    setResValue(providers, "registration_questionnaire_name", this)

    setResValue(providers, "show_review_page_before_submit", this, "bool")
    setResValue(providers, "filter_patient_lists_by_group", this, "bool")
    setResValue(providers, "fetch_identifiers", this, "bool")
    setResValue(providers, "diagnostics_password", this)
  }
  buildTypes {
    getByName("release") { signingConfig = signingConfigs.getByName("debug") }
    debug {
      isMinifyEnabled = false
      applicationIdSuffix = ".debug"
      isDebuggable = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures {
    compose = true
    viewBinding = true
  }
  composeOptions { kotlinCompilerExtensionVersion = composeCompilerExtensionVersion }
  compileOptions {
    // Flag to enable support for the new language APIs
    // See https://developer.android.com/studio/write/java8-support
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin { jvmToolchain(17) }
  packaging {
    resources.excludes.addAll(listOf("META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt"))
    jniLibs { useLegacyPackaging = true }
  }

  packaging { resources.excludes.addAll(listOf("META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt")) }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:$composeBomVersion")

  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  implementation(composeBom)
  implementation(project(":core"))
  implementation("androidx.activity:activity-compose:1.12.1")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.navigation:navigation-compose:$navigationVersion")

  androidTestImplementation(composeBom)
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")

  debugImplementation(composeBom)
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  debugImplementation("androidx.compose.ui:ui-tooling")
}

fun setResValue(
  providers: ProviderFactory,
  propertyName: String,
  variants: VariantDimension,
  type: String = "string",
  propertyKey: String = propertyName,
) {
  val prop = gradleLocalProperties(rootDir, providers).getProperty(propertyKey)
  if (prop != null) {
    variants.resValue(type, propertyName, prop)
  }
}
