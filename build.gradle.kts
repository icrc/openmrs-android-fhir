import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()

  }

  dependencies {
    classpath("com.android.tools.build:gradle:8.2.2")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
//    classpath("com.google.gms:google-services:4.4.1")
//    classpath("com.diffplug.spotless:spotless-plugin-gradle:6.22.0")
    classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")

    // NOTE: Do not place your application dependencies here; they belong
    // in the individual module build.gradle.kts files
  }

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
        username = gradleLocalProperties(rootDir).getProperty("gpr.user")
        password = gradleLocalProperties(rootDir).getProperty("gpr.key")
      }
    }
  }

}