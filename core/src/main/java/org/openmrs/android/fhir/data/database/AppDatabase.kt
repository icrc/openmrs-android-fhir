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

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.openmrs.android.fhir.data.database.model.GroupSessionDraft
import org.openmrs.android.fhir.data.database.model.Identifier
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.data.database.model.PatientOwner
import org.openmrs.android.fhir.data.database.model.PendingChange
import org.openmrs.android.fhir.data.database.model.SyncSession

@Database(
  entities =
    [
      Identifier::class,
      IdentifierType::class,
      SyncSession::class,
      GroupSessionDraft::class,
      PatientOwner::class,
      PendingChange::class,
    ],
  version = 3,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun dao(): Dao

  abstract fun userDataDao(): UserDataDao

  companion object {
    /** Who owns the patients of the device, and who has records waiting to upload. */
    val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
          database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `patient_owner` (
              `patientId` TEXT NOT NULL,
              `owner` TEXT NOT NULL,
              PRIMARY KEY(`patientId`, `owner`)
            )
            """
              .trimIndent(),
          )
          database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_change` (
              `resourceType` TEXT NOT NULL,
              `resourceId` TEXT NOT NULL,
              `owner` TEXT NOT NULL,
              `patientId` TEXT,
              `changedAt` INTEGER NOT NULL,
              PRIMARY KEY(`resourceType`, `resourceId`, `owner`)
            )
            """
              .trimIndent(),
          )
          database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_change_owner` ON `pending_change` (`owner`)",
          )
        }
      }

    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
          database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `groupsessiondraft` (
              `questionnaireId` TEXT NOT NULL,
              `sessionId` TEXT NOT NULL,
              `patientIds` TEXT NOT NULL,
              `patientResponses` TEXT,
              `screenerResponse` TEXT,
              `encounterQuestionnaireJson` TEXT,
              `screenerQuestionnaireJson` TEXT,
              `sessionDate` INTEGER,
              `screenerCompleted` INTEGER NOT NULL,
              `lastUpdated` INTEGER NOT NULL,
              PRIMARY KEY(`questionnaireId`)
            )
            """
              .trimIndent(),
          )
        }
      }
  }
}
