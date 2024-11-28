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
package org.openmrs.android.fhir

import android.app.Application
import android.content.Context
import com.google.android.fhir.DatabaseErrorStrategy.RECREATE_AT_OPEN
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
import org.openmrs.android.fhir.data.RestApiManager
import org.openmrs.android.fhir.di.AppComponent
import org.openmrs.android.fhir.di.DaggerAppComponent
import timber.log.Timber

class FhirApplication : Application(), DataCaptureConfig.Provider {

  val appComponent: AppComponent by lazy { constructApplicationGraph() }

  private var dataCaptureConfig: DataCaptureConfig? = null

  private val dataStore by lazy { DemoDataStore(this) }

  private val restApiClient: RestApiManager by lazy { initializeRestApiManager() }

  override fun onCreate() {
    super.onCreate()
    Timber.e("FHIR Application started. Test Error log. Is Debug " + BuildConfig.DEBUG)
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
          httpLogger =
            HttpLogger(
              HttpLogger.Configuration(
                if (BuildConfig.DEBUG) HttpLogger.Level.BODY else HttpLogger.Level.BASIC,
              ),
            ) {
              Timber.tag("App-HttpLog").d(it)
            },
        ),
      ),
    )
    dataCaptureConfig =
      DataCaptureConfig().apply {
        urlResolver = ReferenceUrlResolver(this@FhirApplication as Context)
        xFhirQueryResolver = XFhirQueryResolver { it ->
          FhirEngineProvider.getInstance(applicationContext).search(it).map { it.resource }
        }
      }
  }

  private fun initializeRestApiManager(): RestApiManager {
    val restApiManager = RestApiManager.getInstance(applicationContext)
    CoroutineScope(Dispatchers.IO).launch {
      restApiManager.initialize(
        applicationContext.applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID],
      )
    }
    return restApiManager
  }

  private fun constructApplicationGraph(): AppComponent {
    return DaggerAppComponent.factory().create(applicationContext)
  }

  companion object {

    fun appComponent(context: Context) =
      (context.applicationContext as FhirApplication).appComponent

    fun dataStore(context: Context) = (context.applicationContext as FhirApplication).dataStore

    fun restApiClient(context: Context) =
      (context.applicationContext as FhirApplication).restApiClient

    fun fhirBaseURl(context: Context) = context.getString(R.string.fhir_base_url)

    fun openmrsRestUrl(context: Context) = context.getString(R.string.openmrs_rest_url)

    fun checkServerUrl(context: Context) = context.getString(R.string.check_server_url)
  }

  override fun getDataCaptureConfig(): DataCaptureConfig = dataCaptureConfig ?: DataCaptureConfig()
}
