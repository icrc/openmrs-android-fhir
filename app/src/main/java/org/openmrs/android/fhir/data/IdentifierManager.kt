import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.ServerConstants.REST_BASE_URL
import org.openmrs.android.fhir.data.PreferenceKeys.Companion.IDENTIFIERS

class IdentifierManager private constructor(private val context: Context) {

    private var restApiClient: RestApiManager = FhirApplication.restApiClient(context)
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(IDENTIFIERS, Context.MODE_PRIVATE)


    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: IdentifierManager
        private const val ENDPOINT = REST_BASE_URL + "patientidentifiertype"


        suspend fun fetchIdentifiers(context: Context) {
            withContext(Dispatchers.IO) {
                instance = IdentifierManager(context)
                val newIdentifiers = instance.fetchIdentifierFromEndpoint()
                if(newIdentifiers!= null){
                    instance.storeIdentifiers(newIdentifiers)
                }
            }
        }
    }

    private fun storeIdentifiers(identifiers: List<String>) {
        sharedPreferences.edit().putStringSet(IDENTIFIERS, identifiers.toSet()).apply()
    }

     suspend fun fetchIdentifierFromEndpoint(): List<String>? {
        return withContext(Dispatchers.IO) {
            try {

                val requestBuilder = Request.Builder()
                    .url(ENDPOINT)
                    .get()
                val response = restApiClient.call(requestBuilder)

                if (response.isSuccessful) {
                    val resArray = mutableListOf<String>()
                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    val identifiers = jsonResponse.getJSONArray("results")
                    for (i in 0..<identifiers.length()) {
                        val objects: JSONObject = identifiers.getJSONObject(i)
                        val uuid = objects["uuid"].toString()
                        val display = objects["display"].toString()
                        resArray.add("${uuid},${display}")
                    }
                    resArray.toList()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getStoredIdentifiers(): List<Pair<String,String>> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<Pair<String,String>>()
            val identifiers = sharedPreferences.getStringSet(IDENTIFIERS, emptySet())?.toList() ?: emptyList()
            if (identifiers.isNotEmpty()) {
                identifiers.forEach { identifier ->
                    result.add(
                        Pair(identifier.substringBefore(","),identifier.substringAfter(",")))
                }
            }
            result
        }
    }

    data class IdentifierItem(
        val display: String,
        val uuid: String
    )


}
