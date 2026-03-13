package com.nexova.survedge.ui.mapping.mapper

import com.nexova.survedge.data.db.entity.PointEntity
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint

fun PointEntity.toLabeledPoint(): LabeledPoint {
    return LabeledPoint(
        id = this.id,
        codeId = this.code,
        coords = listOf(this.longitude, this.latitude),
        elevation = this.elevation,
        ts = this.ts
    )
}

fun LabeledPoint.toPointEntity(projectId: Long): PointEntity {
    return PointEntity(
        id = this.id,
        projectId = projectId,
        code = this.codeId,
        latitude = this.coords.getOrElse(1) { 0.0 },
        longitude = this.coords.getOrElse(0) { 0.0 },
        elevation = this.elevation,
        ts = this.ts
    )
}
