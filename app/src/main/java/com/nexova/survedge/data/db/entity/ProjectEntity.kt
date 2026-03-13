package com.nexova.survedge.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdDate: Long,
    val lastModified: Long,
    val crs: String? = "WGS84", // Coordinate Reference System
    val operatorName: String? = null,
    val clientName: String? = null,
    val verticalDatum: String? = "Ellipsoidal height",
    val distanceUnit: String? = "Meters"
)
