val composeBomVersion: String by project
val navigationVersion = "2.9.6"

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("com.google.devtools.ksp")
  id("maven-publish")
  id("org.jetbrains.kotlin.plugin.compose")
}

// local.properties -> generated resources (shared by every application module)
apply(from = rootProject.file("gradle/app-config.gradle.kts"))

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
