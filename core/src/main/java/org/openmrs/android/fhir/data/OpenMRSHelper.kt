package org.openmrs.android.helpers

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.get
import com.google.android.fhir.search.revInclude
import com.google.android.fhir.search.search
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.openmrs.android.fhir.MockConstants
import org.openmrs.android.fhir.User
import org.openmrs.android.helpers.OpenMRSHelper.MiscHelper.createParticipant
import java.util.Date
import java.util.LinkedList


class OpenMRSHelper {

    // User related functions
    object UserHelper {
        fun getAuthenticatedUser(): User {
            return MockConstants.AUTHENTICATED_USER
        }

        fun getCurrentAuthLocation(): Reference {
            return MockConstants.LOCATION
        }
    }

    // Visit related functions
    object VisitHelper {

        suspend fun getVisits(
            fhirEngine: FhirEngine,
            patientId: String
        ): Map<Encounter, List<Encounter>> {
            val visits: MutableMap<Encounter, List<Encounter>> = HashMap()

            val patientResult = fhirEngine.search<Patient> {
                filter(Resource.RES_ID, { value = of(patientId) })
                revInclude<Encounter>(Encounter.SUBJECT)
            }

            val allEncounters = LinkedList<Encounter>();

            fhirEngine.search<Encounter> {
                filter(Encounter.SUBJECT, { value = "Patient/$patientId" })
            }.map { it.resource }.let { allEncounters.addAll(it) }

            val visitEncounters = allEncounters.filter { encounter ->
                encounter.type.any { type ->
                    type.coding.any { coding ->
                        coding.system == "http://fhir.openmrs.org/code-system/visit-type"
                    }
                }
            }

            visitEncounters.forEach { visit ->
                visits[visit] = allEncounters.filter { it.partOf?.reference.equals("Encounter/" + visit.idPart) }
            }

            return visits
        }


        suspend fun getActiveVisit(fhirEngine: FhirEngine, patientId: String, shouldCreateVisit: Boolean): Encounter? {
            val searchResult = fhirEngine.search<Encounter> {
                filter(Encounter.SUBJECT, { value = "Patient/$patientId" })
            }
            val encounters = searchResult.map { it.resource }

            // Find and return the active encounter
            var currentVisit = encounters.find { encounter: Encounter ->
                val isVisitType =
                    encounter.type?.firstOrNull()?.coding?.firstOrNull()?.system == "http://fhir.openmrs.org/code-system/visit-type"
                val period = encounter.period
                val startDate = period?.start
                val endDate = period?.end

                isVisitType && startDate != null && endDate == null && startDate.before(Date())

            }

            if (currentVisit == null && shouldCreateVisit) {
                currentVisit = startVisit(fhirEngine, patientId, MockConstants.VISIT_TYPE_UUID, Date());
            }
            return currentVisit;
        }

        suspend fun startVisit(
            fhirEngine: FhirEngine,
            patientId: String,
            visitTypeId: String,
            startDate: Date
        ): Encounter {
            val encounter = Encounter().apply {
                subject = Reference("Patient/$patientId")
                status = Encounter.EncounterStatus.INPROGRESS
                setPeriod(Period().apply { start = startDate })
                addParticipant(createParticipant())
                addLocation(Encounter.EncounterLocationComponent().apply {
                    location = Reference("Location/${UserHelper.getCurrentAuthLocation().id}")
                })
                addType(CodeableConcept().apply {
                    coding = listOf(
                        Coding().apply {
                            system = "http://fhir.openmrs.org/code-system/visit-type"
                            code = visitTypeId
                            display = "Facility Visit"
                        }
                    )
                })

            }
            fhirEngine.create(encounter)
            return encounter
        }

        suspend fun endVisit(fhirEngine: FhirEngine, visitId: String): Encounter {
            val encounter: Encounter = fhirEngine.get(visitId);
            val currentDate = Date()
            if (encounter.hasPeriod()) {
                encounter.period.end = Date()
            } else {
                encounter.period = Period().apply {
                    end = currentDate
                }
            }
            fhirEngine.update(encounter);
            return encounter;
        }
    }


    object MiscHelper {
        fun createParticipant(): EncounterParticipantComponent {
            //TODO Replace this with method to get the authenticated user's reference
            val authenticatedUser = OpenMRSHelper.UserHelper.getAuthenticatedUser()
            val participant = EncounterParticipantComponent()
            participant.individual = Reference("Practitioner/${authenticatedUser.uuid}")
            participant.individual.display = authenticatedUser.display
            participant.individual.type = "Practitioner"
            return participant
        }
    }
}
