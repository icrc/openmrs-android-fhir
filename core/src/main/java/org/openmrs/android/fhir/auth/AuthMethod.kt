package org.openmrs.android.fhir.auth

enum class AuthMethod(val value :String ) {
  BASIC("basic"),
  OPENID("openid");

  companion object {
    fun fromValue(value: String) =
      entries.firstOrNull { it.value == value }
        ?: throw IllegalArgumentException("Unknown epoch: $value")
  }
}