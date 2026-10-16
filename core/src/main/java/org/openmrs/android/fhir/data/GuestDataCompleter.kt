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
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import kotlinx.coroutines.flow.first
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.Constants
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.database.AppDatabase
import timber.log.Timber

/**
 * Finishes the data entered while working as a guest ("Continue without signing in").
 *
 * With no session there was no location and no provider, so the resources were saved without the
 * references the server insists on:
 * - patients have no identifiers (each one is tied to a location);
 * - encounters (visits and clinical encounters) have an empty or `Location/null` location and a
 *   `Practitioner/null` participant.
 *
 * Once the user has signed in and picked a location these are filled in, so the next sync uploads
 * them. Only resources still waiting for upload are touched, and nothing is removed.
 */
class GuestDataCompleter(
  private val context: Context,
  private val fhirEngine: FhirEngine,
  private val database: AppDatabase,
) {
  enum class Result {
    /** Everything waiting for a location, identifiers or provider has been completed. */
    Completed,

    /** The user has not chosen a location yet; nothing was changed. */
    NoLocation,

    /**
     * Patients are waiting for identifiers but no identifier type is selected, so uploading them
     * would be refused by the server ("select a preferred identifier"). Nothing was changed.
     */
    NeedsIdentifierTypes,
  }

  suspend fun complete(fetchIdentifiers: Boolean): Result {
    val preferences = context.dataStore.data.first()
    val locationId = preferences[PreferenceKeys.LOCATION_ID]?.takeIf { it.isNotBlank() }
    val location =
      locationId?.let { runCatching { fhirEngine.get(ResourceType.Location, it) }.getOrNull() }
        as? Location
        ?: return Result.NoLocation

    val patientsStillWithoutIdentifiers = completePatients(location, fetchIdentifiers)
    completeEncounters(locationId)
    return if (patientsStillWithoutIdentifiers == 0) {
      Result.Completed
    } else {
      Result.NeedsIdentifierTypes
    }
  }

  /** @return how many waiting patients are still without identifiers. */
  private suspend fun completePatients(location: Location, fetchIdentifiers: Boolean): Int {
    val factory = PatientIdentifierFactory(context, database)
    var withoutIdentifiers = 0
    fhirEngine
      .search<Patient> {}
      .map { it.resource }
      .filter { it.identifier.isEmpty() }
      .filter { isWaitingForUpload(ResourceType.Patient, it.idElement.idPart) }
      .forEach { patient ->
        val identifiers = factory.locationIdentifiers(location, fetchIdentifiers).toMutableList()
        if (identifiers.isEmpty()) {
          // No identifier type is selected yet: uploading now would be refused by the server
          // ("select a preferred identifier"), so the patient is left for the next sync.
          withoutIdentifiers++
          return@forEach
        }
        if (fetchIdentifiers) identifiers.add(0, factory.unsyncedIdentifier(location))
        // The server needs one preferred (official) identifier.
        if (identifiers.none { it.use == Identifier.IdentifierUse.OFFICIAL }) {
          identifiers.first().use = Identifier.IdentifierUse.OFFICIAL
        }
        patient.identifier = identifiers
        fhirEngine.update(patient)
        Timber.i(
          "Guest patient %s received %d identifiers",
          patient.idElement.idPart,
          identifiers.size,
        )
      }
    return withoutIdentifiers
  }

  private suspend fun completeEncounters(locationId: String) {
    val helper = OpenMRSHelper(fhirEngine, context)
    fhirEngine
      .search<Encounter> {}
      .map { it.resource }
      .filter { it.needsLocation() || it.needsParticipant() }
      .filter { isWaitingForUpload(ResourceType.Encounter, it.idElement.idPart) }
      .forEach { encounter ->
        var changed = false

        if (encounter.needsLocation()) {
          if (encounter.location.isEmpty()) {
            encounter.addLocation(Encounter.EncounterLocationComponent())
          }
          encounter.location
            .filter { isMissing(it.location?.reference) }
            .forEach {
              it.location = Reference("Location/$locationId")
              changed = true
            }
        }

        if (encounter.needsParticipant()) {
          val isVisit =
            encounter.type.any { type ->
              type.coding.any { it.system == Constants.VISIT_TYPE_CODE_SYSTEM }
            }
          val replacement =
            if (isVisit) helper.createVisitParticipant() else helper.createEncounterParticipant()
          if (!isMissing(replacement.individual?.reference)) {
            encounter.participant
              .filter { isMissing(it.individual?.reference) }
              .forEach {
                it.individual = replacement.individual
                changed = true
              }
          }
        }

        if (changed) {
          fhirEngine.update(encounter)
          Timber.i("Guest encounter %s completed", encounter.idElement.idPart)
        }
      }
  }

  private suspend fun isWaitingForUpload(type: ResourceType, id: String): Boolean =
    fhirEngine.getLocalChanges(type, id).isNotEmpty()

  private fun Encounter.needsLocation(): Boolean =
    location.isEmpty() || location.any { isMissing(it.location?.reference) }

  private fun Encounter.needsParticipant(): Boolean =
    participant.any { isMissing(it.individual?.reference) }

  /** A reference built from a value that did not exist yet, such as `Location/null`. */
  private fun isMissing(reference: String?): Boolean =
    reference.isNullOrBlank() || reference.endsWith("/null") || reference.endsWith("/")
}
