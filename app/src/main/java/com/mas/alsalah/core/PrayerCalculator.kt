package com.mas.alsalah.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.*

/**
 * Offline solar prayer-time calculator.
 * Uses the NOAA solar declination/equation-of-time approximation and configurable
 * Fajr/Isha twilight angles. Results are local wall-clock times for the supplied zone.
 */
data class PrayerCalculation(
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val asr: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime
)

data class PrayerCalculationMethod(
    val fajrAngle: Double = 18.0,
    val ishaAngle: Double = 17.0,
    val asrShadowFactor: Double = 1.0,
    val ishaFixedMinutes: Int? = null
) {
    init {
        require(fajrAngle in 10.0..24.0)
        require(ishaAngle in 10.0..24.0)
        require(asrShadowFactor in 1.0..2.0)
        require(ishaFixedMinutes == null || ishaFixedMinutes in 60..120)
    }
}

object PrayerCalculator {
    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        method: PrayerCalculationMethod = PrayerCalculationMethod()
    ): PrayerCalculation {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val day = date.dayOfYear
        val gamma = 2.0 * PI / 365.0 * (day - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val offsetHours = date.atTime(12, 0).atZone(zoneId).offset.totalSeconds / 3600.0
        val solarNoon = 720.0 - 4.0 * longitude - eqTime + offsetHours * 60.0
        val lat = Math.toRadians(latitude)

        fun hourAngle(altitudeDegrees: Double): Double {
            val altitude = Math.toRadians(altitudeDegrees)
            val denominator = cos(lat) * cos(decl)
            if (abs(denominator) < 1e-10) return Double.NaN
            val cosine = (sin(altitude) - sin(lat) * sin(decl)) / denominator
            if (cosine !in -1.0..1.0) return Double.NaN
            return Math.toDegrees(acos(cosine)) * 4.0
        }
        fun time(minutes: Double): LocalTime {
            val normalized = ((minutes % 1440.0) + 1440.0) % 1440.0
            val whole = normalized.roundToInt()
            return LocalTime.of((whole / 60) % 24, whole % 60)
        }
        fun twilight(angle: Double): Double {
            val ha = hourAngle(-angle)
            return if (ha.isNaN()) Double.NaN else solarNoon - ha
        }
        // At polar latitudes the sun may not cross the standard sunrise altitude.
        // Use a 12-hour solar-day fallback so the schedule remains usable rather than crashing.
        val sunriseHa = hourAngle(-0.833).let { if (it.isNaN()) 90.0 else it }
        val sunsetHa = sunriseHa
        val asrAltitude = Math.toDegrees(atan(1.0 / (method.asrShadowFactor + tan(abs(lat - decl)))))
        val asrHa = hourAngle(asrAltitude)
        val fajrMinutes = twilight(method.fajrAngle)
        val ishaMinutes = if (method.ishaFixedMinutes != null) Double.NaN else solarNoon + hourAngle(-method.ishaAngle)
        val sunriseMinutes = if (sunriseHa.isNaN()) Double.NaN else solarNoon - sunriseHa
        val sunsetMinutes = if (sunsetHa.isNaN()) Double.NaN else solarNoon + sunsetHa
        // High-latitude fallback: use one seventh of the night for twilight when
        // the requested angle is below the horizon for the selected date.
        val night = (sunriseMinutes + 1440.0) - sunsetMinutes
        val fajr = if (fajrMinutes.isNaN()) sunriseMinutes - night / 7.0 else fajrMinutes
        val fixedIsha = method.ishaFixedMinutes?.let { base ->
            if (base == 90 && HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR) == 9) 120 else base
        }
        val isha = fixedIsha?.let { sunsetMinutes + it } ?: if (ishaMinutes.isNaN()) sunsetMinutes + night / 7.0 else ishaMinutes
        return PrayerCalculation(
            fajr = time(fajr),
            sunrise = time(sunriseMinutes),
            dhuhr = time(solarNoon + 1.0),
            asr = time(if (asrHa.isNaN()) solarNoon + 0.5 * (sunsetMinutes - solarNoon) else solarNoon + asrHa),
            maghrib = time(sunsetMinutes),
            isha = time(isha)
        )
    }
}
