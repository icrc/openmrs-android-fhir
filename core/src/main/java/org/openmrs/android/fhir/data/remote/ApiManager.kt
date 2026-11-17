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
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.sync.download.UrlDownloadRequest
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.BuildConfig
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.data.remote.interceptor.AddCookiesInterceptor
import org.openmrs.android.fhir.data.remote.interceptor.ReceivedCookiesInterceptor
import org.openmrs.android.fhir.data.remote.model.IdentifierWrapper
import org.openmrs.android.fhir.data.remote.model.ResponseWrapper
import org.openmrs.android.fhir.data.remote.model.SessionLocation
import org.openmrs.android.fhir.data.remote.model.SessionResponse
import org.openmrs.android.fhir.data.sync.DemoSyncProgressStore
import org.openmrs.android.fhir.data.sync.ListDownloadStore
import org.openmrs.android.fhir.data.sync.PatientDownloadWorkManager
import org.openmrs.android.fhir.data.sync.ServerPresence
import org.openmrs.android.fhir.data.sync.SyncUrls
import org.openmrs.android.fhir.patientlists.GroupMapper
import org.openmrs.android.fhir.patientlists.GroupServer
import org.openmrs.android.fhir.patientlists.PatientSummary
import org.openmrs.android.fhir.patientlists.ServerGroupRead
import org.openmrs.android.fhir.patientlists.ServerGroupWrite
import org.openmrs.android.fhir.patientlists.ServerGroups
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiManager @Inject constructor(context: Context) :
  Api, ServerPresence, GroupServer, ServerProbe {
  private val appContext = context.applicationContext

  // Read for every request: the user may sign in after this object was created (a guest session
  // that signs in to sync), and a token captured at construction would stay empty.
  private val loginRepository = LoginRepository.getInstance(context)
  private val okHttpClientBuilder: OkHttpClient.Builder by lazy {
    val okHttpClientBuilder =
      HttpClients.base
        .newBuilder()
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
    if (BuildConfig.DEBUG) {
      // Only in a debug build, and never the credentials: the bodies are the patients' data.
      okHttpClientBuilder.addInterceptor(
        HttpLoggingInterceptor().apply {
          level = HttpLoggingInterceptor.Level.BASIC
          redactHeader("Authorization")
          redactHeader("Cookie")
          redactHeader("Set-Cookie")
        },
      )
    }
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
    // Each word is a name of its own (repeated parameters are ANDed): "Ann Smith" has no name that
    // is
    // all of it.
    val names =
      text.trim().split(Regex("\\s+")).joinToString("&") {
        "name=" + URLEncoder.encode(it, "UTF-8")
      }
    val base =
      ServerSettings.fhirBaseUrl(appContext) +
        "Patient?_count=$PATIENT_SEARCH_LIMIT&_elements=$PATIENT_SEARCH_ELEMENTS&"
    val byName = async { patientSearch(base + names) }
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
   * observations, following the search's `next` links. Read only. [PatientRecord.complete] is false
   * when the last page was not reached.
   */
  suspend fun fetchPatientRecord(patientId: String): PatientRecord {
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
    return PatientRecord(resources.filter { it !is OperationOutcome }, complete = url == null)
  }

  class PatientRecord(val resources: List<Resource>, val complete: Boolean)

  override suspend fun isGone(type: ResourceType, id: String): Boolean? {
    if (!id.all { it.isLetterOrDigit() || it == '-' }) return null
    val response = fhirApiService.getResource(ServerSettings.fhirBaseUrl(appContext) + "$type/$id")
    response.body()?.close()
    response.errorBody()?.close()
    return when (response.code()) {
      404,
      410, -> true
      in 200..299 -> false
      else -> null
    }
  }

  override suspend fun existingIds(type: ResourceType, ids: Collection<String>): Set<String>? {
    if (ids.isEmpty() || ids.any { id -> !id.all { it.isLetterOrDigit() || it == '-' } }) {
      return null
    }
    val found = mutableSetOf<String>()
    var url: String? =
      ServerSettings.fhirBaseUrl(appContext) +
        "$type?_id=${ids.joinToString(",")}&_elements=id&_count=${ids.size.coerceAtLeast(100)}"
    var pages = 0
    while (url != null && pages++ < MAX_EXISTENCE_PAGES) {
      val response = fhirApiService.getBundle(url)
      if (!response.isSuccessful) return null
      val bundle = response.body() ?: return null
      bundle.entry.forEach { entry -> entry.resource?.idElement?.idPart?.let { found += it } }
      url = bundle.link.firstOrNull { it.relation == "next" }?.url
    }
    return if (url == null) found else null
  }

  override suspend fun groups(): ServerGroups =
    readGroups(
      ServerSettings.fhirBaseUrl(appContext) +
        "Group?_elements=$GROUP_ELEMENTS&_count=$PATIENT_LIST_PAGE",
    )

  override suspend fun group(id: String): ServerGroupRead {
    require(isFhirId(id)) { "Not a patient list id" }
    val response = fhirApiService.getResource(ServerSettings.fhirBaseUrl(appContext) + "Group/$id")
    val text =
      (if (response.isSuccessful) response.body() else response.errorBody())?.use { it.string() }
    return when {
      response.code() == 404 || response.code() == 410 -> ServerGroupRead.Gone
      !response.isSuccessful ->
        throw IOException("Server answered ${response.code()} for a patient list")
      else ->
        ServerGroupRead.Found(
          parseResource(text) as? Group
            ?: throw IOException("The server did not answer with a Group"),
        )
    }
  }

  override suspend fun patients(ids: List<String>): List<PatientSummary> {
    val clean = ids.filter(::isFhirId)
    if (clean.isEmpty()) return emptyList()
    val response =
      fhirApiService.getBundle(
        ServerSettings.fhirBaseUrl(appContext) +
          "Patient?_id=${clean.joinToString(",")}&_count=${clean.size}" +
          "&_elements=$PATIENT_SUMMARY_ELEMENTS",
      )
    if (!response.isSuccessful) throw IOException("Server answered ${response.code()} for patients")
    return response.body()?.entry.orEmpty().mapNotNull {
      (it.resource as? Patient)?.let(GroupMapper::summary)
    }
  }

  /**
   * A `PUT` with the id chosen on the device, not a `POST`: HAPI replaces the id of a created
   * resource, and the changes queued for this patient list carry the device's id. Sent again, it is
   * an update, so a lost answer does no harm.
   */
  override suspend fun create(group: Group): ServerGroupWrite = write {
    fhirApiService.putResource(
      ServerSettings.fhirBaseUrl(appContext) + "Group/${group.idElement.idPart}",
      group,
    )
  }

  override suspend fun update(group: Group): ServerGroupWrite = write {
    fhirApiService.putResource(
      ServerSettings.fhirBaseUrl(appContext) + "Group/${group.idElement.idPart}",
      group,
    )
  }

  override suspend fun delete(id: String): ServerGroupWrite = write {
    require(isFhirId(id)) { "Not a patient list id" }
    fhirApiService.deleteResource(ServerSettings.fhirBaseUrl(appContext) + "Group/$id")
  }

  override suspend fun addMembers(id: String, patientIds: List<String>): ServerGroupWrite = write {
    require(isFhirId(id)) { "Not a patient list id" }
    fhirApiService.postResource(
      ServerSettings.fhirBaseUrl(appContext) + "Group/$id/\$add-members",
      GroupMapper.parameters(patientIds),
    )
  }

  override suspend fun removeMembers(id: String, patientIds: List<String>): ServerGroupWrite =
    write {
      require(isFhirId(id)) { "Not a patient list id" }
      fhirApiService.postResource(
        ServerSettings.fhirBaseUrl(appContext) + "Group/$id/\$remove-members",
        GroupMapper.parameters(patientIds),
      )
    }

  /**
   * A change to a patient list, answered. 5xx and "too many requests" are the server being busy,
   * not a refusal: the change is kept and goes later. The reason of a refusal is the server's own
   * text.
   */
  private suspend fun write(call: suspend () -> Response<ResponseBody>): ServerGroupWrite {
    val response = call()
    val text =
      (if (response.isSuccessful) response.body() else response.errorBody())?.use { it.string() }
    val code = response.code()
    return when {
      response.isSuccessful -> ServerGroupWrite.Done(parseResource(text) as? Group)
      // 401: the session ended (a single sign-on token, say): the change is fine, and goes after
      // the next sign-in.
      code == 401 || code == 408 || code == 429 || code >= 500 ->
        ServerGroupWrite.Unreachable("Server answered $code")
      else -> ServerGroupWrite.Refused(code, refusalReason(text, code))
    }
  }

  override suspend fun send(method: String, url: String, body: Resource?): ProbeAnswer =
    try {
      val response =
        when (method) {
          "GET" -> fhirApiService.getResource(url)
          "POST" -> fhirApiService.postResource(url, body ?: Parameters())
          "PUT" -> fhirApiService.putResource(url, requireNotNull(body))
          "DELETE" -> fhirApiService.deleteResource(url)
          else -> throw IllegalArgumentException("Unknown method $method")
        }
      val text =
        (if (response.isSuccessful) response.body() else response.errorBody())?.use { it.string() }
      ProbeAnswer(response.code(), text?.take(PROBE_TEXT_LENGTH))
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      ProbeAnswer(0, e.message)
    }

  private fun parseResource(text: String?): Resource? =
    if (text.isNullOrBlank()) {
      null
    } else {
      try {
        FhirContext.forR4Cached().newJsonParser().parseResource(text) as? Resource
      } catch (e: Exception) {
        null
      }
    }

  private fun refusalReason(text: String?, code: Int): String {
    val issue = (parseResource(text) as? OperationOutcome)?.issueFirstRep
    val reason = issue?.diagnostics ?: issue?.details?.text
    return reason?.take(REASON_LENGTH)?.takeIf { it.isNotBlank() } ?: "Server answered $code"
  }

  private fun isFhirId(id: String) = id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' }

  /**
   * The patient lists (Groups) of [search] (`Group?location=...`, see `SyncUrls.groups`), following
   * the `next` links. Read only.
   */
  suspend fun fetchPatientLists(search: String): List<Group> {
    val separator = if (search.contains('?')) "&" else "?"
    return readGroups(
        ServerSettings.fhirBaseUrl(appContext) + search + separator + "_count=$PATIENT_LIST_PAGE",
      )
      .groups
  }

  private suspend fun readGroups(firstPage: String): ServerGroups {
    val groups = mutableListOf<Group>()
    var url: String? = firstPage
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
    return ServerGroups(groups, complete = url == null)
  }

  /**
   * The patients of the patient lists [listIds] with their encounters and observations, page by
   * page ([onPage] receives each one as it arrives), the way the sync downloads them
   * ([PatientDownloadWorkManager]: small pages, and what is done is remembered, so a download that
   * is cut goes on from where it was). Unlike the sync it does not ask only for what changed since
   * the last one: the lists are new to the device. Read only.
   *
   * @return how many patients came.
   */
  suspend fun downloadPatientsOfLists(
    listIds: Set<String>,
    onPage: suspend (List<Resource>) -> Unit,
  ): Int {
    val manager =
      PatientDownloadWorkManager(
        searches = emptyList(),
        idSearches = SyncUrls.patientIdSearches(listIds),
        store = ListDownloadStore(DemoSyncProgressStore(DemoDataStore(appContext))),
        skipKnownPatients = true,
      )
    var patients = 0
    var requests = 0
    while (true) {
      val request = manager.getNextRequest() as? UrlDownloadRequest ?: break
      check(requests++ < MAX_PATIENT_DOWNLOAD_REQUESTS) { "Too many requests for the patients" }
      val url =
        if (request.url.startsWith("http")) {
          request.url
        } else {
          ServerSettings.fhirBaseUrl(appContext) + request.url
        }
      val response = fhirApiService.getBundle(url)
      if (!response.isSuccessful) throw IOException("Server answered ${response.code()}")
      val bundle = response.body() ?: break
      val resources = manager.processResponse(bundle).filter { it !is OperationOutcome }
      patients += resources.count { it is Patient }
      if (resources.isNotEmpty()) onPage(resources)
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
private const val MAX_EXISTENCE_PAGES = 3
private const val MAX_PATIENT_LIST_PAGES = 20
private const val PATIENT_LIST_PAGE = 100
private const val PROBE_TEXT_LENGTH = 4000
private const val MAX_PATIENT_DOWNLOAD_REQUESTS = 200_000
private const val PATIENT_SEARCH_LIMIT = 20
private const val PATIENT_SEARCH_ELEMENTS =
  "id,name,identifier,birthDate,gender,address,telecom,active"

private const val GROUP_ELEMENTS = "id,name,quantity,extension,managingEntity,meta,active"
private const val PATIENT_SUMMARY_ELEMENTS = "id,name,identifier,birthDate,gender"
private const val REASON_LENGTH = 200
