import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()

  }
  dependencies {
    classpath("com.android.tools.build:gradle:8.5.2")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
    classpath("com.google.gms:google-services:4.4.2")
    classpath("com.diffplug.spotless:spotless-plugin-gradle:6.22.0")
    classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")

    // NOTE: Do not place your application dependencies here; they belong
    // in the individual module build.gradle.kts files
  }
}


 plugins {
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
  }

allprojects {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven {
      name = "google-android-fhir"
      url = uri("https://maven.pkg.github.com/google/android-fhir")
      credentials {
        username = localPropertyOrEnv(providers,"gpr.user", "USERNAME")
        password = localPropertyOrEnv(providers,"gpr.key", "TOKEN")
      }
    }
  }

}

fun localPropertyOrEnv(providers: ProviderFactory, propertyName: String, envName: String): String? =
  gradleLocalProperties(rootDir, providers).getProperty(propertyName) ?: System.getenv(envName)