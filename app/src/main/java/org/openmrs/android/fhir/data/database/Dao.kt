package org.openmrs.android.fhir.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.openmrs.android.fhir.data.database.model.Identifier
import org.openmrs.android.fhir.data.database.model.IdentifierType

@Dao
interface Dao {

    @Query("SELECT * from identifier WHERE identifierTypeUUID=:identifierTypeUUID LIMIT 1")
    suspend fun getOneIdentifierByType(identifierTypeUUID: String): Identifier?

    @Query("SELECT * from IdentifierType WHERE uuid=:identifierTypeUUID LIMIT 1")
    suspend fun getIdentifierTypeById(identifierTypeUUID: String): IdentifierType?

    @Query("SELECT * FROM identifiertype")
    suspend fun getAllIdentifierTypes(): List<IdentifierType>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllIdentifierModel(identifiers: List<Identifier>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllIdentifierTypeModel(identifierTypes: List<IdentifierType>)

    @Query("SELECT COUNT(*) FROM identifier WHERE identifierTypeUUID=:identifierTypeId")
    suspend fun getIdentifierCountByTypeId(identifierTypeId: String): Int
    @Delete
    suspend fun delete(identifier: Identifier)

    @Query("DELETE FROM identifier WHERE value = :identifierValue ")
    suspend fun deleteIdentifierByValue(identifierValue: String)

}