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

import androidx.room.Entity
import androidx.room.Index

/**
 * A patient that [owner] brought to the device: registered here, or downloaded while signed in. A
 * patient can have several owners (each user who downloaded them). Only kept once the device has
 * more than one user.
 */
@Entity(tableName = "patient_owner", primaryKeys = ["patientId", "owner"])
data class PatientOwner(
  val patientId: String,
  val owner: String,
)

/**
 * A record [owner] created, changed or deleted on the device. It is a note, not the queue: the
 * queue of changes to upload is the FHIR engine's. A note is dropped once the engine has nothing to
 * upload for the record, so the table only holds what is waiting (plus what was uploaded since the
 * last time it was looked at).
 */
@Entity(
  tableName = "pending_change",
  primaryKeys = ["resourceType", "resourceId", "owner"],
  indices = [Index("owner")],
)
data class PendingChange(
  val resourceType: String,
  val resourceId: String,
  val owner: String,
  /** The patient the record is about, to count people rather than records; none for the rest. */
  val patientId: String?,
  val changedAt: Long,
)

/** A user with data waiting to upload, as the login page lists them. */
data class PendingUser(
  val user: String,
  val patients: Int,
  val records: Int,
)
