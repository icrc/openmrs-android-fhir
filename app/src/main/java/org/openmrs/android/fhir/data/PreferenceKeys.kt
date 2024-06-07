package org.openmrs.android.fhir.data

import androidx.datastore.preferences.core.stringPreferencesKey

class PreferenceKeys {
    companion object {
        val LOCATION_ID = stringPreferencesKey("LOCATION_ID")
        val LOCATION_NAME = stringPreferencesKey("LOCATION_NAME")
    }
}