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
package org.openmrs.android.fhir.data

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

class PreferenceKeys {
  companion object {
    val LOCATION_ID = stringPreferencesKey("LOCATION_ID")
    val SELECTED_PATIENT_LISTS = stringSetPreferencesKey("SELECTED_PATIENT_LISTS")
    val LOCATION_NAME = stringPreferencesKey("LOCATION_NAME")
    val FAVORITE_LOCATIONS = stringSetPreferencesKey("FAVORITE_LOCATIONS")
    val SELECTED_IDENTIFIER_TYPES = stringSetPreferencesKey("SELECTED_IDENTIFIER_TYPES")
    val PREF_COOKIES = stringSetPreferencesKey("PREF_COOKIES")
    val USER_UUID = stringPreferencesKey("USER_UUID")
    val USER_NAME = stringPreferencesKey("USER_NAME")
    val USER_PROVIDER_UUID = stringPreferencesKey("USER_PROVIDER_UUID")
    val USER_PROVIDER_NAME = stringPreferencesKey("USER_PROVIDER_NAME")
  }
}
