package com.mas.alsalah

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mas.alsalah.data.SavedSettings
import com.mas.alsalah.data.SettingsRepository
import com.mas.alsalah.data.PrayerAlertSettings
import com.mas.alsalah.data.CachedPrayerDay
import com.mas.alsalah.core.PrayerCalculator
import com.mas.alsalah.core.PrayerMethod
import java.time.LocalDate
import java.time.ZoneId
import com.mas.alsalah.notifications.PrayerNotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SavedSettings()
    )

    fun saveLocation(latitude: Double, longitude: Double, city: String) {
        viewModelScope.launch {
            repository.saveLocation(latitude, longitude, city)
            PrayerNotificationScheduler.scheduleNext(getApplication())
        }
    }

    fun saveCity(city: String) {
        viewModelScope.launch { repository.saveCity(city) }
    }

    fun saveTasbeeh(count: Int, target: Int) {
        viewModelScope.launch { repository.saveTasbeeh(count, target) }
    }

    fun saveCalculationMethod(method: String) {
        viewModelScope.launch {
            repository.saveCalculationMethod(method)
            PrayerNotificationScheduler.scheduleNext(getApplication())
        }
    }

    fun saveClockFormat(use24Hour: Boolean) { viewModelScope.launch { repository.saveClockFormat(use24Hour) } }

    fun savePrayerOffset(prayer: String, minutes: Int) {
        viewModelScope.launch {
            repository.savePrayerOffset(prayer, minutes)
            PrayerNotificationScheduler.scheduleNext(getApplication())
        }
    }

    fun savePrayerAlert(prayer: String, value: PrayerAlertSettings) {
        viewModelScope.launch {
            repository.savePrayerAlert(prayer, value)
            PrayerNotificationScheduler.scheduleNext(getApplication())
        }
    }

    fun savePrayerVolume(prayer: String, volumePercent: Int) {
        viewModelScope.launch { repository.savePrayerVolume(prayer, volumePercent) }
    }

    fun setAllPrayerAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAllPrayerAlertsEnabled(enabled)
            PrayerNotificationScheduler.scheduleNext(getApplication())
        }
    }

    fun saveHijriDayOffset(days: Int) { viewModelScope.launch { repository.saveHijriDayOffset(days) } }
    fun saveShowSunrise(show: Boolean) { viewModelScope.launch { repository.saveShowSunrise(show) } }
    fun saveDarkMode(enabled: Boolean) { viewModelScope.launch { repository.saveDarkMode(enabled) } }
    fun saveThemeMode(mode: String) { viewModelScope.launch { repository.saveThemeMode(mode) } }

    fun cachePrayerTimes(settings: SavedSettings) {
        val latitude = settings.latitude ?: return
        val longitude = settings.longitude ?: return
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone)
            val method = PrayerMethod.values().firstOrNull { it.id == settings.calculationMethod } ?: PrayerMethod.EGYPT
            val signature = "${"%.5f".format(java.util.Locale.US, latitude)}|${"%.5f".format(java.util.Locale.US, longitude)}|${method.id}|${zone.id}"
            if (repository.hasPrayerTimesCache(signature, start.toString())) return@launch
            // Prayer calculations are CPU work: keep the 30-day cache generation off the UI thread.
            val days = withContext(Dispatchers.Default) {
                (0 until 30).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    val times = PrayerCalculator.calculate(date, latitude, longitude, zone, method.calculation())
                    CachedPrayerDay(
                        date = date.toString(), fajr = times.fajr.toString(), sunrise = times.sunrise.toString(),
                        dhuhr = times.dhuhr.toString(), asr = times.asr.toString(),
                        maghrib = times.maghrib.toString(), isha = times.isha.toString()
                    )
                }
            }
            repository.savePrayerTimesCache(signature, days)
        }
    }

    fun saveAdhkarProgress(category: String, index: Int, count: Int) {
        viewModelScope.launch { repository.saveAdhkarProgress(category, index, count) }
    }
}
