package com.mas.alsalah.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object QiblaCalculator {
    private const val KAABA_LATITUDE = 21.422487
    private const val KAABA_LONGITUDE = 39.826206

    /** Initial great-circle bearing from the supplied coordinates to the Kaaba, in degrees clockwise from true north. */
    fun bearing(latitude: Double, longitude: Double): Double {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLongitude = Math.toRadians(KAABA_LONGITUDE - longitude)
        val y = sin(deltaLongitude) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLongitude)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
