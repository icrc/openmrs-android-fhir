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
package org.openmrs.android.fhir.patientlists

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import com.google.android.fhir.get
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.upload.SuccessfulUploadResponseMapping
import com.google.android.fhir.sync.upload.UploadRequestResult
import org.hl7.fhir.instance.model.api.IBase
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.data.database.PatientListsDao
import org.openmrs.android.fhir.data.database.model.PatientListMemberChange
import org.openmrs.android.fhir.viewmodel.applyPatientTextQuery

/**
 * Where a patient registered on this device is after the upload. The server gives a new patient its
 * own id and the device's copy takes it, so what was added to a patient list for the id the patient
 * was created with has to follow.
 */
sealed interface Uploaded {
  data object Waiting : Uploaded

  data class Now(val patient: PatientSummary) : Uploaded

  data object Missing : Uploaded

  data object Ambiguous : Uploaded
}

fun interface UploadedPatients {
  suspend fun find(change: PatientListMemberChange): Uploaded
}

class UploadedPatientFinder(private val fhirEngine: FhirEngine, private val dao: PatientListsDao) :
  UploadedPatients {
  override suspend fun find(change: PatientListMemberChange): Uploaded {
    val local = runCatching { fhirEngine.get<Patient>(change.patientId) }.getOrNull()
    if (local != null) {
      return if (neverUploaded(change.patientId)) {
        Uploaded.Waiting
      } else {
        Uploaded.Now(GroupMapper.summary(local))
      }
    }
    val noted = dao.uploadedId(change.patientId)?.serverId
    if (noted != null) {
      val patient = runCatching { fhirEngine.get<Patient>(noted) }.getOrNull()
      return Uploaded.Now(
        patient?.let(GroupMapper::summary)
          ?: PatientSummary(noted, change.name, change.gender, change.birthDate, change.identifier),
      )
    }
    val alike =
      fhirEngine
        .search<Patient> { applyPatientTextQuery(change.name) }
        .map { it.resource }
        .filter { it.idElement.idPart != change.patientId && sameEntry(it, change) }
    return choose(alike)
  }

  internal companion object {
    fun choose(alike: List<Patient>): Uploaded =
      when (alike.size) {
        0 -> Uploaded.Missing
        1 -> Uploaded.Now(GroupMapper.summary(alike.single()))
        else -> Uploaded.Ambiguous
      }
  }

  private suspend fun neverUploaded(patientId: String): Boolean =
    fhirEngine.getLocalChanges(ResourceType.Patient, patientId).any {
      it.type == LocalChange.Type.INSERT
    }

  private fun sameEntry(patient: Patient, change: PatientListMemberChange): Boolean =
    patient.gender?.toCode() == change.gender &&
      patient.birthDateElement.valueAsString == change.birthDate
}

/**
 * The id the server gave each patient created on the device, read from the answers of an upload:
 * the exact way to follow a patient whose id changes, rather than looking for them by what was
 * entered.
 */
object UploadedIds {
  fun pairs(result: UploadRequestResult): List<Pair<String, String>> =
    (result as? UploadRequestResult.Success)
      ?.successfulUploadResponseMappings
      ?.flatMap(::pairs)
      .orEmpty()

  internal fun pairs(mapping: SuccessfulUploadResponseMapping): List<Pair<String, String>> =
    pairs(mapping.localChanges, mapping.output)

  fun pairs(changes: List<LocalChange>, output: IBase): List<Pair<String, String>> {
    val created =
      changes.firstOrNull {
        it.resourceType == ResourceType.Patient.name && it.type == LocalChange.Type.INSERT
      }
        ?: return emptyList()
    val serverId =
      when (output) {
        is Resource -> output.idElement?.idPart
        is Bundle.BundleEntryResponseComponent ->
          output.location?.split("/")?.takeIf { it.size > 3 }?.let { it[it.size - 3] }
        else -> null
      }
    return if (serverId.isNullOrEmpty() || serverId == created.resourceId) {
      emptyList()
    } else {
      listOf(created.resourceId to serverId)
    }
  }
}
