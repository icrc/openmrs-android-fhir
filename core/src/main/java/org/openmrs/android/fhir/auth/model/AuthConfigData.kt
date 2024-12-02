/*
* BSD 3-Clause License
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
*    contributors may be used to endorse or promote products derived from
*    this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.openmrs.android.fhir.auth.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthConfigData(
  @Json(name = "client_id") val clientId: String,
  @Json(name = "authorization_scope") val authorizationScope: String?,
  @Json(name = "redirect_uri") val redirectUri: String?,
  // @Json(name = "end_session_redirect_uri") val endSessionRedirectUri: String?, // Uncomment if
  // needed
  @Json(name = "discovery_uri") val discoveryUri: String?,
  @Json(name = "authorization_endpoint_uri") val authorizationEndpointUri: String?,
  @Json(name = "token_endpoint_uri") val tokenEndpointUri: String?,
  @Json(name = "user_info_endpoint_uri") val userInfoEndpointUri: String?,
  @Json(name = "end_session_endpoint") val endSessionEndpoint: String?,
  @Json(name = "registration_endpoint_uri") val registrationEndpointUri: String?,
  @Json(name = "https_required") val httpsRequired: Boolean,
  @Json(name = "replace_localhost_by_10_0_2_2") val replaceLocalhostBy1022: Boolean?,
)
