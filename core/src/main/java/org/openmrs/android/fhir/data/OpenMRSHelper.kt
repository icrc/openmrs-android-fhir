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
package org.openmrs.android.helpers

import android.content.Context
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import java.util.Date
import java.util.LinkedList
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Reference
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

class OpenMRSHelper
@Inject
constructor(
  private val fhirEngine: FhirEngine,
  private val context: Context,
) {

  suspend fun getAuthenticatedUserId(): String? {
    return context.applicationContext.dataStore.data.first().get(PreferenceKeys.USER_UUID)
  }

  suspend fun getAuthenticatedUserName(): String? {
    return context.applicationContext.dataStore.data.first().get(PreferenceKeys.USER_NAME)
  }

  suspend fun getAuthenticatedProviderUuid(): String? {
    return context.applicationContext.dataStore.data.first().get(PreferenceKeys.USER_PROVIDER_UUID)
  }

  suspend fun getCurrentAuthLocation(): Reference {
    val selectedLocationId =
      context.applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
    return Reference("Location/$selectedLocationId") // Reference to the location
  }

  // Visit related functions
  suspend fun getVisits(
    patientId: String,
  ): Map<Encounter, List<Encounter>> {
    val visits: MutableMap<Encounter, List<Encounter>> = HashMap()

    val allEncounters = LinkedList<Encounter>()

    fhirEngine
      .search<Encounter> { filter(Encounter.SUBJECT, { value = "Patient/$patientId" }) }
      .map { it.resource }
      .let { allEncounters.addAll(it) }

    val visitEncounters =
      allEncounters.filter { encounter ->
        encounter.type.any { type ->
          type.coding.any { coding ->
            coding.system == "http://fhir.openmrs.org/code-system/visit-type"
          }
        }
      }

    visitEncounters.forEach { visit ->
      visits[visit] =
        allEncounters.filter { it.partOf?.reference.equals("Encounter/" + visit.idPart) }
    }

    return visits
  }

  suspend fun getActiveVisit(
    patientId: String,
    shouldCreateVisit: Boolean,
  ): Encounter? {
    val searchResult =
      fhirEngine.search<Encounter> { filter(Encounter.SUBJECT, { value = "Patient/$patientId" }) }
    val encounters = searchResult.map { it.resource }

    // Find and return the active encounter
    var currentVisit =
      encounters.find { encounter: Encounter ->
        val isVisitType =
          encounter.type?.firstOrNull()?.coding?.firstOrNull()?.system ==
            "http://fhir.openmrs.org/code-system/visit-type"
        val period = encounter.period
        val startDate = period?.start
        val endDate = period?.end

        isVisitType && startDate != null && endDate == null && startDate.before(Date())
      }

    if (currentVisit == null && shouldCreateVisit) {
      currentVisit =
        startVisit(
          patientId,
          "default-visit-type-uuid",
          Date(),
        ) // Replace with actual visit type UUID
    }
    return currentVisit
  }

  suspend fun startVisit(
    patientId: String,
    visitTypeId: String,
    startDate: Date,
  ): Encounter {
    val encounter =
      Encounter().apply {
        subject = Reference("Patient/$patientId")
        status = Encounter.EncounterStatus.INPROGRESS
        setPeriod(Period().apply { start = startDate })
        addParticipant(createParticipant())
        addLocation(
          Encounter.EncounterLocationComponent().apply {
            location = getCurrentAuthLocation() // Dynamic location
          },
        )
        addType(
          CodeableConcept().apply {
            coding =
              listOf(
                Coding().apply {
                  system = "http://fhir.openmrs.org/code-system/visit-type"
                  code = visitTypeId
                  display = "Facility Visit"
                },
              )
          },
        )
      }
    fhirEngine.create(encounter)
    return encounter
  }

  suspend fun endVisit(visitId: String): Encounter {
    val encounter: Encounter = fhirEngine.get(visitId)
    val currentDate = Date()
    if (encounter.hasPeriod()) {
      encounter.period.end = Date()
    } else {
      encounter.period = Period().apply { end = currentDate }
    }
    fhirEngine.update(encounter)
    return encounter
  }

  suspend fun createParticipant(): EncounterParticipantComponent {
    val participant = EncounterParticipantComponent()
    participant.individual = Reference("Practitioner/${getAuthenticatedProviderUuid()}")
    participant.individual.display = getAuthenticatedUserName()
    participant.individual.type = "Practitioner"
    return participant
  }
}
