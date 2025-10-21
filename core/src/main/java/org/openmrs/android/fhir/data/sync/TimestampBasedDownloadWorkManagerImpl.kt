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
import com.google.android.fhir.sync.SyncDataParams
import com.google.android.fhir.sync.download.DownloadRequest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

class TimestampBasedDownloadWorkManagerImpl(
  private val dataStore: DemoDataStore,
  private val context: Context,
) : DownloadWorkManager {
  private val resourceTypeList = ResourceType.entries.map { it.name }
  private val urls = LinkedList(loadUrlsFromProperties())

  private fun loadUrlsFromProperties(): List<String> {
    val syncUrls = context.getString(R.string.fhir_sync_urls).split(',')
    val firstTimeUrls = context.getString(R.string.first_fhir_sync_url).split(',')
    val cohortListType =
      context.getString(R.string.cohort_list_type).trim().takeIf { it.isNotEmpty() }

    val shouldFilterGroupsByLocation =
      context.resources.getBoolean(R.bool.filter_patient_lists_by_group)
    val selectedLocationId =
      if (shouldFilterGroupsByLocation) {
        runBlocking {
          context.applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
        }
      } else {
        null
      }

    val urlList =
      (firstTimeUrls + syncUrls).distinctBy { url ->
        resourceTypeList.find { resourceType -> url.startsWith(resourceType, ignoreCase = true) }
      }

    return urlList.map { url ->
      var updatedUrl = url

      if (
        shouldFilterGroupsByLocation &&
          !selectedLocationId.isNullOrBlank() &&
          updatedUrl.substringBefore("?").equals(ResourceType.Group.name, ignoreCase = true) &&
          !updatedUrl.contains("location=")
      ) {
        val separator = if (updatedUrl.contains("?")) "&" else "?"
        updatedUrl = "$updatedUrl${separator}location=$selectedLocationId"
      }

      if (
        cohortListType != null &&
          updatedUrl.substringBefore("?").equals(ResourceType.Group.name, ignoreCase = true) &&
          !updatedUrl.contains("list-type=")
      ) {
        val separator = if (updatedUrl.contains("?")) "&" else "?"
        updatedUrl = "$updatedUrl${separator}list-type=$cohortListType"
      }

      if (updatedUrl.contains("_has:Group:member:id=")) {
        val selectedPatientLists = runBlocking {
          context.applicationContext.dataStore.data
            .first()[PreferenceKeys.Companion.SELECTED_PATIENT_LISTS]
        }

        if (selectedPatientLists.isNullOrEmpty()) {
          updatedUrl.replace("_has:Group:member:id=&", "")
        } else {
          updatedUrl.replace(
            "?_has:Group:member:id=",
            "?_has:Group:member:id=" + selectedPatientLists.joinToString(","),
          )
        }
      } else {
        updatedUrl
      }
    }
  }

  override suspend fun getNextRequest(): DownloadRequest? {
    var url = urls.poll() ?: return null

    val resourceTypeToDownload =
      ResourceType.fromCode(url.findAnyOf(resourceTypeList, ignoreCase = true)!!.second)
    dataStore.getLasUpdateTimestamp(resourceTypeToDownload)?.let {
      url = affixLastUpdatedTimestamp(url, it)
    }
    return DownloadRequest.Companion.of(url)
  }

  override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> {
    return urls.associate { url ->
      val resourceType = ResourceType.fromCode(url.substringBefore("?"))
      val lastUpdated = dataStore.getLasUpdateTimestamp(resourceType)

      var baseUrl =
        if (lastUpdated.isNullOrEmpty()) {
          url
        } else {
          affixLastUpdatedTimestamp(url, lastUpdated)
        }

      baseUrl = removeSummaryParameter(baseUrl)

      val summaryUrl =
        baseUrl + "&${SyncDataParams.SUMMARY_KEY}=${SyncDataParams.SUMMARY_COUNT_VALUE}"

      resourceType to summaryUrl
    }
  }

  override suspend fun processResponse(response: Resource): Collection<Resource> {
    // As per FHIR documentation :
    // If the search fails (cannot be executed, not that there are no matches), the
    // return value SHALL be a status code 4xx or 5xx with an OperationOutcome.
    // See https://www.hl7.org/fhir/http.html#search for more details.
    if (response is OperationOutcome) {
      throw FHIRException(response.issueFirstRep.diagnostics)
    }

    // If the resource returned is a List containing Patients, extract Patient references and fetch
    // all resources related to the patient using the $everything operation.
    if (response is ListResource) {
      for (entry in response.entry) {
        val reference = Reference(entry.item.reference)
        if (reference.referenceElement.resourceType == "Patient") {
          val patientUrl = "${entry.item.reference}/\$everything"
          urls.add(patientUrl)
        }
      }
    }

    // If the resource returned is a Bundle, check to see if there is a "next" relation referenced
    // in the Bundle.link component, if so, append the URL referenced to list of URLs to download.
    if (response is Bundle) {
      val nextUrl = response.link.firstOrNull { component -> component.relation == "next" }?.url
      if (nextUrl != null) {
        urls.add(nextUrl)
      }
    }

    // Finally, extract the downloaded resources from the bundle.
    var bundleCollection: Collection<Resource> = mutableListOf()
    if (response is Bundle && response.type == Bundle.BundleType.SEARCHSET) {
      bundleCollection =
        response.entry
          .map { it.resource }
          .also { extractAndSaveLastUpdateTimestampToFetchFutureUpdates(it) }
    }
    return bundleCollection
  }

  private suspend fun extractAndSaveLastUpdateTimestampToFetchFutureUpdates(
    resources: List<Resource>,
  ) {
    resources
      .groupBy { it.resourceType }
      .entries
      .map { map ->
        dataStore.saveLastUpdatedTimestamp(
          map.key,
          map.value.maxOfOrNull { it.meta.lastUpdated }?.toTimeZoneString() ?: "",
        )
      }
  }
}

/**
 * Affixes the last updated timestamp to the request URL.
 *
 * If the request URL includes the `$everything` parameter, the last updated timestamp will be
 * attached using the `_since` parameter. Otherwise, the last updated timestamp will be attached
 * using the `_lastUpdated` parameter.
 */
private fun affixLastUpdatedTimestamp(url: String, lastUpdated: String): String {
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

private fun Date.toTimeZoneString(): String {
  val simpleDateFormat =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
      .withZone(ZoneId.systemDefault())
  return simpleDateFormat.format(this.toInstant())
}

private fun removeSummaryParameter(url: String): String {
  return url.replace(Regex("&_summary=[^&]*"), "")
}
