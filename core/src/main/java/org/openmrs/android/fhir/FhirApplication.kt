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

import RestApiManager
import android.app.Application
import android.content.Context
import androidx.room.Room
import com.google.android.fhir.DatabaseErrorStrategy.RECREATE_AT_OPEN
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineConfiguration
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.ServerConfiguration
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.android.fhir.datacapture.XFhirQueryResolver
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.remote.HttpLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import timber.log.Timber

class FhirApplication : Application(), DataCaptureConfig.Provider {
  // Only initiate the FhirEngine when used for the first time, not when the app is created.
  private val fhirEngine: FhirEngine by lazy { constructFhirEngine() }

  private val roomDatabase: AppDatabase by lazy { constructRoomDatabase()}

  private var dataCaptureConfig: DataCaptureConfig? = null

  private val dataStore by lazy { DemoDataStore(this) }

  private val restApiClient: RestApiManager by lazy { initializeRestApiManager() }

  override fun onCreate() {
    super.onCreate()
    Timber.e("FHIR Application started. Test Error log. Is Debug "+BuildConfig.DEBUG  )
    Timber.d("FHIR Application started. Test Debug log")
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    FhirEngineProvider.init(
      FhirEngineConfiguration(
        enableEncryptionIfSupported = false,
        RECREATE_AT_OPEN,
        ServerConfiguration(
          fhirBaseURl(applicationContext),
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
    constructRoomDatabase()
  }

  private fun initializeRestApiManager(): RestApiManager {
    val restApiManager = RestApiManager.getInstance(applicationContext)
    CoroutineScope(Dispatchers.IO).launch {
      restApiManager.initialize(applicationContext.applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID])
    }
    return restApiManager
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(this)
  }

  private fun constructRoomDatabase() : AppDatabase{
    return Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java, "openmrs_android_fhir"
    ).build()
  }

  companion object {
    fun fhirEngine(context: Context) = (context.applicationContext as FhirApplication).fhirEngine

    fun dataStore(context: Context) = (context.applicationContext as FhirApplication).dataStore

    fun restApiClient(context: Context) = (context.applicationContext as FhirApplication).restApiClient

    fun roomDatabase(context: Context) = (context.applicationContext as FhirApplication).roomDatabase

    fun fhirBaseURl(context: Context)= context.getString(R.string.fhir_base_url)

    fun openmrsRestUrl(context: Context)= context.getString(R.string.openmrs_rest_url)

    fun checkServerUrl(context: Context)= context.getString(R.string.check_server_url)
  }

  override fun getDataCaptureConfig(): DataCaptureConfig = dataCaptureConfig ?: DataCaptureConfig()
}

