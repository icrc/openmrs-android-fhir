package org.openmrs.android.fhir.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.openmrs.android.fhir.data.database.model.Identifier
import org.openmrs.android.fhir.data.database.model.IdentifierType

@Database(entities = [Identifier::class, IdentifierType::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): Dao
}