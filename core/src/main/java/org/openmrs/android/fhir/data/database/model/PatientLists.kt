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
import androidx.room.PrimaryKey

/**
 * A patient list (a Group of the server) as the server last said it. Only the server's word is kept
 * here; what the user changed and has not sent is in [PatientListChange] and
 * [PatientListMemberChange], and the screens show the two together (see `PatientListOverlay`).
 *
 * [quantity] is how many active members the server counted; [attributesJson] holds the patient list
 * module's own attributes as `[["type","value"], ...]`. [membersLoaded] says whether
 * [PatientListMemberRow] has the members of this patient list (they are read when a patient list is
 * opened, not with the list).
 */
@Entity(tableName = "patient_list")
data class PatientListRow(
  @PrimaryKey val id: String,
  val name: String,
  val description: String?,
  val typeName: String?,
  val locationId: String?,
  val locationName: String?,
  val creatorName: String?,
  val dateCreated: Long?,
  val changedByName: String?,
  val dateChanged: Long?,
  val quantity: Int,
  val attributesJson: String?,
  val membersLoaded: Boolean,
  val refreshedAt: Long,
)

@Entity(
  tableName = "patient_list_member",
  primaryKeys = ["listId", "patientId"],
  indices = [Index("listId")],
)
data class PatientListMemberRow(
  val listId: String,
  val patientId: String,
  val name: String,
  val gender: String?,
  val birthDate: String?,
  val identifier: String?,
)

/**
 * What the user did to a patient list and has not sent: [kind] is `CREATE`, `UPDATE` or `DELETE`.
 * There is one change per patient list and user: later edits are folded into it. [owner] is the
 * user who made it (blank for a guest): only they see it and send it, so a change goes to the
 * server under the session of the person who made it. [error] is the server's reason when it
 * refused the change; a change with an error is not sent again until the user asks.
 */
@Entity(tableName = "patient_list_change", primaryKeys = ["listId", "owner"])
data class PatientListChange(
  val listId: String,
  val kind: String,
  val name: String?,
  val description: String?,
  val locationId: String?,
  val locationName: String?,
  val createdAt: Long,
  val error: String?,
  val owner: String = "",
)

/**
 * A patient the user added to a patient list or removed from it and has not sent: [action] is `ADD`
 * or `REMOVE`. Adding and removing the same patient cancel out, so there is at most one per patient
 * and user ([owner], as for [PatientListChange]). The patient's details are kept so that the list
 * can show someone the device may not know.
 */
@Entity(
  tableName = "patient_list_member_change",
  primaryKeys = ["listId", "patientId", "owner"],
  indices = [Index("listId")],
)
data class PatientListMemberChange(
  val listId: String,
  val patientId: String,
  val action: String,
  val name: String,
  val gender: String?,
  val birthDate: String?,
  val identifier: String?,
  val createdAt: Long,
  val error: String?,
  /**
   * An add for a patient registered on the device and not uploaded: the server gives the patient
   * another id, so the add waits for the upload and then follows the patient.
   */
  val heldForUpload: Boolean = false,
  val owner: String = "",
)

/**
 * The id the server gave a patient registered on this device, noted when the upload answers: what
 * was added to a patient list for the id the patient was created with follows it to this one.
 */
@Entity(tableName = "patient_uploaded_id")
data class UploadedPatientId(
  @PrimaryKey val localId: String,
  val serverId: String,
  val uploadedAt: Long,
)

/**
 * What was done to a patient list on this device, and by whom: the history of the list. [action] is
 * one of [PatientListEventAction]; [patientId] and [patientName] are set for the members. Kept
 * after the change is sent, so it says what happened and not only what waits.
 */
@Entity(tableName = "patient_list_event", indices = [Index("listId")])
data class PatientListEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val listId: String,
  val action: String,
  val patientId: String?,
  val patientName: String?,
  val detail: String?,
  val user: String,
  val at: Long,
)

object PatientListEventAction {
  const val LIST_CREATED = "LIST_CREATED"
  const val LIST_RENAMED = "LIST_RENAMED"
  const val LIST_DELETED = "LIST_DELETED"
  const val MEMBER_ADDED = "MEMBER_ADDED"
  const val MEMBER_REMOVED = "MEMBER_REMOVED"
}

object PatientListChangeKind {
  const val CREATE = "CREATE"
  const val UPDATE = "UPDATE"
  const val DELETE = "DELETE"
}

object PatientListMemberAction {
  const val ADD = "ADD"
  const val REMOVE = "REMOVE"
}
