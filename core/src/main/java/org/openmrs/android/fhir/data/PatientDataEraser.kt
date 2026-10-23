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

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.filter.ReferenceParamFilterCriterion
import com.google.android.fhir.search.search
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.RiskAssessment
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.multiuser.UserDataLedger
import org.openmrs.android.fhir.unidentified.UnidentifiedPatients

/**
 * "Delete patient data": what the signed-in user has on the device (the patients they can see, with
 * their encounters, observations and the rest) is removed so that a new flow can start, without
 * signing out.
 *
 * Gone: those patients and everything recorded for them (including what was not uploaded yet), the
 * patients to identify, the group-session drafts, the choice of patient lists (and the download
 * marks of patients, so the next download starts from the beginning). Kept: the signed-in session,
 * the location, the forms, the locations and the patient lists themselves, the settings.
 *
 * On a device shared by several users only the patients the signed-in user can see are removed.
 */
class PatientDataEraser(
  private val context: Context,
  private val fhirEngine: FhirEngine,
  private val database: AppDatabase,
  private val ledger: UserDataLedger,
  private val unidentified: UnidentifiedPatients,
) {

  /** What would go. */
  data class Preview(
    val patients: Int,
    /** Records among them that were changed on the device and not uploaded yet. */
    val notUploaded: Int,
  )

  suspend fun preview(): Preview {
    val scope = scope()
    val notUploaded =
      scope.entries.sumOf { (type, ids) ->
        ids.count { fhirEngine.getLocalChanges(type, it).isNotEmpty() }
      }
    return Preview(patients = scope[ResourceType.Patient].orEmpty().size, notUploaded = notUploaded)
  }

  /** @return how many patients were removed. */
  suspend fun erase(): Int {
    val scope = scope()
    // The things that point at others first.
    ORDER.forEach { type ->
      scope[type].orEmpty().chunked(CHUNK).forEach { ids ->
        fhirEngine.purge(type, ids.toSet(), forcePurge = true)
      }
    }
    val patients = scope[ResourceType.Patient].orEmpty()
    ledger.forget(patients)
    unidentified.removeAll()
    database.dao().clearGroupSessionDrafts()
    context.applicationContext.dataStore.edit { preferences ->
      preferences.remove(PreferenceKeys.SELECTED_PATIENT_LISTS)
      preferences.remove(PreferenceKeys.PENDING_LIST_DOWNLOADS)
      preferences.remove(PreferenceKeys.LIVE_PATIENT_IDS)
      preferences.remove(PreferenceKeys.LIVE_TEMPORARY_IDS)
    }
    DemoDataStore(context.applicationContext).clearLastUpdatedTimestamps(*ORDER.toTypedArray())
    return patients.size
  }

  /** The ids to remove, by type: the visible patients, and what points at them. */
  private suspend fun scope(): Map<ResourceType, Set<String>> {
    val all = fhirEngine.search<Patient> {}.map { it.resource.idElement.idPart }
    val hidden = ledger.hiddenAmong(all)
    val patients = all.filter { it !in hidden }
    if (patients.isEmpty()) return emptyMap()
    val result = linkedMapOf<ResourceType, Set<String>>()
    result[ResourceType.Observation] =
      idsAbout(patients) { subjects ->
        fhirEngine
          .search<Observation> { filter(Observation.SUBJECT, *subjects, operation = Operation.OR) }
          .map { it.resource.idElement.idPart }
      }
    result[ResourceType.Condition] =
      idsAbout(patients) { subjects ->
        fhirEngine
          .search<Condition> { filter(Condition.SUBJECT, *subjects, operation = Operation.OR) }
          .map { it.resource.idElement.idPart }
      }
    result[ResourceType.RiskAssessment] =
      idsAbout(patients) { subjects ->
        fhirEngine
          .search<RiskAssessment> {
            filter(RiskAssessment.SUBJECT, *subjects, operation = Operation.OR)
          }
          .map { it.resource.idElement.idPart }
      }
    result[ResourceType.Encounter] =
      idsAbout(patients) { subjects ->
        fhirEngine
          .search<Encounter> { filter(Encounter.SUBJECT, *subjects, operation = Operation.OR) }
          .map { it.resource.idElement.idPart }
      }
    result[ResourceType.Patient] = patients.toSet()
    return result
  }

  private suspend fun idsAbout(
    patients: List<String>,
    search: suspend (Array<ReferenceParamFilterCriterion.() -> Unit>) -> List<String>,
  ): Set<String> {
    val found = mutableSetOf<String>()
    patients.chunked(SEARCH_CHUNK).forEach { chunk ->
      val subjects =
        chunk
          .map<String, ReferenceParamFilterCriterion.() -> Unit> { id -> { value = "Patient/$id" } }
          .toTypedArray()
      found += search(subjects)
    }
    return found
  }

  private companion object {
    const val CHUNK = 200
    const val SEARCH_CHUNK = 50
    val ORDER =
      listOf(
        ResourceType.Observation,
        ResourceType.Condition,
        ResourceType.RiskAssessment,
        ResourceType.Encounter,
        ResourceType.Patient,
      )
  }
}
