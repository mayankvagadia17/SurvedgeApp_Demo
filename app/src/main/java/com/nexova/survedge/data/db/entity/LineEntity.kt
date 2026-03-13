package com.nexova.survedge.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lines",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["projectId", "id"])]
)
data class LineEntity(
    @PrimaryKey(autoGenerate = true)
    val pk: Long = 0,
    val id: String, // Line ID (e.g. "L1")
    val projectId: Long,
    val code: String, // Feature code (e.g. "ROAD")
    val name: String? = null,
    val description: String? = null,
    val length: Double? = 0.0,
    val isClosed: Boolean = false,
    val createdDate: Long = System.currentTimeMillis()
)
