import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.ServerConstants.CHECK_SERVER_URL
import org.openmrs.android.fhir.ServerConstants.REST_BASE_URL
import java.io.IOException

class RestApiManager private constructor(private val context: Context) {
    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private var sessionCookie: String? = null

    companion object {
        @Volatile
        private var INSTANCE: RestApiManager? = null

        fun getInstance(context: Context): RestApiManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RestApiManager(context).also { INSTANCE = it }
            }
        }
    }

    suspend fun initialize(locationId: String?) {
        locationId?.let {
            updateSessionLocation(it)
        }
    }
    private suspend fun setSessionLocation(locationId: String) {
        withContext(Dispatchers.IO) {
            sessionCookie = ""
            val url = REST_BASE_URL + "session"
            val json = """{"sessionLocation": "$locationId"}"""
            val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + LoginRepository.getInstance(context).getAccessToken())
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

        sessionCookie?.let {
            requestBuilder.addHeader("Cookie", it.split(";")[0])
        }
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()

        return response
    }

    fun isServerLive(): Boolean {
        val request = Request.Builder().url(CHECK_SERVER_URL).build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: IOException) {
            false
        }
    }
}
