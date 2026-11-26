package com.mas.alsalah.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PrayerCalculatorTest {
    @Test fun calculatesOrderedPrayerTimesForCairo() {
        val result = PrayerCalculator.calculate(
            LocalDate.of(2026, 3, 20), 30.0444, 31.2357, ZoneId.of("Africa/Cairo")
        )
        val times = listOf(result.fajr, result.sunrise, result.dhuhr, result.asr, result.maghrib, result.isha)
        assertEquals(6, times.size)
        assertTrue(times.zipWithNext().all { (a, b) -> a.isBefore(b) })
    }

    @Test fun ummAlQuraUsesNinetyMinuteIshaInterval() {
        val result = PrayerCalculator.calculate(
            (0..365).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }.first { java.time.chrono.HijrahDate.from(it).get(java.time.temporal.ChronoField.MONTH_OF_YEAR) == 10 }, 21.4225, 39.8262, ZoneId.of("Asia/Riyadh"),
            PrayerMethod.UMM_AL_QURA.calculation()
        )
        assertEquals(90L, java.time.Duration.between(result.maghrib, result.isha).toMinutes())
    }

    @Test fun hanafiAsrIsLaterThanStandardAsr() {
        val date = LocalDate.of(2026, 3, 20)
        val standard = PrayerCalculator.calculate(date, 30.0444, 31.2357, ZoneId.of("Africa/Cairo"), PrayerMethod.EGYPT.calculation())
        val hanafi = PrayerCalculator.calculate(date, 30.0444, 31.2357, ZoneId.of("Africa/Cairo"), PrayerMethod.EGYPT_HANAFI.calculation())
        assertTrue(hanafi.asr.isAfter(standard.asr))
    }

    @Test fun ummAlQuraUsesLongerIshaIntervalDuringRamadan() {
        val date = (0..365).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }.first { java.time.chrono.HijrahDate.from(it).get(java.time.temporal.ChronoField.MONTH_OF_YEAR) == 9 }
        val result = PrayerCalculator.calculate(date, 21.4225, 39.8262, ZoneId.of("Asia/Riyadh"), PrayerMethod.UMM_AL_QURA.calculation())
        assertEquals(120L, java.time.Duration.between(result.maghrib, result.isha).toMinutes())
    }

    @Test fun highLatitudePolarDateDoesNotCrash() {
        val summer = PrayerCalculator.calculate(LocalDate.of(2026, 6, 21), 70.0, 25.0, ZoneId.of("Europe/Helsinki"))
        val winter = PrayerCalculator.calculate(LocalDate.of(2026, 12, 21), 70.0, 25.0, ZoneId.of("Europe/Helsinki"))
        assertTrue(summer.fajr != summer.sunrise)
        assertTrue(winter.maghrib != winter.isha)
    }

    @Test fun qiblaBearingIsInCompassRange() {
        val bearing = QiblaCalculator.bearing(30.0444, 31.2357)
        assertTrue(bearing in 0.0..360.0)
        assertTrue(bearing in 135.6..136.7)
    }
}
