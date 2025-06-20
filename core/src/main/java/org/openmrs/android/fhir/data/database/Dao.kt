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
import androidx.room.Transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import org.openmrs.android.fhir.data.database.model.Identifier
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus

@Dao
interface Dao {

  @Query("SELECT * from identifier WHERE identifierTypeUUID=:identifierTypeUUID LIMIT 1")
  suspend fun getOneIdentifierByType(identifierTypeUUID: String): Identifier?

  @Query("SELECT * from IdentifierType WHERE uuid=:identifierTypeUUID LIMIT 1")
  suspend fun getIdentifierTypeById(identifierTypeUUID: String): IdentifierType?

  @Query("SELECT * FROM identifiertype") suspend fun getAllIdentifierTypes(): List<IdentifierType>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAllIdentifierModel(identifiers: List<Identifier>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAllIdentifierTypeModel(identifierTypes: List<IdentifierType>)

  @Query("SELECT COUNT(*) FROM identifier WHERE identifierTypeUUID=:identifierTypeId")
  suspend fun getIdentifierCountByTypeId(identifierTypeId: String): Int

  @Query("SELECT COUNT(*) FROM IdentifierType") suspend fun getIdentifierTypesCount(): Int

  @Delete suspend fun delete(identifier: Identifier)

  @Query("DELETE FROM identifier WHERE value = :identifierValue ")
  suspend fun deleteIdentifierByValue(identifierValue: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSyncSession(syncSession: SyncSession)

  @Query("SELECT * FROM syncsession ORDER BY id DESC")
  fun getAllSyncSessions(): Flow<List<SyncSession>>

  @Query("SELECT * FROM syncsession") suspend fun getAllSyncSessionsAsList(): List<SyncSession>

  @Query("SELECT * FROM syncsession WHERE status='ONGOING' ORDER BY startTime DESC LIMIT 1")
  suspend fun getInProgressSyncSession(): SyncSession?

  @Query("UPDATE syncsession SET status = :newStatus WHERE id = :sessionId")
  suspend fun updateSyncSessionStatus(sessionId: Int, newStatus: SyncStatus)

  @Query(
    "UPDATE syncsession SET totalPatientsToDownload=:total, downloadedPatients=:completed WHERE id = :sessionId",
  )
  suspend fun updateSyncSessionDownloadValues(sessionId: Int, completed: Int, total: Int)

  @Query(
    "UPDATE syncsession SET totalPatientsToUpload=:total, uploadedPatients=:completed WHERE id = :sessionId",
  )
  suspend fun updateSyncSessionUploadValues(sessionId: Int, completed: Int, total: Int)

  @Query("UPDATE syncsession SET completionTime=:completionTime WHERE id = :sessionId")
  suspend fun updateSyncSessionCompletionTime(sessionId: Int, completionTime: String)

  @Query("UPDATE syncsession SET errors=:errors WHERE id = :sessionId")
  suspend fun updateSyncSessionErrors(sessionId: Int, errors: List<String>)

  @Query("DELETE FROM syncsession WHERE id = :sessionId")
  suspend fun deleteSyncSession(sessionId: Int)

  @Query("DELETE FROM syncsession WHERE status != 'ONGOING'")
  suspend fun clearAllSyncSessionsExceptOngoing()

  @Transaction
  suspend fun getOrCreateInProgressSyncSession(formatter: DateTimeFormatter): SyncSession {
    val existing = getInProgressSyncSession()
    if (existing != null) {
      return existing
    }

    val newSession =
      SyncSession(
        startTime = LocalDateTime.now().format(formatter).toString(),
        downloadedPatients = 0,
        uploadedPatients = 0,
        totalPatientsToDownload = 0,
        totalPatientsToUpload = 0,
        completionTime = null,
        status = SyncStatus.ONGOING,
      )
    insertSyncSession(newSession)
    return newSession
  }
}
