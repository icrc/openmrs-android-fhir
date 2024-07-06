package org.openmrs.android.fhir.data

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

class PreferenceKeys {
    companion object {
        val LOCATION_ID = stringPreferencesKey("LOCATION_ID")
        val LOCATION_NAME = stringPreferencesKey("LOCATION_NAME")
        val IDENTIFIER_ID = stringPreferencesKey("IDENTIFIER_ID")
        val IDENTIFIER_NAME = stringPreferencesKey("IDENTIFIER_NAME")
        val FAVORITE_LOCATIONS = stringSetPreferencesKey("FAVORITE_LOCATIONS")
        const val IDENTIFIERS = "IDENTIFIERS"
        const val PATIENT_IDENTIFIERS = "PATIENT_IDENTIFIERS"
    }
}