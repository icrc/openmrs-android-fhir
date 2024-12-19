package org.openmrs.android.fhir.auth.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BasicAuthState(
  val username: String = "",
  val password: String = "",
  val expiryEpoch: Long = System.currentTimeMillis(),
  val authenticated: Boolean = false,
)
