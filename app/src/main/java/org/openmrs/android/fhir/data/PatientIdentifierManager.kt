import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.ServerConstants.REST_BASE_URL
import org.openmrs.android.fhir.data.PreferenceKeys.Companion.PATIENT_IDENTIFIERS

class PatientIdentifierManager private constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("identifier_prefs", Context.MODE_PRIVATE)
    private val client: OkHttpClient = FhirApplication.okHttpClient(context)

    companion object {
        private lateinit var instance: PatientIdentifierManager

        fun initialize(context: Context) {
            instance = PatientIdentifierManager(context)
        }

        private val IDENTIFIERS_KEY = PATIENT_IDENTIFIERS
        private const val HSU_ID_TYPE = "691eed12-c0f1-11e2-94be-8c13b969e334"
        private const val DEFAULT_FETCH_COUNT = 10
        private const val MINIMUM_THRESHOLD = 3
        private const val ENDPOINT = REST_BASE_URL + "idgen/identifiersource/" + HSU_ID_TYPE + "/identifier"

        suspend fun getNextIdentifier(): String? {
            return instance.getNextIdentifierInternal()
        }
    }

    suspend fun fetchIdentifiers(count: Int = DEFAULT_FETCH_COUNT) {
        val newIdentifiers = mutableListOf<String>()
        for (i in 1..count) {
            val identifier = fetchIdentifierFromEndpoint()
            if (identifier != null) {
                newIdentifiers.add(identifier)
            }
        }
        storeIdentifiers(newIdentifiers)
    }

    private suspend fun fetchIdentifierFromEndpoint(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(ENDPOINT).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    jsonResponse.getString("identifier")
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun storeIdentifiers(identifiers: List<String>) {
        val existingIdentifiers = getStoredIdentifiers().toMutableList()
        existingIdentifiers.addAll(identifiers)
        sharedPreferences.edit().putStringSet(IDENTIFIERS_KEY, existingIdentifiers.toSet()).apply()
    }

    private fun getStoredIdentifiers(): List<String> {
        return sharedPreferences.getStringSet(IDENTIFIERS_KEY, emptySet())?.toList() ?: emptyList()
    }

    private suspend fun getNextIdentifierInternal(): String? {
        val identifiers = getStoredIdentifiers().toMutableList()
        return if (identifiers.isNotEmpty()) {
            val nextIdentifier = identifiers.removeAt(0)
            sharedPreferences.edit().putStringSet(IDENTIFIERS_KEY, identifiers.toSet()).apply()
            if (identifiers.size < MINIMUM_THRESHOLD && isInternetAvailable()) {
                fetchIdentifiers()
            }
            nextIdentifier
        } else {
            null
        }
    }

    private fun isInternetAvailable(): Boolean {
        // Implement your logic to check for internet connectivity
        return true
    }
}
