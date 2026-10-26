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
package org.openmrs.android.fhir.auth

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthorizationException
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import org.openmrs.android.fhir.BuildConfig
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.model.AuthConfigData
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.extensions.fromJson
import org.openmrs.android.fhir.extensions.toJson

class AuthConfiguration private constructor(private val context: Context) {
  private val authConfigKey by lazy { stringPreferencesKey("AuthConfig") }
  var lastException: AuthorizationException? = null
  val stored: AuthConfigData
    get() {
      val serializedAuth =
        runBlocking { context.dataStore.data.first()[authConfigKey] } ?: return authConfigData
      return serializedAuth.fromJson<AuthConfigData>() ?: authConfigData
    }

  suspend fun isNotStored(): Boolean {
    return context.dataStore.data.first()[authConfigKey] == null
  }

  suspend fun save() {
    context.dataStore.edit { pref -> pref[authConfigKey] = authConfigData.toJson() }
  }

  private val appConfig by lazy { AppConfig.get(context) }

  /**
   * The single sign-on settings, read from `res/values/auth_config.xml` (an application module's
   * own copy replaces the one in `core`).
   */
  val authConfigData: AuthConfigData by lazy {
    val discoveryUrl = context.getString(R.string.auth_discovery_uri).trim()
    val redirectHost = context.getString(R.string.auth_redirect_uri_host).trim()
    val redirectPath = context.getString(R.string.auth_redirect_uri_path).trim()
    AuthConfigData(
      clientId = context.getString(R.string.auth_client_id).trim(),
      redirectUri = "$redirectHost:$redirectPath",
      authorizationScope = context.getString(R.string.auth_authorization_scope).trim(),
      discoveryUri = discoveryUrl,
      authorizationEndpointUri = "",
      registrationEndpointUri = "",
      tokenEndpointUri = "",
      userInfoEndpointUri = "",
      endSessionEndpoint = "",
      httpsRequired = context.resources.getBoolean(R.bool.auth_https_required),
      replaceLocalhostBy1022 = true,
    )
  }
  val clientId: String
    get() = authConfigData.clientId

  val scope: String?
    get() = authConfigData.authorizationScope

  val redirectUri: Uri?
    get() = Uri.parse(authConfigData.redirectUri)

  val discoveryUri: Uri?
    get() =
      if (authConfigData.discoveryUri.isNullOrBlank()) {
        null
      } else {
        Uri.parse(authConfigData.discoveryUri)
      }

  val authEndpointUri: Uri?
    get() = Uri.parse(authConfigData.authorizationEndpointUri)

  val tokenEndpointUri: Uri?
    get() = Uri.parse(authConfigData.tokenEndpointUri)

  val registrationEndpointUri: Uri?
    get() = Uri.parse(authConfigData.registrationEndpointUri)

  val endSessionEndpoint: Uri?
    get() = Uri.parse(authConfigData.endSessionEndpoint)

  val connectionBuilder: ConnectionBuilder
    get() =
      if (authConfigData.httpsRequired) {
        DefaultConnectionBuilder.INSTANCE
      } else {
        // Plain http is allowed (the network security config decides which hosts), https needs a
        // certificate the system trusts: no build accepts every certificate.
        PlainHttpConnectionBuilder
      }

  /**
   * Development only: the provider announces "localhost" (a Keycloak in a container on the
   * developer's machine), which inside the Android emulator is the machine's address 10.0.2.2.
   */
  val replaceLocalhost: Boolean
    get() =
      BuildConfig.DEBUG &&
        !authConfigData.httpsRequired &&
        (authConfigData.replaceLocalhostBy1022 ?: true)

  init {
    // Without single sign-on there is nothing to check.
    if (AuthMethod.OPENID in AuthMethod.offeredBy(appConfig.authMethod)) {
      AuthConfigUtil.isRequiredConfigString(authConfigData.clientId)
      AuthConfigUtil.isRequiredConfigString(authConfigData.authorizationScope)
      AuthConfigUtil.isRequiredConfigUri(authConfigData.redirectUri)
      AuthConfigUtil.isRedirectUriRegistered(
        context,
        checkNotNull(authConfigData.redirectUri) { "auth_config has no redirect uri" },
      )
    }
  }

  companion object {
    @SuppressLint("StaticFieldLeak") private var INSTANCE: AuthConfiguration? = null

    @Synchronized
    fun getInstance(context: Context): AuthConfiguration {
      if (INSTANCE == null) {
        INSTANCE = AuthConfiguration(context.applicationContext)
      }
      return INSTANCE as AuthConfiguration
    }
  }
}
