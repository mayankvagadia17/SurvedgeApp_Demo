package com.nexova.survedge.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "line_points",
    primaryKeys = ["linePk", "pointPk", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = LineEntity::class,
            parentColumns = ["pk"],
            childColumns = ["linePk"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PointEntity::class,
            parentColumns = ["pk"],
            childColumns = ["pointPk"],
            onDelete = ForeignKey.CASCADE // If point is deleted, remove it from line
        )
    ],
    indices = [
        Index(value = ["linePk"]), 
        Index(value = ["pointPk"])
    ]
)
data class LinePointCrossRef(
    val linePk: Long,
    val pointPk: Long,
    val orderIndex: Int // Important for sequence in a line
)
