package com.nexova.survedge.utils

import com.google.gson.Gson
import com.nexova.survedge.data.db.entity.PointEntity
import com.nexova.survedge.data.model.PointImportRoot

object JSONImporter {

    fun parseJSON(jsonContent: String, projectId: Long): List<PointEntity> {
        return try {
            val root = Gson().fromJson(jsonContent, PointImportRoot::class.java)
            root.points.map { importItem ->
                PointEntity(
                    id = importItem.id,
                    projectId = projectId,
                    code = importItem.codeId ?: "NO-CODE",
                    description = null,
                    // Coords usually [Longitude, Latitude] in GeoJSON-like formats
                    longitude = importItem.coords.getOrElse(0) { 0.0 },
                    latitude = importItem.coords.getOrElse(1) { 0.0 },
                    elevation = importItem.elevation ?: 0.0,
                    ts = importItem.ts
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
