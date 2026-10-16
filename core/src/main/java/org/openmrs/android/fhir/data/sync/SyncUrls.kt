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
package org.openmrs.android.fhir.data.sync

import android.content.Context
import org.openmrs.android.fhir.config.AppConfig

/**
 * The FHIR searches the app downloads. They are the same in every deployment, so only the few
 * values that differ (location tag, cohort type, ...) come from [AppConfig].
 */
object SyncUrls {
  fun location(context: Context): String =
    "Location?_sort=_lastUpdated&_summary=data&_tag=" +
      AppConfig.get(context).sync.locationTag.trim().replace(' ', '+')

  fun questionnaires(): String = "Questionnaire?_sort=_lastUpdated"

  /** The patients of the selected patient lists; the lists are filled in when it is requested. */
  fun patients(context: Context): String {
    val since = AppConfig.get(context).sync.patientsSince.trim()
    return "Patient?_has:Group:member:id=&_revinclude=Encounter:patient" +
      "&_revinclude=Observation:patient&_sort=_lastUpdated" +
      if (since.isEmpty()) "" else "&_lastUpdated=gt$since"
  }

  /** The patient lists, of [locationId] when the app is set to scope them to the location. */
  fun groups(context: Context, locationId: String?): String {
    val sync = AppConfig.get(context).sync
    val query = mutableListOf<String>()
    if (sync.filterPatientListsByLocation && !locationId.isNullOrBlank()) {
      query += "location=$locationId"
    }
    sync.cohortType.trim().takeIf { it.isNotEmpty() }?.let { query += "cohort-type=$it" }
    return if (query.isEmpty()) "Group" else "Group?" + query.joinToString("&")
  }

  /** What the very first download fetches, before any patient list is chosen. */
  fun firstSync(context: Context): List<String> = listOf(location(context), questionnaires())

  /** What every later sync fetches. */
  fun periodic(context: Context): List<String> =
    listOf(location(context), questionnaires(), patients(context))
}
