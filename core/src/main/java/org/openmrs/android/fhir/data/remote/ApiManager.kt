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
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.data.remote.interceptor.AddCookiesInterceptor
import org.openmrs.android.fhir.data.remote.interceptor.ReceivedCookiesInterceptor
import org.openmrs.android.fhir.data.remote.model.IdentifierWrapper
import org.openmrs.android.fhir.data.remote.model.ResponseWrapper
import org.openmrs.android.fhir.data.remote.model.SessionLocation
import org.openmrs.android.fhir.data.remote.model.SessionResponse
import org.openmrs.android.fhir.data.sync.SyncUrls
import org.openmrs.android.fhir.data.sync.applyPatientListScope
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiManager @Inject constructor(context: Context) : Api {
  private val appContext = context.applicationContext

  // Read for every request: the user may sign in after this object was created (a guest session
  // that signs in to sync), and a token captured at construction would stay empty.
  private val loginRepository = LoginRepository.getInstance(context)
  private val okHttpClientBuilder: OkHttpClient.Builder by lazy {
    val okHttpClientBuilder =
      OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          val original = chain.request()
          val request = original.newBuilder()
          resolveAuthorizationHeader(
              existing = original.header("Authorization"),
              accessToken = loginRepository.getAccessToken(),
              basicAuthEncodedString = loginRepository.getBasicAuthEncodedString(),
            )
            ?.let { request.header("Authorization", it) }
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
      .baseUrl(ServerSettings.restUrl(context))
      .client(okHttpClientBuilder.build())
      .addConverterFactory(MoshiConverterFactory.create())
      .build()
      .create(ApiService::class.java)
  }

  private val fhirApiService: FhirApiService by lazy {
    return@lazy Retrofit.Builder()
      .baseUrl(ServerSettings.fhirBaseUrl(context))
      .client(okHttpClientBuilder.build())
      .addConverterFactory(FhirConverterFactory.create())
      .build()
      .create(FhirApiService::class.java)
  }

  override suspend fun setLocationSession(sessionLocationMap: SessionLocation): ApiResponse<Any> {
    return executeApiHelper { apiService.setLocationSession(sessionLocationMap) }
  }

  override suspend fun getIdentifier(idType: String): ApiResponse<IdentifierWrapper> {
    return executeApiHelper { apiService.getIdentifier(idType) }
  }

  override suspend fun getLocation(context: Context, locationId: String): ApiResponse<Location> {
    return executeApiHelper {
      fhirApiService.getLocation(ServerSettings.fhirBaseUrl(context) + "Location/$locationId")
    }
  }

  override suspend fun getAutogeneratedIdentifier(): ApiResponse<ResponseWrapper> {
    return executeApiHelper { apiService.getAutogeneratedIdentifier() }
  }

  override suspend fun validateSession(authorization: String): ApiResponse<SessionResponse> {
    return executeApiHelper { apiService.validateSession(authorization) }
  }

  /**
   * Every Questionnaire the server publishes, following the search bundle's `next` links. Read
   * only: nothing is written to the server.
   */
  suspend fun fetchQuestionnaires(): List<Questionnaire> {
    val questionnaires = mutableListOf<Questionnaire>()
    var url: String? = ServerSettings.fhirBaseUrl(appContext) + "Questionnaire?_count=100"
    var pages = 0
    while (url != null && pages++ < MAX_QUESTIONNAIRE_PAGES) {
      val response = fhirApiService.getQuestionnaires(url)
      if (!response.isSuccessful) {
        throw IOException("Server answered ${response.code()} for $url")
      }
      val bundle = response.body() ?: break
      bundle.entry.mapNotNullTo(questionnaires) { it.resource as? Questionnaire }
      url = bundle.link.firstOrNull { it.relation == "next" }?.url
    }
    return questionnaires
  }

  /**
   * The patients of the server whose name or identifier match [text] (a FHIR search, read only), at
   * most [PATIENT_SEARCH_LIMIT] of each. Only what a result row shows is asked for.
   */
  suspend fun searchPatients(text: String): List<Patient> = coroutineScope {
    val query = URLEncoder.encode(text.trim(), "UTF-8")
    val base =
      ServerSettings.fhirBaseUrl(appContext) +
        "Patient?_count=$PATIENT_SEARCH_LIMIT&_elements=$PATIENT_SEARCH_ELEMENTS&"
    val byName = async { patientSearch(base + "name=$query") }
    // The identifier search is exact and cheap; a server that does not take it still answers by
    // name.
    val byIdentifier = async {
      try {
        patientSearch(base + "identifier=$query")
      } catch (e: IOException) {
        emptyList()
      }
    }
    (byName.await() + byIdentifier.await()).distinctBy { it.idElement.idPart }
  }

  private suspend fun patientSearch(url: String): List<Patient> {
    val response = fhirApiService.getBundle(url)
    // Only the status goes in the message: the URL holds what the user typed.
    if (!response.isSuccessful) throw IOException("Server answered ${response.code()} to a search")
    return response.body()?.entry.orEmpty().mapNotNull { it.resource as? Patient }
  }

  /**
   * Everything the sync downloads about one patient: the patient, their encounters and their
   * observations, following the search's `next` links. Read only.
   */
  suspend fun fetchPatientRecord(patientId: String): List<Resource> {
    require(patientId.all { it.isLetterOrDigit() || it == '-' }) { "Not a patient id" }
    val resources = mutableListOf<Resource>()
    var url: String? =
      ServerSettings.fhirBaseUrl(appContext) +
        "Patient?_id=$patientId&_revinclude=Encounter:patient&_revinclude=Observation:patient"
    var pages = 0
    while (url != null && pages++ < MAX_RECORD_PAGES) {
      val response = fhirApiService.getBundle(url)
      if (!response.isSuccessful) throw IOException("Server answered ${response.code()}")
      val bundle = response.body() ?: break
      bundle.entry.mapNotNullTo(resources) { it.resource }
      url = bundle.link.firstOrNull { it.relation == "next" }?.url
    }
    return resources.filter { it !is OperationOutcome }
  }

  /**
   * The patient lists (Groups) of [search] (`Group?location=...`, see `SyncUrls.groups`), following
   * the `next` links. Read only.
   */
  suspend fun fetchPatientLists(search: String): List<Group> {
    val groups = mutableListOf<Group>()
    val separator = if (search.contains('?')) "&" else "?"
    var url: String? =
      ServerSettings.fhirBaseUrl(appContext) + search + separator + "_count=$PATIENT_LIST_PAGE"
    var pages = 0
    while (url != null && pages++ < MAX_PATIENT_LIST_PAGES) {
      val response = fhirApiService.getBundle(url)
      if (!response.isSuccessful) {
        throw IOException("Server answered ${response.code()} for the patient lists")
      }
      val bundle = response.body() ?: break
      bundle.entry.mapNotNullTo(groups) { it.resource as? Group }
      url = bundle.link.firstOrNull { it.relation == "next" }?.url
    }
    return groups
  }

  /**
   * The patients of the patient lists [listIds] with their encounters and observations, page by
   * page ([onPage] receives each one as it arrives), following the `next` links. Unlike the sync it
   * does not ask only for what changed since the last one: the lists are new to the device. Read
   * only.
   *
   * @return how many patients came.
   */
  suspend fun downloadPatientsOfLists(
    listIds: Set<String>,
    onPage: suspend (List<Resource>) -> Unit,
  ): Int {
    val search = applyPatientListScope(SyncUrls.patients(appContext), listIds) ?: return 0
    var patients = 0
    var url: String? =
      ServerSettings.fhirBaseUrl(appContext) + search + "&_count=$PATIENT_DOWNLOAD_PAGE"
    var pages = 0
    while (url != null) {
      check(pages++ < MAX_PATIENT_DOWNLOAD_PAGES) { "Too many pages of patients" }
      val response = fhirApiService.getBundle(url)
      if (!response.isSuccessful) throw IOException("Server answered ${response.code()}")
      val bundle = response.body() ?: break
      val resources = bundle.entry.mapNotNull { it.resource }.filter { it !is OperationOutcome }
      patients += resources.count { it is Patient }
      if (resources.isNotEmpty()) onPage(resources)
      url = bundle.link.firstOrNull { it.relation == "next" }?.url
    }
    return patients
  }

  /**
   * Replaces [resource] on the server with the whole of it (a PUT to its own address).
   *
   * @throws IOException when the server does not accept it.
   */
  suspend fun putResource(resource: Resource) {
    val url =
      ServerSettings.fhirBaseUrl(appContext) + "${resource.fhirType()}/${resource.idElement.idPart}"
    val response = fhirApiService.putResource(url, resource)
    if (!response.isSuccessful) {
      throw IOException("Server answered ${response.code()} to the update")
    }
  }

  suspend fun checkServerConnection(url: String): Boolean {
    return try {
      val response = apiService.checkServerStatus(url)
      response.isSuccessful || response.code() == 401
    } catch (_: Exception) {
      false
    }
  }
}

/**
 * Chooses the single `Authorization` header for a REST request.
 *
 * A request that already carries one (for example the explicit Basic header used to validate a
 * session at login) is left alone. Sending two `Authorization` headers makes the reverse proxy
 * answer 400, which used to make login impossible whenever credentials were already stored.
 */
internal fun resolveAuthorizationHeader(
  existing: String?,
  accessToken: String,
  basicAuthEncodedString: String,
): String? =
  when {
    existing != null -> null
    basicAuthEncodedString.isNotEmpty() -> "Bearer $basicAuthEncodedString"
    accessToken.isNotEmpty() -> "Bearer $accessToken"
    else -> null
  }

private const val MAX_QUESTIONNAIRE_PAGES = 20
private const val MAX_RECORD_PAGES = 20
private const val MAX_PATIENT_LIST_PAGES = 20
private const val PATIENT_LIST_PAGE = 100
private const val PATIENT_DOWNLOAD_PAGE = 50
private const val MAX_PATIENT_DOWNLOAD_PAGES = 500
private const val PATIENT_SEARCH_LIMIT = 20
private const val PATIENT_SEARCH_ELEMENTS =
  "id,name,identifier,birthDate,gender,address,telecom,active"
