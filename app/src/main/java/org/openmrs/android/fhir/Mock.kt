package org.openmrs.android.fhir
import org.hl7.fhir.r4.model.Reference

data class Form(
    val display: String,
    val code: String,
    val resource: String
) {
    constructor(display: String, code: String) : this(display, code, "")
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
)



object MockConstants {
    val LOCATION = createMockLocationReference()
    val AUTHENTICATED_USER = getAuthenticatedUser()
    val MOCK_FORMS = listOf(
        Form(
            display = "Assessment Form",
            code = "0c63150d-ff39-42e1-9048-834mh76p2s72",
            resource = "assessment.json"
        ),
        Form(
            display = "Followup Form",
            code = "07a7dd1c-7280-483a-a3bc-01be995293ac",
            resource = "assessment.json"
        ),
        Form(
            display = "Closure Form",
            code = "95458795-3o06-4l59-9508-c217aa21ea26",
            resource = "assessment.json"
        )
    )
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
        display = "Admin"
    )
    return authenticatedUser
}

