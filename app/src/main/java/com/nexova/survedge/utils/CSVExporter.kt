package com.nexova.survedge.utils

import com.nexova.survedge.data.db.entity.PointEntity
import java.util.Locale

object CSVExporter {
    private const val HEADER = "Name,Code,Code description,Easting,Northing,Elevation,Description,Longitude,Latitude,Ellipsoidal height,Origin,Easting RMS,Northing RMS,Elevation RMS,Lateral RMS,Antenna height,Antenna height units,Solution status,Correction type,Averaging start,Averaging end,Samples"

    fun generateCSV(points: List<PointEntity>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(HEADER).append("\n")

        points.forEach { point ->
            val row = formatPoint(point)
            stringBuilder.append(row).append("\n")
        }

        return stringBuilder.toString()
    }

    private fun formatPoint(point: PointEntity): String {
        return buildString {
            // Name
            append(formatValue(point.id)).append(",")
            // Code
            append(formatValue(point.code)).append(",")
            // Code description
            append(formatValue(point.description)).append(",")
            // Easting
            append(formatDouble(point.easting, 3)).append(",")
            // Northing
            append(formatDouble(point.northing, 3)).append(",")
            // Elevation (Orthometric height usually, but using elevation field)
            append(formatDouble(point.elevation, 3)).append(",")
            // Description
            append(formatValue(point.description)).append(",")
            // Longitude
            append(formatDouble(point.longitude, 8)).append(",")
            // Latitude
            append(formatDouble(point.latitude, 8)).append(",")
            // Ellipsoidal height
            append(formatDouble(point.ellipsoidalHeight, 3)).append(",")
            // Origin
            append("Global").append(",")
            // Easting RMS
            append(formatDouble(point.hRMS, 3)).append(",")
            // Northing RMS
            append(formatDouble(point.hRMS, 3)).append(",")
            // Elevation RMS
            append(formatDouble(point.vRMS, 3)).append(",")
            // Lateral RMS
            append(formatDouble(point.hRMS, 3)).append(",")
            // Antenna height
            append(formatDouble(point.antennaHeight, 3)).append(",")
            // Antenna height units
            append(formatValue(point.antennaHeightUnits)).append(",")
            // Solution status
            append(formatValue(point.solutionStatus)).append(",")
            // Correction type
            append(formatValue(point.correctionType)).append(",")
            // Averaging start
            append(formatValue(point.averagingStart)).append(",")
            // Averaging end
            append(formatValue(point.averagingEnd)).append(",")
            // Samples
            append(formatValue(point.samples))
        }
    }

    private fun formatValue(value: Any?): String {
        return value?.toString() ?: ""
    }

    private fun formatDouble(value: Double?, decimals: Int): String {
        return if (value != null) {
            String.format(Locale.US, "%.${decimals}f", value)
        } else {
            ""
        }
    }
}
