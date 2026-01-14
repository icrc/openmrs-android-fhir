import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:8.10.1")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    classpath("com.google.gms:google-services:4.4.2")
    classpath("com.diffplug.spotless:spotless-plugin-gradle:6.21.0")
    classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.0")

    // NOTE: Do not place your application dependencies here; they belong
    // in the individual module build.gradle.kts files
  }
}

plugins {
  id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
  id("org.jetbrains.kotlin.android") version "2.2.21" apply false
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
        username = localPropertyOrEnv(providers, "gpr.user", "USERNAME")
        password = localPropertyOrEnv(providers, "gpr.key", "TOKEN")
      }
    }
  }

  configureSpotless()
}

fun Project.configureSpotless() {
  val ktlintVersion = "0.50.0"
  val ktlintOptions = mapOf("indent_size" to "2", "continuation_indent_size" to "2")
  apply(plugin = "com.diffplug.spotless")
  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      targetExclude("**/build/")
      ktlint(ktlintVersion).userData(ktlintOptions)
      ktfmt().googleStyle()
      licenseHeaderFile(
        "${project.rootProject.projectDir}/license-header.txt",
        "^\\s*(package|import|class|object|sealed|open|interface|abstract).*",
      )
    }
    kotlinGradle {
      target("*.gradle.kts")
      ktlint(ktlintVersion).userData(ktlintOptions)
      ktfmt().googleStyle()
    }
    format("xml") {
      target("**/*.xml")
      targetExclude("**/build/", ".idea/")
      prettier(mapOf("prettier" to "2.0.5", "@prettier/plugin-xml" to "0.13.0"))
        .config(mapOf("parser" to "xml", "tabWidth" to 4))
    }
  }
}

fun localPropertyOrEnv(providers: ProviderFactory, propertyName: String, envName: String): String? =
  gradleLocalProperties(rootDir, providers).getProperty(propertyName) ?: System.getenv(envName)
