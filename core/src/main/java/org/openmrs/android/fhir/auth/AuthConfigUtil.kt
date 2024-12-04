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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import org.json.JSONObject
import org.openmrs.android.fhir.auth.model.EndpointConfig
import org.openmrs.android.fhir.extensions.fromJson
import org.openmrs.android.fhir.extensions.toJson

object AuthConfigUtil {

  class InvalidConfigurationException : Exception {
    internal constructor(reason: String?) : super(reason)

    internal constructor(reason: String?, cause: Throwable?) : super(reason, cause)
  }

  private fun getStringIfNotNull(propName: String?): String? {
    if (TextUtils.isEmpty(propName)) {
      throw InvalidConfigurationException("Missing field in the configuration JSON")
    }
    return propName
  }

  fun isRequiredConfigString(propName: String?) {
    getStringIfNotNull(propName)
  }

  fun isRequiredConfigUri(propName: String?) {
    val uriStr = getStringIfNotNull(propName)
    val uri: Uri =
      try {
        Uri.parse(uriStr)
      } catch (ex: Throwable) {
        throw InvalidConfigurationException("$propName could not be parsed", ex)
      }
    if (!uri.isHierarchical || !uri.isAbsolute) {
      throw InvalidConfigurationException("$propName must be hierarchical and absolute")
    }
    if (!TextUtils.isEmpty(uri.encodedUserInfo)) {
      throw InvalidConfigurationException("$propName must not have user info")
    }
    if (!TextUtils.isEmpty(uri.encodedQuery)) {
      throw InvalidConfigurationException("$propName must not have query parameters")
    }
    if (!TextUtils.isEmpty(uri.encodedFragment)) {
      throw InvalidConfigurationException("$propName must not have a fragment")
    }
  }

  fun isRequiredConfigWebUri(propName: String?): Uri {
    isRequiredConfigUri(propName)
    val uri: Uri = Uri.parse(propName)
    val scheme = uri.scheme
    if (TextUtils.isEmpty(scheme) || !("http" == scheme || "https" == scheme)) {
      throw InvalidConfigurationException("$propName must have an http or https scheme")
    }
    return uri
  }

  fun isRedirectUriRegistered(context: Context, redirectUri: String) {
    // ensure that the redirect URI declared in the configuration is handled by some activity
    // in the app, by querying the package manager speculatively
    val redirectIntent = Intent()
    redirectIntent.setPackage(context.packageName)
    redirectIntent.action = Intent.ACTION_VIEW
    redirectIntent.addCategory(Intent.CATEGORY_BROWSABLE)
    redirectIntent.data = Uri.parse(redirectUri)
    if (context.packageManager.queryIntentActivities(redirectIntent, 0).isEmpty()) {
      throw InvalidConfigurationException(
        "redirect_uri is not handled by any activity in this app! " +
          "Ensure that the appAuthRedirectScheme in your build.gradle file " +
          "is correctly configured, or that an appropriate intent filter " +
          "exists in your app manifest.",
      )
    }
  }

  fun replaceLocalhost(jsonString: String, replaceLocalhost: Boolean): JSONObject {
    val androidSystemLocalhostURL = "10.0.2.2"
    val localhostEndpoints = jsonString.fromJson<EndpointConfig>()
    if (!replaceLocalhost) {
      return JSONObject(localhostEndpoints.toJson())
    }
    val updatedAuthEndpoint =
      localhostEndpoints?.authorizationEndpoint?.replace("localhost", androidSystemLocalhostURL)
    val updatedTokenEndpoint =
      localhostEndpoints?.tokenEndpoint?.replace("localhost", androidSystemLocalhostURL)
    val updatedEndpointConfig = EndpointConfig(updatedAuthEndpoint!!, updatedTokenEndpoint!!)
    val oldDiscoveryDoc = JSONObject(jsonString).getString("discoveryDoc")
    val updatedDiscoveryDoc = oldDiscoveryDoc.replace("localhost", androidSystemLocalhostURL)
    val newJson = JSONObject(updatedEndpointConfig.toJson())
    newJson.put("discoveryDoc", JSONObject(updatedDiscoveryDoc))
    return newJson
  }
}
