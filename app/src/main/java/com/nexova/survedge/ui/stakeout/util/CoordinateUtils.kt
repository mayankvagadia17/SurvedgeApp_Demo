package com.nexova.survedge.ui.stakeout.util

import kotlin.math.*

object CoordinateUtils {
    
    private const val EARTH_RADIUS = 6371000.0 // meters
    
    /**
     * Calculate horizontal distance between two points using Haversine formula
     * @return distance in meters
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS * c
    }
    
    /**
     * Calculate bearing from point 1 to point 2
     * @return bearing in degrees (0-360)
     */
    fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
    
    /**
     * Calculate North and East offsets from user to target
     * @return Pair(northOffset, eastOffset) in meters
     * Positive north = target is north of user
     * Positive east = target is east of user
     */
    fun calculateOffsets(
        userLat: Double, userLon: Double,
        targetLat: Double, targetLon: Double
    ): Pair<Double, Double> {
        val dLat = Math.toRadians(targetLat - userLat)
        val dLon = Math.toRadians(targetLon - userLon)
        
        val northOffset = dLat * EARTH_RADIUS
        val eastOffset = dLon * EARTH_RADIUS * cos(Math.toRadians(userLat))
        
        return Pair(northOffset, eastOffset)
    }
    
    /**
     * Calculate vertical distance with pole height adjustment
     * @return vertical distance in meters
     * Positive = fill (target is higher)
     * Negative = cut (target is lower)
     */
    fun calculateVerticalDistance(
        userElevation: Double,
        targetElevation: Double,
        poleHeight: Double
    ): Double {
        return targetElevation - (userElevation + poleHeight)
    }
}
