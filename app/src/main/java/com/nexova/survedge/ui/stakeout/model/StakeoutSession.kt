package com.nexova.survedge.ui.stakeout.model

data class StakeoutSession(
    val id: String,
    val targetPoints: List<StakeoutPoint>,
    val currentIndex: Int = 0,
    val poleHeight: Double = 1.80,
    val toleranceThreshold: Double = 0.05,
    val autoFollowEnabled: Boolean = true,
    val startedAt: Long = System.currentTimeMillis()
)
