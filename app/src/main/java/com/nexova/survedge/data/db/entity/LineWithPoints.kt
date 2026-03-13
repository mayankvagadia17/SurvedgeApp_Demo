package com.nexova.survedge.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class LineWithPoints(
    @Embedded val line: LineEntity,
    @Relation(
        parentColumn = "pk", // line.pk
        entityColumn = "pk", // point.pk
        associateBy = Junction(
            value = LinePointCrossRef::class,
            parentColumn = "linePk",
            entityColumn = "pointPk"
        )
    )
    val points: List<PointEntity>
)
