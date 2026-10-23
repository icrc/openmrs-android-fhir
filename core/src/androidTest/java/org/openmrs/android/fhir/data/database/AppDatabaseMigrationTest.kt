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

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The database of the app keeps its data when the app is updated from any older version. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
  private val databaseName = "migration-test"

  @get:Rule
  val helper =
    MigrationTestHelper(
      InstrumentationRegistry.getInstrumentation(),
      AppDatabase::class.java,
    )

  private val allMigrations =
    arrayOf(
      AppDatabase.MIGRATION_1_2,
      AppDatabase.MIGRATION_2_3,
      AppDatabase.MIGRATION_3_4,
    )

  @Test
  fun migratesFromVersion1ToTheLatestAndKeepsTheIdentifiers() {
    helper.createDatabase(databaseName, 1).apply {
      execSQL("INSERT INTO Identifier (value, identifierTypeUUID) VALUES ('10000X', 'type-1')")
      close()
    }

    val migrated = helper.runMigrationsAndValidate(databaseName, 4, true, *allMigrations)

    migrated.query("SELECT value, identifierTypeUUID FROM Identifier").use {
      assertTrue(it.moveToFirst())
      assertEquals("10000X", it.getString(0))
      assertEquals("type-1", it.getString(1))
    }
    // The tables added since exist and are empty.
    listOf("groupsessiondraft", "patient_owner", "pending_change", "unidentified_patient")
      .forEach { table ->
        migrated.query("SELECT COUNT(*) FROM $table").use {
          assertTrue(it.moveToFirst())
          assertEquals(0, it.getInt(0))
        }
      }
  }

  @Test
  fun keepsTheGroupSessionDraftsOfVersion2() {
    helper.createDatabase(databaseName, 2).apply {
      // A draft saved before the responses were required: its column holds nothing.
      execSQL(
        "INSERT INTO groupsessiondraft (questionnaireId, sessionId, patientIds, patientResponses, screenerCompleted, lastUpdated) " +
          "VALUES ('form-1', 'session-1', 'p1,p2', NULL, 0, 1000)",
      )
      execSQL(
        "INSERT INTO groupsessiondraft (questionnaireId, sessionId, patientIds, patientResponses, screenerCompleted, lastUpdated) " +
          "VALUES ('form-2', 'session-2', 'p3', '{\"p3\":\"r\"}', 1, 2000)",
      )
      close()
    }

    val migrated =
      helper.runMigrationsAndValidate(
        databaseName,
        4,
        true,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
      )

    migrated
      .query(
        "SELECT sessionId, patientIds, patientResponses FROM groupsessiondraft ORDER BY lastUpdated",
      )
      .use {
        assertTrue(it.moveToFirst())
        assertEquals("session-1", it.getString(0))
        assertEquals("p1,p2", it.getString(1))
        assertEquals("{}", it.getString(2))
        assertTrue(it.moveToNext())
        assertEquals("session-2", it.getString(0))
        assertEquals("{\"p3\":\"r\"}", it.getString(2))
      }
  }

  @Test
  fun migratesTheVersion3Tables() {
    helper.createDatabase(databaseName, 3).close()
    val migrated = helper.runMigrationsAndValidate(databaseName, 4, true, AppDatabase.MIGRATION_3_4)
    migrated.execSQL(
      "INSERT INTO unidentified_patient (patientId, note, createdAt) VALUES ('p1', 'note', 1)",
    )
    migrated.query("SELECT note FROM unidentified_patient").use {
      assertTrue(it.moveToFirst())
      assertEquals("note", it.getString(0))
    }
  }
}
