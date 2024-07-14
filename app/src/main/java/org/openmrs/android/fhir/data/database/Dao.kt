package org.openmrs.android.fhir.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.openmrs.android.fhir.data.database.model.IdentifierModel
import org.openmrs.android.fhir.data.database.model.IdentifierTypeModel

@Dao
interface Dao {

    @Query("SELECT * from identifiermodel WHERE identifierTypeUUID=:identifierTypeUUID LIMIT 1")
    suspend fun getOneIdentifierByType(identifierTypeUUID: String): IdentifierModel?

    @Query("SELECT * from IdentifierTypeModel WHERE uuid=:identifierTypeUUID LIMIT 1")
    suspend fun getIdentifierTypeById(identifierTypeUUID: String): IdentifierTypeModel?

    @Query("SELECT * FROM identifiertypemodel")
    suspend fun getAllIdentifierTypes(): List<IdentifierTypeModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllIdentifierModel(identifiers: List<IdentifierModel>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllIdentifierTypeModel(identifierTypes: List<IdentifierTypeModel>)

    @Query("SELECT COUNT(*) FROM identifiermodel WHERE identifierTypeUUID=:identifierTypeId")
    suspend fun getIdentifierCountByTypeId(identifierTypeId: String): Int
    @Delete
    suspend fun delete(identifierModel: IdentifierModel)

    @Query("DELETE FROM identifiermodel WHERE value = :identifierValue ")
    suspend fun deleteIdentifierByValue(identifierValue: String)

}