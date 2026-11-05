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
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.forms.useEmbeddedFormsOnly

/**
 * The FHIR searches the app downloads. They are the same in every deployment, so only the few
 * values that differ (location tag, cohort type, ...) come from [AppConfig].
 */
object SyncUrls {
  fun location(context: Context): String =
    "Location?_sort=_lastUpdated&_summary=data&_tag=" +
      AppConfig.get(context).sync.locationTag.trim().replace(' ', '+')

  /** The forms of the server; `null` while the app uses only the forms shipped with it. */
  fun questionnaires(context: Context): String? =
    if (context.useEmbeddedFormsOnly()) null else "Questionnaire?_sort=_lastUpdated"

  /** Patients per page of the search for their ids (nothing but the id comes back). */
  const val PATIENT_IDS_PAGE_SIZE = 200

  /**
   * Patients whose data is asked for together, and resources per page of that. The server cannot
   * page what a search includes, so asking for the patients with their history made one response of
   * the whole history of each (on a real server a page of ten patients was almost 2 MB and took 25
   * s to build, and a phone cut it); with these, no response has more than [HISTORY_PAGE_SIZE]
   * resources whatever the history, and a batch is small enough to be done between two cuts.
   */
  const val HISTORY_BATCH_SIZE = 10
  const val HISTORY_PAGE_SIZE = 50

  /** Patients per batch when only what changed is asked for: there is little to fetch. */
  const val KNOWN_BATCH_SIZE = 40

  /**
   * Lists in one search. Selecting every list of a server (150 on one) made a URL of 5.6 KB, which
   * a proxy or a server may refuse; the searches are split in groups of this many.
   */
  const val LISTS_PER_SEARCH = 40

  internal const val PATIENT_IDS_SEARCH =
    "Patient?_has:Group:member:id=&_elements=id&_count=$PATIENT_IDS_PAGE_SIZE"

  /** The searches for the ids of the patients of [lists], [LISTS_PER_SEARCH] lists in each. */
  fun patientIdSearches(lists: Collection<String>): List<String> =
    lists.sorted().chunked(LISTS_PER_SEARCH).mapNotNull {
      applyPatientListScope(PATIENT_IDS_SEARCH, it.toSet())
    }

  /**
   * The patients, their encounters or their observations, for [patientIds], a page of
   * [HISTORY_PAGE_SIZE], oldest change first. With [since], only what changed after it
   * ([inclusive]: at or after it, to go on from the last resource stored without missing those
   * changed in the same second).
   */
  fun history(
    type: ResourceType,
    patientIds: List<String>,
    since: String? = null,
    inclusive: Boolean = false,
  ): String {
    val by = if (type == ResourceType.Patient) "_id" else "patient"
    return "${type.name}?$by=${patientIds.joinToString(",")}&_count=$HISTORY_PAGE_SIZE" +
      "&_sort=_lastUpdated" +
      (since?.let { "&_lastUpdated=${if (inclusive) "ge" else "gt"}$it" } ?: "")
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
  fun firstSync(context: Context): List<String> =
    listOfNotNull(location(context), questionnaires(context))

  /** What every later sync fetches besides the patients, which the lists chosen decide. */
  fun periodic(context: Context): List<String> =
    listOfNotNull(location(context), questionnaires(context))
}
