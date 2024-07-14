package org.openmrs.android.fhir.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.openmrs.android.fhir.data.database.model.IdentifierModel
import org.openmrs.android.fhir.data.database.model.IdentifierTypeModel

@Database(entities = [IdentifierModel::class, IdentifierTypeModel::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): Dao
}