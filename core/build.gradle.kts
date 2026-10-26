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

object Versions {
  const val androidXCore = "1.17.0"
  const val composeNavigation = "2.9.6"
  const val desugar_jdk_libs = "2.1.5"
  const val appauth = "0.11.1"
  const val timber = "5.0.1"
  const val fhirEngine = "1.2.0"
  const val fhirDataCapture = "1.3.1"
  const val lifecycle = "2.10.0"
  const val navigation = "2.9.6"
  const val datastore = "1.2.1"
  const val coroutines = "1.11.0"
  const val constraintlayout = "2.2.2"
  const val appcompat = "1.8.0"
  const val fragment = "1.9.1"
  const val activity = "1.13.0"
  const val recyclerview = "1.4.0"
  const val work = "2.12.0"
  const val material = "1.14.0"
  const val retrofitVersion = "2.12.0"
  const val room = "2.8.5"
  const val junit = "4.13.2"
  const val coreTesting = "2.2.0"
  const val androidXTest = "1.6.1"
  const val androidTestTruth = "1.6.0"
  const val androidXTestRunner = "1.6.2"
  const val androidXTestJunit = "1.2.1"
  const val coroutineTest = "1.11.0"
  const val mockito = "5.24.0"
  const val mockitoKotlin = "6.4.0"
  const val daggerVersion = "2.60.1"
  const val eventBus = "3.3.1"
  const val moshi = "1.15.2"
  const val loggingInterceptor = "4.12.0"
  const val biometric = "1.1.0"
  const val zip4j = "2.11.6"
  const val zxingEmbedded = "4.3.0"
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:$composeBomVersion")

  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.desugar_jdk_libs}")
  implementation(composeBom)
  implementation("androidx.core:core:${Versions.androidXCore}")
  implementation("androidx.activity:activity-ktx:${Versions.activity}")
  implementation("androidx.activity:activity-compose:${Versions.activity}")
  implementation("androidx.appcompat:appcompat:${Versions.appcompat}")
  implementation("androidx.constraintlayout:constraintlayout:${Versions.constraintlayout}")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.runtime:runtime")
  implementation("androidx.compose.runtime:runtime-livedata")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.datastore:datastore-preferences:${Versions.datastore}")
  implementation("androidx.fragment:fragment-ktx:${Versions.fragment}")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycle}")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}")
  implementation("androidx.navigation:navigation-fragment-ktx:${Versions.navigation}")
  implementation("androidx.navigation:navigation-ui-ktx:${Versions.navigation}")
  implementation("androidx.navigation:navigation-compose:${Versions.composeNavigation}")
  implementation("androidx.recyclerview:recyclerview:${Versions.recyclerview}")
  implementation("androidx.work:work-runtime-ktx:${Versions.work}")
  implementation("com.google.android.material:material:${Versions.material}")
  implementation("com.squareup.retrofit2:retrofit:${Versions.retrofitVersion}")
  implementation("com.squareup.retrofit2:converter-moshi:${Versions.retrofitVersion}")
  implementation("com.squareup.okhttp3:logging-interceptor:${Versions.loggingInterceptor}")
  ksp("com.squareup.moshi:moshi-kotlin-codegen:${Versions.moshi}")
  implementation("com.squareup.moshi:moshi:${Versions.moshi}")
  implementation("org.greenrobot:eventbus:${Versions.eventBus}")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
  implementation("com.jakewharton.timber:timber:${Versions.timber}")
  implementation("net.openid:appauth:${Versions.appauth}")
  implementation("net.lingala.zip4j:zip4j:${Versions.zip4j}")
  // QR codes: drawing the settings to share, and the scanner that reads them.
  implementation("com.journeyapps:zxing-android-embedded:${Versions.zxingEmbedded}")
  implementation("com.google.android.fhir:engine:${Versions.fhirEngine}")
  implementation("com.google.android.fhir:data-capture:${Versions.fhirDataCapture}")
  implementation("androidx.biometric:biometric:${Versions.biometric}")
  implementation("androidx.room:room-runtime:${Versions.room}")
  implementation("androidx.room:room-ktx:${Versions.room}")
  ksp("androidx.room:room-compiler:${Versions.room}")
  //
  androidTestImplementation(composeBom)

  androidTestImplementation("junit:junit:${Versions.junit}")
  androidTestImplementation("androidx.test:core:${Versions.androidXTest}")
  androidTestImplementation("androidx.test:rules:${Versions.androidXTest}")
  androidTestImplementation("androidx.test:runner:${Versions.androidXTestRunner}")
  androidTestImplementation("androidx.test.ext:junit:${Versions.androidXTestJunit}")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")

  // AndroidX Test libraries
  testImplementation("junit:junit:${Versions.junit}")
  testImplementation("androidx.arch.core:core-testing:${Versions.coreTesting}")
  testImplementation("androidx.test:core:${Versions.androidXTest}")
  testImplementation("androidx.test:rules:${Versions.androidXTest}")
  testImplementation("androidx.test:runner:${Versions.androidXTestRunner}")
  testImplementation("androidx.test.ext:truth:${Versions.androidTestTruth}")
  testImplementation("androidx.test.ext:junit:${Versions.androidXTestJunit}")
  testImplementation(composeBom)
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation(composeBom)
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  debugImplementation("androidx.compose.ui:ui-tooling")

  // Mockito for mocking dependencies
  testImplementation("org.mockito:mockito-core:${Versions.mockito}")
  testImplementation("org.mockito.kotlin:mockito-kotlin:${Versions.mockitoKotlin}")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutineTest}")

  // Dependency injection
  implementation("com.google.dagger:dagger:${Versions.daggerVersion}")
  ksp("com.google.dagger:dagger-compiler:${Versions.daggerVersion}")
  testImplementation(kotlin("test"))
}
