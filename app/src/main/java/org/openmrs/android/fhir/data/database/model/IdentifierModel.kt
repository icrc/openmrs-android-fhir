package org.openmrs.android.fhir.data.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class IdentifierModel(
    @PrimaryKey val value: String,
    val identifierTypeUUID: String,
)