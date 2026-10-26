val composeBomVersion: String by project

plugins {
  id("com.android.library")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("com.google.devtools.ksp")
  id("maven-publish")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "org.openmrs.android.fhir"
  compileSdk = 36
  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildFeatures.buildConfig = true
    manifestPlaceholders["appAuthRedirectScheme"] = "org.openmrs.android.fhir.app"
  }
  buildFeatures {
    compose = true
    viewBinding = true
  }
  buildTypes {
    release {
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    // Flag to enable support for the new language APIs
    // See https://developer.android.com/studio/write/java8-support
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin { jvmToolchain(17) }
  packaging { resources.excludes.addAll(listOf("META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt")) }
  lint {
    // Errors stop the build; warnings are reported. "Typos" reads the base64 certificates of
    // font_certs.xml as misspelled words.
    abortOnError = true
    warningsAsErrors = false
    disable += "Typos"
  }

  publishing {
    multipleVariants {
      allVariants()
      withSourcesJar()
    }
  }
  repositories {
    maven {
      name = "CI"
      url = uri("https://maven.pkg.github.com/icrc/openmrs-android-fhir")
      if (System.getenv("GITHUB_TOKEN") != null) {
        credentials {
          username = System.getenv("GITHUB_ACTOR")
          password = System.getenv("GITHUB_TOKEN")
        }
      }
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.generateKotlin", "false")
}

publishing {
  publications {
    register<MavenPublication>("default") {
      groupId = android.namespace
      artifactId = "coreapp"
      version = "0.1.1-SNAPSHOT"

      afterEvaluate { from(components["default"]) }
    }
  }
  repositories {
    maven {
      name = "CI"
      url = uri("https://maven.pkg.github.com/icrc/openmrs-android-fhir")
      if (System.getenv("GITHUB_TOKEN") != null) {
        credentials {
          username = System.getenv("GITHUB_ACTOR")
          password = System.getenv("GITHUB_TOKEN")
        }
      }
    }
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:$composeBomVersion")

  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(composeBom)
  implementation(libs.androidx.core.base)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.base)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.runtime.base)
  implementation(libs.androidx.compose.runtime.livedata)
  implementation(libs.androidx.compose.ui.base)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.material)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.logging.interceptor)
  ksp(libs.moshi.kotlin.codegen)
  implementation(libs.moshi.base)
  implementation(libs.eventbus)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)
  implementation(libs.appauth)
  implementation(libs.zip4j)
  // QR codes: drawing the settings to share, and the scanner that reads them.
  implementation(libs.zxing.android.embedded)
  implementation(libs.fhir.engine)
  implementation(libs.fhir.data.capture)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  //
  androidTestImplementation(composeBom)

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)

  // AndroidX Test libraries
  testImplementation(libs.junit)
  testImplementation(libs.androidx.core.testing)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.rules)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.androidx.test.ext.truth)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(composeBom)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling.base)

  // Mockito for mocking dependencies
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.kotlinx.coroutines.test)

  // Dependency injection
  implementation(libs.dagger.base)
  ksp(libs.dagger.compiler)
  testImplementation(kotlin("test"))
}
