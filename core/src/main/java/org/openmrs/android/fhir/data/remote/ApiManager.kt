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
package org.openmrs.android.fhir.data.remote

import android.content.Context
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.hl7.fhir.r4.model.Bundle
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.remote.interceptor.AddCookiesInterceptor
import org.openmrs.android.fhir.data.remote.interceptor.ReceivedCookiesInterceptor
import org.openmrs.android.fhir.data.remote.model.IdentifierWrapper
import org.openmrs.android.fhir.data.remote.model.ResponseWrapper
import org.openmrs.android.fhir.data.remote.model.SessionLocation
import org.openmrs.android.fhir.data.remote.model.SessionResponse
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiManager @Inject constructor(context: Context) : Api {
  private val accessToken = LoginRepository.getInstance(context).getAccessToken()
  private val basicAuthEncodedString =
    LoginRepository.getInstance(context).getBasicAuthEncodedString()
  private val okHttpClientBuilder: OkHttpClient.Builder by lazy {
    val okHttpClientBuilder =
      OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          val request = chain.request().newBuilder()
          if (accessToken.isNotEmpty()) {
            request.addHeader(
              "Authorization",
              "Bearer $accessToken",
            )
          }
          if (basicAuthEncodedString.isNotEmpty()) {
            request.addHeader(
              "Authorization",
              "Bearer $basicAuthEncodedString",
            )
          }
          return@addInterceptor chain.proceed(request.build())
        }
        .addInterceptor(AddCookiesInterceptor(context))
        .addInterceptor(ReceivedCookiesInterceptor(context))
        .addInterceptor(
          HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
        )
    return@lazy okHttpClientBuilder
  }

  private val apiService: ApiService by lazy {
    return@lazy Retrofit.Builder()
      .baseUrl(context.resources.getString(R.string.openmrs_rest_url))
      .client(okHttpClientBuilder.build())
      .addConverterFactory(MoshiConverterFactory.create())
      .build()
      .create(ApiService::class.java)
  }

  private val fhirApiService: FhirApiService by lazy {
    return@lazy Retrofit.Builder()
      .baseUrl(context.resources.getString(R.string.fhir_base_url))
      .client(okHttpClientBuilder.build())
      .addConverterFactory(FhirConverterFactory.create())
      .build()
      .create(FhirApiService::class.java)
  }

  override suspend fun setLocationSession(sessionLocation: SessionLocation): ApiResponse<Any> {
    return executeApiHelper { apiService.setLocationSession(sessionLocation) }
  }

  override suspend fun getIdentifier(idType: String): ApiResponse<IdentifierWrapper> {
    return executeApiHelper { apiService.getIdentifier(idType) }
  }

  override suspend fun getLocations(context: Context): ApiResponse<Bundle> {
    return executeApiHelper {
      val regex =
        Regex("(Location\\?[^,]*)") // Extracts the part containing Location and its parameters
      val matchResult = regex.find(context.resources.getString(R.string.fhir_sync_urls))
      val extractedPart = matchResult?.value ?: "Location?_tag=Login+Location"
      fhirApiService.getLocations(
        context.resources.getString(R.string.fhir_base_url) + extractedPart,
      )
    }
  }

  override suspend fun getAutogeneratedIdentifier(): ApiResponse<ResponseWrapper> {
    return executeApiHelper { apiService.getAutogeneratedIdentifier() }
  }

  override suspend fun validateSession(authorization: String): ApiResponse<SessionResponse> {
    return executeApiHelper { apiService.validateSession(authorization) }
  }
}
