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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.openmrs.android.fhir.data.database.model.PatientOwner
import org.openmrs.android.fhir.data.database.model.PendingChange

@Dao
interface UserDataDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addOwners(owners: List<PatientOwner>)

  /** At most a few hundred ids at a time (SQLite limits the variables of one statement). */
  @Query("SELECT * FROM patient_owner WHERE patientId IN (:patientIds)")
  suspend fun ownersOf(patientIds: List<String>): List<PatientOwner>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addPending(changes: List<PendingChange>)

  @Query("SELECT COUNT(*) FROM pending_change") suspend fun pendingCount(): Int

  @Query("SELECT * FROM pending_change") suspend fun allPending(): List<PendingChange>

  @Delete suspend fun removePending(changes: List<PendingChange>)

  @Query("DELETE FROM patient_owner WHERE patientId IN (:patientIds)")
  suspend fun removeOwnersOf(patientIds: List<String>)

  @Query("DELETE FROM pending_change WHERE patientId IN (:patientIds)")
  suspend fun removePendingOf(patientIds: List<String>)

  @Query("DELETE FROM pending_change") suspend fun clearPending()

  @Query("DELETE FROM patient_owner") suspend fun clearOwners()
}
