import com.android.build.api.dsl.VariantDimension
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("maven-publish")
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
//    server
    setResValue("fhir_base_url", "FHIR_BASE_URL",this)
    setResValue("openmrs_rest_url", "OPENMRS_REST_URL",this)
    setResValue("check_server_url", "CHECK_SERVER_URL",this)
    //    oauth
    setResValue("auth_authorization_scope", "AUTH_AUTHORIZATION_SCOPE",this)
    setResValue("auth_client_id", "AUTH_CLIENT_ID",this)
    setResValue("auth_redirect_uri_host", "AUTH_REDIRECT_URI_HOST",this)
    setResValue("auth_redirect_uri_path", "AUTH_REDIRECT_URI_PATH",this)
    setResValue("auth_discovery_uri", "AUTH_DISCOVERY_URI",this)
    setResValue("auth_end_session_endpoint", "AUTH_END_SESSION_ENDPOINT",this)
    setResValue("auth_authorization_endpoint_uri", "AUTH_AUTHORIZATION_ENDPOINT_URI",this)
    setResValue("auth_token_endpoint_uri", "AUTH_TOKEN_ENDPOINT_URI",this)
    setResValue("auth_user_info_endpoint_uri", "AUTH_USER_INFO_ENDPOINT_URI",this)
    setResValue("auth_registration_endpoint_uri", "AUTH_REGISTRATION_ENDPOINT_URI",this)
    setResValue("auth_registration_endpoint_uri", "AUTH_REGISTRATION_ENDPOINT_URI",this)

    setResValue("auth_https_required", "AUTH_HTTPS_REQUIRED",this,"bool")
    setResValue("auth_replace_localhost_by_10_0_2_2", "AUTH_REPLACE_LOCALHOST_BY_10_0_2_2",this,"bool")



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
  repositories {
    maven {
      url = uri("https://maven.pkg.github.com/google/android-fhir")
      credentials {
        username = localPropertyOrEnv("gpr.user", "USERNAME")
        password = localPropertyOrEnv("gpr.key", "TOKEN")
      }
    }
  }
}

publishing{
  repositories{
      maven{
        name = "CI"
        url= uri(System.getenv("REPOSITORY_URL"))
        credentials {
          username = System.getenv("GITHUB_ACTOR")
          password = System.getenv("GITHUB_TOKEN")
        }
      }
  }
}



dependencies {
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
}

fun localPropertyOrEnv(propertyName: String, envName: String): String? =
  gradleLocalProperties(rootDir).getProperty(propertyName) ?: System.getenv(envName)

fun setResValue(propertyName: String, envName: String,variants: VariantDimension, type: String="string") {
  val prop=localPropertyOrEnv(propertyName,envName)
  if(prop!=null){
    variants.resValue(type, propertyName, prop)
  }
}




