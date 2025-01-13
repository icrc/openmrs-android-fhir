import com.android.build.api.dsl.VariantDimension
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("com.google.devtools.ksp")
  id("maven-publish")
}

android {
  namespace = "org.openmrs.android.fhir.dev3_app"
  compileSdk = 34
  defaultConfig {
    applicationId = "org.openmrs.android.fhir.dev3_app"
    minSdk = 26
    targetSdk = 34
    manifestPlaceholders["appAuthRedirectScheme"] = applicationId!!
    buildFeatures.buildConfig = true
    versionCode = 1

    //    server
    setResValue(providers, "fhir_base_url", this)
    setResValue(providers, "fhir_sync_urls", this)
    setResValue(providers, "openmrs_rest_url", this)
    setResValue(providers, "check_server_url", this)
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
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86")
      isUniversalApk = false
    }
  }
  buildFeatures { viewBinding = true }
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
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.1")
  implementation(project(":core"))
}

fun setResValue(
  providers: ProviderFactory,
  propertyName: String,
  variants: VariantDimension,
  type: String = "string",
) {
  val prop = gradleLocalProperties(rootDir, providers).getProperty(propertyName)
  if (prop != null) {
    variants.resValue(type, propertyName, prop)
  }
}
