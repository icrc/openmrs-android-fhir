package org.openmrs.android.fhir
import org.hl7.fhir.r4.model.Reference
import java.text.SimpleDateFormat
import java.util.Locale

data class Form(
    val display: String,
    val encounterType: String,
    val form: String,
    val resource: String
) {
    constructor(display: String, code: String, form: String) : this(display, code, form, "")
}

data class Patient(
    val display: String,
    val code: String,
    val resource: String
) {
    constructor(display: String, code: String) : this(display, code, "")
}

data class User(
    val uuid: String,
    val display: String,
    val providerUuid: String,
)



object MockConstants {
    val DATE24_FORMATTER = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    val LOCATION = createMockLocationReference()
    val AUTHENTICATED_USER = getAuthenticatedUser()
    val VISIT_TYPE_UUID = "7b0f5697-27e3-40c4-8bae-f4049abfb4ed";
    val MOCK_FORMS = listOf(
        Form(
            display = "Assessment Form",
            encounterType = "0c63150d-ff39-42e1-9048-834mh76p2s72",
            form = "17dea7cc-a0e6-34be-af31-9867004202df",
            resource = "assessment.json"
        ),
        Form(
            display = "Followup Form",
            encounterType = "07a7dd1c-7280-483a-a3bc-01be995293ac",
            form = "798a600f-fe2b-3d24-ad15-19d8ff2c17ac",
            resource = "assessment.json"
        ),
        Form(
            display = "Closure Form",
            encounterType = "95458795-3o06-4l59-9508-c217aa21ea26",
            form = "07154b0d-2163-3fe2-b75f-096368f0f852",
            resource = "assessment.json"
        )
    )
    val WRAP_ENCOUNTER = true
}
fun createMockLocationReference(): Reference {
    val locationReference = Reference().apply {
        reference = "Location/8d6c993e-c2cc-11de-8d13-0010c6dffd0f"
        type = "Location"
        display = "Unknown Location"
    }
    return locationReference
}

fun getAuthenticatedUser(): User {
    val authenticatedUser = User(
        uuid = "1c3db49d-440a-11e6-a65c-00e04c680037",
        display = "Admin",
        providerUuid = "f9badd80-ab76-11e2-9e96-0800200c9a66"
    )
    return authenticatedUser
}

