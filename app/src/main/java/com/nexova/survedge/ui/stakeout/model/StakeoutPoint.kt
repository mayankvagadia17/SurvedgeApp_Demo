package com.nexova.survedge.ui.stakeout.model

data class StakeoutPoint(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val isLine: Boolean = false,
    val order: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
