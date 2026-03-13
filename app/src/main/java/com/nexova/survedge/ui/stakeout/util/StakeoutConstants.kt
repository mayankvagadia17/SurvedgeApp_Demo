package com.nexova.survedge.ui.stakeout.util

object StakeoutConstants {
    // Distance thresholds (meters)
    const val BULLSEYE_TRANSITION_DISTANCE = 0.5 // meters (Zoomed out view starts earlier)
    const val BULLSEYE_EXIT_DISTANCE = 0.6 // Switch back to map mode
    const val DEFAULT_TOLERANCE = 0.05 // In-tolerance threshold
    
    // Default values
    const val DEFAULT_POLE_HEIGHT = 1.80 // meters
    const val DEFAULT_UPDATE_RATE = 1.0 // Hz
    
    // UI constants
    const val BULLSEYE_RING_COUNT = 4
    const val BULLSEYE_RING_INTERVAL = 0.1 // meters between rings
    
    // Auto-follow delay
    const val AUTO_FOLLOW_DELAY_MS = 2000L // 2 seconds
}
