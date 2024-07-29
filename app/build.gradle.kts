import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("com.google.devtools.ksp")
}

android {
  namespace = "org.openmrs.android.fhir"
  compileSdk = 34
  defaultConfig {
    applicationId = "org.openmrs.android.fhir"
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["appAuthRedirectScheme"] = applicationId!!
    buildFeatures.buildConfig = true
    versionCode = 1
  }
  buildTypes {
    debug {
      isMinifyEnabled = false
      applicationIdSuffix = ".debug"
      isDebuggable = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures { viewBinding = true }
  compileOptions {
    // Flag to enable support for the new language APIs
    // See https://developer.android.com/studio/write/java8-support
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin { jvmToolchain(11) }
  packaging { resources.excludes.addAll(listOf("META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt")) }
  repositories{
    maven {
      url = uri("https://maven.pkg.github.com/google/android-fhir")
      credentials {
        username = gradleLocalProperties(rootDir).getProperty("gpr.user") ?: System.getenv("USERNAME")
        password = gradleLocalProperties(rootDir).getProperty("gpr.key") ?: System.getenv("TOKEN")
      }
    }
  }
}

dependencies {
  implementation("androidx.activity:activity:1.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
  implementation("androidx.activity:activity-ktx:1.8.2")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.datastore:datastore-preferences:1.0.0")
  implementation("androidx.fragment:fragment-ktx:1.6.2")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
  implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
  implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
  implementation("com.squareup.retrofit2:converter-gson:2.9.0")
  implementation("androidx.datastore:datastore-preferences:1.0.0")
  implementation("com.google.android.material:material:1.11.0")
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation("net.openid:appauth:0.11.1")
  implementation("com.auth0.android:jwtdecode:2.0.2")
  implementation("com.google.android.fhir:engine:1.0.0-SNAPSHOT")
  implementation("com.google.android.fhir:data-capture:1.1.0-SNAPSHOT")
  //Room database
  val room_version = "2.6.1"
  implementation("androidx.room:room-runtime:$room_version")
  implementation("androidx.room:room-ktx:$room_version")
  annotationProcessor("androidx.room:room-compiler:$room_version")
  ksp("androidx.room:room-compiler:$room_version")
}
