package com.nexova.survedge.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "points",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["code"]), Index(value = ["projectId", "id"])]
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true)
    val pk: Long = 0,
    val id: String, // User-facing ID like "P1" or UUID
    val projectId: Long,
    
    // Core attributes
    val code: String,
    val description: String? = null,
    
    // Coordinates (Stored as pure formatting double for flexibility)
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val ellipsoidalHeight: Double? = null,
    
    // Grid Coordinates
    val easting: Double? = null,
    val northing: Double? = null,
    val zone: String? = null,
    
    // GNSS Metadata
    val hRMS: Double? = null, // Horizontal RMS
    val vRMS: Double? = null, // Vertical RMS
    val pdop: Double? = null,
    val gdop: Double? = null,
    val satellitesCount: Int? = null,
    val specificSatellites: String? = null, // JSON string or delimited list of sat counts per constellation (GPS:10, GLONASS:5)
    val solutionStatus: String? = null, // "FIXED", "FLOAT", "SINGLE"
    val correctionType: String? = null,
    
    // Antenna Info
    val antennaHeight: Double? = null,
    val antennaHeightUnits: String? = "m",
    
    // Base Station Info
    val baseEasting: Double? = null,
    val baseNorthing: Double? = null,
    val baseElevation: Double? = null,
    val baseLongitude: Double? = null,
    val baseLatitude: Double? = null,
    val baseEllipsoidalHeight: Double? = null,
    val baselineLength: Double? = null,
    val mountPoint: String? = null,
    
    // Time
    val ts: String, // ISO 8601 string as per JSON requirement
    val averagingStart: String? = null,
    val averagingEnd: String? = null,
    val samples: Int? = null,
    
    // Device Info
    val deviceType: String? = null,
    val deviceSerialNumber: String? = null
)
