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
package org.openmrs.android.fhir.data

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.LoginRepository

class RestApiManager private constructor(private val context: Context) {
  private val client: OkHttpClient = OkHttpClient.Builder().build()
  private var sessionCookie: String? = null

  companion object {
    @Volatile private var INSTANCE: RestApiManager? = null

    fun getInstance(context: Context): RestApiManager {
      return INSTANCE
        ?: synchronized(this) { INSTANCE ?: RestApiManager(context).also { INSTANCE = it } }
    }
  }

  suspend fun initialize(locationId: String?) {
    locationId?.let { updateSessionLocation(it) }
  }

  private suspend fun setSessionLocation(locationId: String) {
    val accessToken = LoginRepository.getInstance(context).getAccessToken()
    val basicAuthEncodedString = LoginRepository.getInstance(context).getBasicAuthEncodedString()
    withContext(Dispatchers.IO) {
      sessionCookie = ""
      val url = FhirApplication.checkServerUrl(context)
      val json = """{"sessionLocation": "$locationId"}"""
      val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
      val request =
        Request.Builder()
          .url(url)
          .addHeader(
            "Authorization",
            when {
              accessToken.isNotEmpty() -> "Bearer $accessToken"
              basicAuthEncodedString.isNotEmpty() -> "Basic $basicAuthEncodedString"
              else -> ""
            },
          )
          .post(requestBody)
          .build()

      val response = client.newCall(request).execute()
      response.use {
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val cookies = response.headers("Set-Cookie")
        if (cookies.isNotEmpty()) {
          sessionCookie = cookies[0]
        }
      }
    }
  }

  suspend fun updateSessionLocation(locationId: String) {
    setSessionLocation(locationId)
  }

  fun call(requestBuilder: Request.Builder): Response {
    val accessToken = LoginRepository.getInstance(context).getAccessToken()
    requestBuilder.addHeader("Authorization", "Bearer $accessToken")

    sessionCookie?.let { requestBuilder.addHeader("Cookie", it.split(";")[0]) }
    val request = requestBuilder.build()
    val response = client.newCall(request).execute()

    return response
  }

  fun isServerLive(): Boolean {
    val request = Request.Builder().url(FhirApplication.checkServerUrl(context)).build()
    return try {
      client.newCall(request).execute().use { response -> response.isSuccessful }
    } catch (e: IOException) {
      false
    }
  }
}
