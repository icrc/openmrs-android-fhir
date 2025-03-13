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
package org.openmrs.android.fhir.data.database.model

// Data models
data class UnsyncedPatient(
  val id: String,
  val name: String,
  val encounters: List<UnsyncedEncounter> = emptyList(),
  var isExpanded: Boolean = false,
  val isSynced: Boolean = false,
)

data class UnsyncedEncounter(
  val id: String,
  val title: String,
  val patientId: String,
  val observations: List<UnsyncedObservation> = emptyList(),
  var isExpanded: Boolean = false,
  val isSynced: Boolean = false,
)

data class UnsyncedObservation(
  val id: String,
  val title: String,
  val encounterId: String,
  val patientId: String,
  val isSynced: Boolean = false,
)

// Combined view item for the RecyclerView
sealed class UnsyncedResource {
  data class PatientItem(val patient: UnsyncedPatient) : UnsyncedResource()

  data class EncounterItem(val encounter: UnsyncedEncounter) : UnsyncedResource()

  data class ObservationItem(val observation: UnsyncedObservation) : UnsyncedResource()
}
