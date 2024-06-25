/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.android.fhir

import android.app.Application
import android.content.Context
import com.google.android.fhir.DatabaseErrorStrategy.RECREATE_AT_OPEN
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineConfiguration
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.ServerConfiguration
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.android.fhir.datacapture.XFhirQueryResolver
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.remote.HttpLogger
import okhttp3.OkHttpClient
import timber.log.Timber

class FhirApplication : Application(), DataCaptureConfig.Provider {
  // Only initiate the FhirEngine when used for the first time, not when the app is created.
  private val fhirEngine: FhirEngine by lazy { constructFhirEngine() }

  private var dataCaptureConfig: DataCaptureConfig? = null

  private val dataStore by lazy { DemoDataStore(this) }

  val okHttpClient: OkHttpClient by lazy { constructOkHttpClient() }

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    FhirEngineProvider.init(
      FhirEngineConfiguration(
        enableEncryptionIfSupported = false,
        RECREATE_AT_OPEN,
        ServerConfiguration(
          ServerConstants.BASE_URL,
          authenticator = LoginRepository.getInstance(applicationContext),
          httpLogger = HttpLogger(
            HttpLogger.Configuration(
              if (BuildConfig.DEBUG) HttpLogger.Level.BODY else HttpLogger.Level.BASIC,
            ),
          ) {
            Timber.tag("App-HttpLog").d(it)
          }
        )
      )
    )
    dataCaptureConfig =
      DataCaptureConfig().apply {
        urlResolver = ReferenceUrlResolver(this@FhirApplication as Context)
        xFhirQueryResolver = XFhirQueryResolver { it -> fhirEngine.search(it).map { it.resource } }
      }
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(this)
  }

  private fun constructOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder().build()
  }

  companion object {
    fun fhirEngine(context: Context) = (context.applicationContext as FhirApplication).fhirEngine

    fun dataStore(context: Context) = (context.applicationContext as FhirApplication).dataStore

    fun okHttpClient(context: Context) = (context.applicationContext as FhirApplication).okHttpClient
  }

  override fun getDataCaptureConfig(): DataCaptureConfig = dataCaptureConfig ?: DataCaptureConfig()
}

object ServerConstants {
  const val BASE_URL = "http://10.0.2.2:8080/openmrs/ws/fhir2/R4/"
  const val REST_BASE_URL = "http://10.0.2.2:8080/openmrs/ws/rest/v1/"
}
