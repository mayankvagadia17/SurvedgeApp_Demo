package com.nexova.survedge.ui.stakeout.model

data class StakeoutMeasurement(
    val targetPointId: String,
    val horizontalDistance: Double,
    val verticalDistance: Double,
    val northOffset: Double,
    val eastOffset: Double,
    val bearing: Double,
    val inTolerance: Boolean
)
