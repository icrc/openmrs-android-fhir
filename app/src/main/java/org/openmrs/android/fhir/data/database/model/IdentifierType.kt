package org.openmrs.android.fhir.data.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class IdentifierType(
    @PrimaryKey val uuid: String,
    val display: String?,
    val isAutoGenerated: Boolean,
    val required: Boolean,
    val sourceId: String,
) {
}