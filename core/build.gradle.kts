plugins {
  id("com.android.library")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("com.google.devtools.ksp")
  id("maven-publish")
  id("kotlin-kapt")
}

android {
  namespace = "org.openmrs.android.fhir"
  compileSdk = 34
  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.Android JUnitRunner"
    buildFeatures.buildConfig = true
  }
  buildFeatures { viewBinding = true }
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

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

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
  const val androidXCore = "1.12.0"
  const val desugar_jdk_libs = "2.1.0"
  const val appauth = "0.11.1"
  const val timber = "5.0.1"
  const val jwtdecode = "2.0.2"
  const val fhirEngine = "1.2.0-SNAPSHOT"
  const val fhirDataCapture = "1.3.0-SNAPSHOT"
  const val lifecycle = "2.8.4"
  const val navigation = "2.7.7"
  const val datastore = "1.1.1"
  const val coroutines = "1.8.1"
  const val constraintlayout = "2.1.4"
  const val appcompat = "1.7.0"
  const val fragment = "1.8.2"
  const val activity = "1.9.1"
  const val recyclerview = "1.3.2"
  const val work = "2.9.1"
  const val material = "1.12.0"
  const val retrofitVersion = "2.9.0"
  const val kotlinStdlibJdk7 = "1.9.22"
  const val room = "2.6.1"
  const val junit = "4.13.2"
  const val coreTesting = "2.2.0"
  const val androidXTest = "1.6.1"
  const val androidTestTruth = "1.6.0"
  const val androidXTestRunner = "1.6.2"
  const val androidXTestJunit = "1.2.1"
  const val coroutineTest = "1.8.1"
  const val mockito = "4.0.0"
  const val daggerVersion = "2.51.1"
  const val eventBus = "3.3.1"
  const val moshi = "1.14.0"
  const val loggingInterceptor = "5.0.0-alpha.2"
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.desugar_jdk_libs}")
  implementation("androidx.core:core:${Versions.androidXCore}")
  implementation("androidx.activity:activity-ktx:${Versions.activity}")
  implementation("androidx.appcompat:appcompat:${Versions.appcompat}")
  implementation("androidx.constraintlayout:constraintlayout:${Versions.constraintlayout}")
  implementation("androidx.datastore:datastore-preferences:${Versions.datastore}")
  implementation("androidx.fragment:fragment-ktx:${Versions.fragment}")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycle}")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}")
  implementation("androidx.navigation:navigation-fragment-ktx:${Versions.navigation}")
  implementation("androidx.navigation:navigation-ui-ktx:${Versions.navigation}")
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
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.kotlinStdlibJdk7}")
  implementation("com.jakewharton.timber:timber:${Versions.timber}")
  implementation("net.openid:appauth:${Versions.appauth}")
  implementation("com.auth0.android:jwtdecode:${Versions.jwtdecode}")
  implementation("com.google.android.fhir:engine:${Versions.fhirEngine}")
  implementation("com.google.android.fhir:data-capture:${Versions.fhirDataCapture}")

  implementation("androidx.room:room-runtime:${Versions.room}")
  implementation("androidx.room:room-ktx:${Versions.room}")
  ksp("androidx.room:room-compiler:${Versions.room}")

  androidTestImplementation("junit:junit:${Versions.junit}")

  // AndroidX Test libraries
  testImplementation("junit:junit:${Versions.junit}")
  testImplementation("androidx.arch.core:core-testing:${Versions.coreTesting}")
  testImplementation("androidx.test:core:${Versions.androidXTest}")
  testImplementation("androidx.test:rules:${Versions.androidXTest}")
  testImplementation("androidx.test:runner:${Versions.androidXTestRunner}")
  testImplementation("androidx.test.ext:truth:${Versions.androidTestTruth}")
  testImplementation("androidx.test.ext:junit:${Versions.androidXTestJunit}")

  // Mockito for mocking dependencies
  testImplementation("org.mockito:mockito-core:${Versions.mockito}")
  testImplementation("org.mockito:mockito-inline:${Versions.mockito}")
  testImplementation("org.mockito:mockito-android:${Versions.mockito}")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutineTest}")

  // Dependency injection
  implementation("com.google.dagger:dagger:${Versions.daggerVersion}")
  kapt("com.google.dagger:dagger-compiler:${Versions.daggerVersion}")
}
