# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# The page breadcrumbs of a form (core/forms/PageBreadcrumbs.kt) read and set the form library's
# internal state by reflection: keep these names.
-keep class com.google.android.fhir.datacapture.QuestionnaireViewModel { *; }
-keep class com.google.android.fhir.datacapture.QuestionnaireState { *; }
-keep class com.google.android.fhir.datacapture.QuestionnairePagination { *; }
-keep class com.google.android.fhir.datacapture.QuestionnairePage { *; }
-keep class com.google.android.fhir.datacapture.DisplayMode** { *; }
