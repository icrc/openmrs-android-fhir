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
package org.openmrs.android.fhir.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListEvent
import org.openmrs.android.fhir.data.database.model.PatientListMemberChange
import org.openmrs.android.fhir.data.database.model.PatientListMemberRow
import org.openmrs.android.fhir.data.database.model.PatientListRow
import org.openmrs.android.fhir.data.database.model.UploadedPatientId

/**
 * The patient lists kept on the device: what the server said (`patient list`,
 * `patient_list_member`) and what the user did and has not sent (`patient_list_change`,
 * `patient_list_member_change`). Nothing here is in the FHIR engine: its upload would send a Group
 * as a patch, which the server does not take.
 */
@Dao
interface PatientListsDao {
  @Query("SELECT * FROM patient_list") fun observePatientLists(): Flow<List<PatientListRow>>

  @Query("SELECT * FROM patient_list") suspend fun patientLists(): List<PatientListRow>

  @Query("SELECT * FROM patient_list WHERE id = :id")
  suspend fun patientList(id: String): PatientListRow?

  @Query("SELECT * FROM patient_list WHERE id = :id")
  fun observePatientList(id: String): Flow<PatientListRow?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPatientLists(rows: List<PatientListRow>)

  @Query("DELETE FROM patient_list WHERE id IN (:ids)")
  suspend fun deletePatientLists(ids: List<String>)

  @Query("DELETE FROM patient_list") suspend fun clearPatientLists()

  @Query("SELECT * FROM patient_list_member WHERE listId = :listId ORDER BY name COLLATE NOCASE")
  fun observeMembers(listId: String): Flow<List<PatientListMemberRow>>

  @Query("SELECT * FROM patient_list_member WHERE patientId = :patientId")
  fun observeMembershipsOf(patientId: String): Flow<List<PatientListMemberRow>>

  @Query("SELECT * FROM patient_list_member WHERE listId = :listId")
  suspend fun members(listId: String): List<PatientListMemberRow>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMembers(rows: List<PatientListMemberRow>)

  @Query("DELETE FROM patient_list_member WHERE listId = :listId")
  suspend fun clearMembers(listId: String)

  @Query("DELETE FROM patient_list_member WHERE listId = :listId AND patientId IN (:patientIds)")
  suspend fun removeMembers(listId: String, patientIds: List<String>)

  @Query("DELETE FROM patient_list_member") suspend fun clearAllMembers()

  @Transaction
  suspend fun replaceMembers(listId: String, rows: List<PatientListMemberRow>) {
    clearMembers(listId)
    upsertMembers(rows)
  }

  @Query("SELECT * FROM patient_list_change ORDER BY createdAt")
  fun observeChanges(): Flow<List<PatientListChange>>

  @Query("SELECT * FROM patient_list_change ORDER BY createdAt")
  suspend fun changes(): List<PatientListChange>

  @Query("SELECT * FROM patient_list_change WHERE listId = :listId")
  suspend fun change(listId: String): PatientListChange?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChange(change: PatientListChange)

  @Query("DELETE FROM patient_list_change WHERE listId = :listId")
  suspend fun removeChange(listId: String)

  @Query("DELETE FROM patient_list_change") suspend fun clearChanges()

  @Query("SELECT * FROM patient_list_change ORDER BY createdAt")
  suspend fun allChanges(): List<PatientListChange>

  @Query("DELETE FROM patient_list_change WHERE listId = :listId AND owner = :owner")
  suspend fun removeChangeFor(listId: String, owner: String)

  @Query("DELETE FROM patient_list_change WHERE owner = :owner")
  suspend fun clearChangesFor(owner: String)

  @Query("DELETE FROM patient_list_change") suspend fun wipeChanges()

  @Query("SELECT * FROM patient_list_member_change ORDER BY createdAt")
  fun observeMemberChanges(): Flow<List<PatientListMemberChange>>

  @Query("SELECT * FROM patient_list_member_change ORDER BY createdAt")
  suspend fun memberChanges(): List<PatientListMemberChange>

  @Query(
    "SELECT * FROM patient_list_member_change WHERE listId = :listId AND patientId = :patientId",
  )
  suspend fun memberChange(listId: String, patientId: String): PatientListMemberChange?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putMemberChanges(changes: List<PatientListMemberChange>)

  @Query(
    "DELETE FROM patient_list_member_change WHERE listId = :listId AND patientId IN (:patientIds)",
  )
  suspend fun removeMemberChanges(listId: String, patientIds: List<String>)

  @Query("DELETE FROM patient_list_member_change WHERE listId = :listId")
  suspend fun removeMemberChangesOf(listId: String)

  @Query("DELETE FROM patient_list_member_change") suspend fun clearMemberChanges()

  @Query("SELECT * FROM patient_list_member_change ORDER BY createdAt")
  suspend fun allMemberChanges(): List<PatientListMemberChange>

  @Query(
    "DELETE FROM patient_list_member_change " +
      "WHERE listId = :listId AND patientId IN (:patientIds) AND owner = :owner",
  )
  suspend fun removeMemberChangesFor(listId: String, patientIds: List<String>, owner: String)

  @Query("DELETE FROM patient_list_member_change WHERE listId = :listId AND owner = :owner")
  suspend fun removeMemberChangesOfListFor(listId: String, owner: String)

  @Query("DELETE FROM patient_list_member_change WHERE owner = :owner")
  suspend fun clearMemberChangesFor(owner: String)

  @Query("DELETE FROM patient_list_member_change") suspend fun wipeMemberChanges()

  @Query(
    "SELECT (SELECT COUNT(*) FROM patient_list_change) + (SELECT COUNT(*) FROM patient_list_member_change)",
  )
  suspend fun pendingCount(): Int

  @Query(
    "SELECT (SELECT COUNT(*) FROM patient_list_change) + (SELECT COUNT(*) FROM patient_list_member_change)",
  )
  fun observePendingCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUploadedId(row: UploadedPatientId)

  @Query("SELECT * FROM patient_uploaded_id WHERE localId = :localId")
  suspend fun uploadedId(localId: String): UploadedPatientId?

  @Query("DELETE FROM patient_uploaded_id WHERE localId = :localId")
  suspend fun removeUploadedId(localId: String)

  @Query("DELETE FROM patient_uploaded_id") suspend fun clearUploadedIds()

  @Query("DELETE FROM patient_uploaded_id WHERE uploadedAt < :before")
  suspend fun pruneUploadedIds(before: Long)

  @Insert suspend fun addEvents(events: List<PatientListEvent>)

  @Query("SELECT * FROM patient_list_event WHERE listId = :listId ORDER BY at DESC, id DESC")
  fun observeEvents(listId: String): Flow<List<PatientListEvent>>

  @Query("SELECT * FROM patient_list_event WHERE listId = :listId ORDER BY at DESC, id DESC")
  suspend fun events(listId: String): List<PatientListEvent>

  @Query(
    "DELETE FROM patient_list_event WHERE listId = :listId AND id NOT IN " +
      "(SELECT id FROM patient_list_event WHERE listId = :listId ORDER BY at DESC, id DESC LIMIT :keep)",
  )
  suspend fun trimEvents(listId: String, keep: Int)

  @Query("DELETE FROM patient_list_event WHERE listId = :listId")
  suspend fun removeEventsOf(listId: String)

  @Query("DELETE FROM patient_list_event") suspend fun clearEvents()
}
