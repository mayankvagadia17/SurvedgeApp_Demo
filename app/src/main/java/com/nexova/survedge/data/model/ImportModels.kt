package com.nexova.survedge.data.model

data class PointImportRoot(
    val points: List<PointImportItem>
)

data class PointImportItem(
    val id: String,
    val codeId: String?,
    val coords: List<Double>,
    val elevation: Double?,
    val ts: String
)
