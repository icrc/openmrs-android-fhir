import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.ServerConstants.REST_BASE_URL
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.IdentifierModel

class PatientIdentifierManager private constructor(private val context: Context) {

    private var restApiClient: RestApiManager = FhirApplication.restApiClient(context)
    private var database: AppDatabase = FhirApplication.roomDatabase(context)

    companion object {
        private lateinit var instance: PatientIdentifierManager
        suspend fun updateAvailablePatientIdentifiers(context: Context) {
            withContext(Dispatchers.IO) {
                instance = PatientIdentifierManager(context)
                val selectedIdentifierTypes = context.applicationContext.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.toList()
                if (selectedIdentifierTypes != null) {
                    //To fetch only identifier which  requires server uuid.
                    instance.fetchIdentifiers(selectedIdentifierTypes.filter {identifierTypeId ->
                        val isUnique = let {instance.database.dao().getIdentifierTypeById(identifierTypeId)?.isUnique}
                        isUnique != null && isUnique != "null"
                    })
                }
            }
        }


        private const val HSU_ID_TYPE = "691eed12-c0f1-11e2-94be-8c13b969e334" //TODO: Set default identifierType from configuration.
        private const val DEFAULT_FETCH_COUNT = 2
        private const val MINIMUM_THRESHOLD = 1 // TODO: update logic of minimum threshold in the app

        suspend fun getNextIdentifiers(): MutableMap<String?,String?> {
            return withContext(Dispatchers.IO) {
                val selectedIdentifierTypes = instance.context.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.toList()
                if(selectedIdentifierTypes != null){
                    val filteredIdentifierTypes = selectedIdentifierTypes.filter { identifierTypeId ->
                        val isUnique = let {instance.database.dao().getIdentifierTypeById(identifierTypeId)?.isUnique }
                        isUnique != null && isUnique != "null"
                    }
                    val identifierMap = mutableMapOf<String?, String?>()
                    for (identifierTypeId in filteredIdentifierTypes){
                        val identifierType = let{instance.database.dao().getIdentifierTypeById(identifierTypeId)}
                        val value = let {instance.database.dao().getOneIdentifierByType(identifierTypeId)?.value}
                        if (value != null) {
                            identifierMap[identifierType?.display] = value
                            //Delete identifier
                            instance.database.dao().deleteIdentifierByValue(value)
                        }
                    }
                    identifierMap

                } else {
                    mutableMapOf()
                }

            }
        }
    }

    suspend fun fetchIdentifiers(selectedIdentifierTypes: List<String>, count: Int = DEFAULT_FETCH_COUNT) {
        withContext(Dispatchers.IO) {
            for (identifierTypeId in selectedIdentifierTypes) {
                val newIdentifiers = mutableListOf<String>()
                for (i in 1..count) {
                    val identifier = fetchIdentifierFromEndpoint(identifierTypeId)
                    if (identifier != null) {
                        newIdentifiers.add(identifier)
                    }
                }
                storeIdentifiers(newIdentifiers, identifierTypeId)
            }

        }
    }

    private suspend fun fetchIdentifierFromEndpoint(identifierId: String? = HSU_ID_TYPE): String? {
        return withContext(Dispatchers.IO) {
            try {

                val requestBuilder = Request.Builder()
                    .url(getEndpoint(identifierId))
                    .post(RequestBody.create("application/json; charset=utf-8".toMediaType(), "{}"))
                val response = restApiClient.call(requestBuilder)

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

    private suspend fun storeIdentifiers(identifierValues: List<String>, identifierTypeId: String) {
        val identifiers: MutableList<IdentifierModel> = mutableListOf()
        for (identifierValue in identifierValues){
            identifiers.add(IdentifierModel(
                identifierValue,
                identifierTypeId
            ))
        }
        withContext(Dispatchers.IO) {
            database.dao().insertAllIdentifierModel(identifiers)
        }
    }

    private fun getEndpoint(idType: String? = HSU_ID_TYPE) : String{
        return REST_BASE_URL + "idgen/identifiersource/" + idType + "/identifier"
    }

}
