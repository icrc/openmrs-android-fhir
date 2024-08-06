plugins {
  id("com.android.library")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("maven-publish")
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
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    // Flag to enable support for the new language APIs
    // See https://developer.android.com/studio/write/java8-support
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin { jvmToolchain(11) }
  packaging { resources.excludes.addAll(listOf("META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt")) }

  publishing {
    singleVariant("release") {
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

publishing {
  publications {
    create<MavenPublication>("release") {
      groupId = android.namespace
      artifactId = "coreapp"
      version = "0.1-SNAPSHOT"

      afterEvaluate {
        from(components["release"])
      }
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
  const val desugar_jdk_libs = "2.0.4"
  const val appauth = "0.11.1"
  const val timber = "5.0.1"
  const val jwtdecode = "2.0.2"
  const val fhirEngine = "1.0.0-SNAPSHOT"
  const val fhirDataCapture = "1.1.0-SNAPSHOT"
  const val lifecycle = "2.7.0"
  const val navigation = "2.7.7"
  const val datastore = "1.0.0"
  const val coroutines = "1.7.3"
  const val constraintlayout = "2.1.4"
  const val appcompat = "1.6.1"
  const val fragment = "1.6.2"
  const val activity = "1.8.2"
  const val recyclerview = "1.3.2"
  const val work = "2.9.0"
  const val material = "1.11.0"
  const val converterGson = "2.9.0"
  const val kotlinStdlibJdk7 = "1.9.22"
}

dependencies {


  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.desugar_jdk_libs}")
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
  implementation("com.squareup.retrofit2:converter-gson:${Versions.converterGson}")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.kotlinStdlibJdk7}")
  implementation("com.jakewharton.timber:timber:${Versions.timber}")
  implementation("net.openid:appauth:${Versions.appauth}")
  implementation("com.auth0.android:jwtdecode:${Versions.jwtdecode}")
  implementation("com.google.android.fhir:engine:${Versions.fhirEngine}")
  implementation("com.google.android.fhir:data-capture:${Versions.fhirDataCapture}")
}



