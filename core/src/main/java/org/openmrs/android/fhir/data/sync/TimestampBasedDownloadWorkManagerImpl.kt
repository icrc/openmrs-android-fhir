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
import com.google.android.fhir.sync.DownloadWorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

/**
 * What the sync downloads: locations, forms, and the patients of the selected patient lists with
 * their encounters and observations, in small pieces ([PatientDownloadWorkManager]).
 */
class TimestampBasedDownloadWorkManagerImpl
private constructor(private val manager: PatientDownloadWorkManager) :
  DownloadWorkManager by manager {

  constructor(dataStore: DemoDataStore, context: Context) : this(create(dataStore, context))

  private companion object {
    fun create(dataStore: DemoDataStore, context: Context): PatientDownloadWorkManager {
      val lists = runBlocking {
        context.applicationContext.dataStore.data
          .first()[PreferenceKeys.Companion.SELECTED_PATIENT_LISTS]
          .orEmpty()
      }
      return PatientDownloadWorkManager(
        searches = SyncUrls.periodic(context),
        idSearches = SyncUrls.patientIdSearches(lists),
        store = DemoSyncProgressStore(dataStore),
      )
    }
  }
}

private const val PATIENT_LIST_PLACEHOLDER = "_has:Group:member:id="

/** Resource types that hold data about patients; none of it may be downloaded without a list. */
private val PATIENT_DATA_TYPES =
  setOf(
    "Patient",
    "Encounter",
    "Observation",
    "Condition",
    "Procedure",
    "MedicationRequest",
    "MedicationStatement",
    "AllergyIntolerance",
    "Immunization",
    "DiagnosticReport",
    "RiskAssessment",
    "ServiceRequest",
    "Appointment",
    "EpisodeOfCare",
    "Flag",
    "Person",
  )

/**
 * Restricts patient requests to the selected patient lists (Groups).
 * - A request that already carries the `_has:Group:member:id=` placeholder has it filled in.
 * - A `Patient` request without it gets the filter added, so the scope does not depend on how the
 *   sync URLs were configured.
 * - When no list is selected, every patient-data request is skipped (`null`) instead of being sent
 *   unscoped, which would download the patients of the whole server.
 *
 * Requests for anything else (locations, questionnaires, patient lists) are returned unchanged.
 */
internal fun applyPatientListScope(url: String, selectedPatientLists: Set<String>?): String? {
  val resourceType = url.substringBefore("?").substringBefore("/")
  val hasPlaceholder = url.contains(PATIENT_LIST_PLACEHOLDER)
  if (!hasPlaceholder && resourceType !in PATIENT_DATA_TYPES) return url
  if (selectedPatientLists.isNullOrEmpty()) return null
  val ids = selectedPatientLists.joinToString(",")
  if (hasPlaceholder) {
    return url.replace(Regex("([?&])" + Regex.escape(PATIENT_LIST_PLACEHOLDER) + "(?=&|$)")) {
      it.groupValues[1] + PATIENT_LIST_PLACEHOLDER + ids
    }
  }
  if (resourceType != "Patient") return url
  val separator = if (url.contains("?")) "&" else "?"
  return url + separator + PATIENT_LIST_PLACEHOLDER + ids
}

/**
 * Affixes the last updated timestamp to the request URL.
 *
 * If the request URL includes the `$everything` parameter, the last updated timestamp will be
 * attached using the `_since` parameter. Otherwise, the last updated timestamp will be attached
 * using the `_lastUpdated` parameter.
 */
internal fun affixLastUpdatedTimestamp(url: String, lastUpdated: String): String {
  var downloadUrl = url

  // Affix lastUpdate to a $everything query using _since as per:
  // https://hl7.org/fhir/operation-patient-everything.html
  if (downloadUrl.contains("\$everything")) {
    downloadUrl = "$downloadUrl?_since=$lastUpdated"
  }

  // Affix lastUpdate to non-$everything queries as per:
  // https://hl7.org/fhir/operation-patient-everything.html
  if (!downloadUrl.contains("\$everything")) {
    val lastUpdatedRegex = Regex("([?&])_lastUpdated=gt[^&]*")

    if (downloadUrl.contains("_lastUpdated=")) {
      // Replace existing _lastUpdated value
      downloadUrl = downloadUrl.replace(lastUpdatedRegex, "$1_lastUpdated=gt$lastUpdated")
    } else {
      // Append _lastUpdated normally
      val separator = if (downloadUrl.contains('?')) "&" else "?"
      downloadUrl = "$downloadUrl${separator}_lastUpdated=gt$lastUpdated"
    }
  }

  // Do not modify any URL set by a server that specifies the token of the page to return.
  if (downloadUrl.contains("&page_token")) {
    downloadUrl = url
  }

  return downloadUrl
}

internal fun removeSummaryParameter(url: String): String {
  return url.replace(Regex("&_summary=[^&]*"), "")
}
