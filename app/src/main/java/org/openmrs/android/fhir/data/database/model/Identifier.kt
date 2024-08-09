package org.openmrs.android.fhir.data.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Identifier(
    @PrimaryKey val value: String,
    val identifierTypeUUID: String,
)