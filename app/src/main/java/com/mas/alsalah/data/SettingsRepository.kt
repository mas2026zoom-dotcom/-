package com.mas.alsalah.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.appDataStore by preferencesDataStore(name = "al_salah_settings")

enum class AlertSound { TAKBEER, TONE, SILENT }
data class PrayerAlertSettings(
    val enabled: Boolean = true,
    val preAlertMinutes: Int = 0,
    val atPrayerTime: Boolean = true,
    val sound: AlertSound = AlertSound.TAKBEER,
    val vibration: Boolean = true,
    val volumePercent: Int = 80,
    val repeat: Boolean = false
) {
    init {
        require(preAlertMinutes in setOf(0, 5, 10, 15, 20, 30) || preAlertMinutes in 1..60)
        require(volumePercent in 0..100)
    }
}

data class SavedSettings(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String = "",
    val adhkarCount: Int = 0,
    val adhkarCategory: String = "الصباح",
    val adhkarIndex: Int = 0,
    val tasbeehCount: Int = 0,
    val tasbeehTarget: Int = 33,
    val calculationMethod: String = "EGYPT",
    val use24Hour: Boolean = true,
    val manualOffsets: Map<String, Int> = emptyMap(),
    val alerts: Map<String, PrayerAlertSettings> = emptyMap(),
    val hijriDayOffset: Int = 0,
    val showSunrise: Boolean = true,
    val darkMode: Boolean = false,
    val themeMode: String = "SYSTEM"
) {
    fun alertFor(prayer: String) = alerts[prayer] ?: PrayerAlertSettings()
    fun offsetFor(prayer: String) = manualOffsets[prayer] ?: 0
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val latitude = doublePreferencesKey("latitude")
        val longitude = doublePreferencesKey("longitude")
        val city = stringPreferencesKey("city")
        val adhkarCount = intPreferencesKey("adhkar_count")
        val adhkarCategory = stringPreferencesKey("adhkar_category")
        val adhkarIndex = intPreferencesKey("adhkar_index")
        val tasbeehCount = intPreferencesKey("tasbeeh_count")
        val tasbeehTarget = intPreferencesKey("tasbeeh_target")
        val method = stringPreferencesKey("calculation_method")
        val use24Hour = booleanPreferencesKey("use_24_hour")
        val hijriOffset = intPreferencesKey("hijri_day_offset")
        val showSunrise = booleanPreferencesKey("show_sunrise")
        val darkMode = booleanPreferencesKey("dark_mode")
        val themeMode = stringPreferencesKey("theme_mode")
        val prayerCacheSignature = stringPreferencesKey("prayer_cache_signature")
        val prayerCachePayload = stringPreferencesKey("prayer_cache_payload")
    }
    private fun offsetKey(prayer: String) = intPreferencesKey("offset_$prayer")
    private fun alertKey(prayer: String, field: String) = stringPreferencesKey("alert_${prayer}_$field")
    private fun alertIntKey(prayer: String, field: String) = intPreferencesKey("alert_${prayer}_$field")
    private fun alertBoolKey(prayer: String, field: String) = booleanPreferencesKey("alert_${prayer}_$field")
    private val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    val settings: Flow<SavedSettings> = context.appDataStore.data.map { p ->
        val alerts = prayerNames.associateWith { name ->
            PrayerAlertSettings(
                enabled = p[alertBoolKey(name, "enabled")] ?: true,
                preAlertMinutes = p[alertIntKey(name, "pre")] ?: 0,
                atPrayerTime = p[alertBoolKey(name, "at")] ?: true,
                sound = runCatching { AlertSound.valueOf(p[alertKey(name, "sound")] ?: "TAKBEER") }.getOrDefault(AlertSound.TAKBEER),
                vibration = p[alertBoolKey(name, "vibration")] ?: true,
                volumePercent = p[alertIntKey(name, "volume")] ?: 80,
                repeat = p[alertBoolKey(name, "repeat")] ?: false
            )
        }
        SavedSettings(
            latitude = p[Keys.latitude], longitude = p[Keys.longitude], city = p[Keys.city] ?: "",
            adhkarCount = p[Keys.adhkarCount] ?: 0, adhkarCategory = p[Keys.adhkarCategory] ?: "الصباح",
            adhkarIndex = p[Keys.adhkarIndex] ?: 0, tasbeehCount = p[Keys.tasbeehCount] ?: 0,
            tasbeehTarget = p[Keys.tasbeehTarget] ?: 33, calculationMethod = p[Keys.method] ?: "EGYPT",
            use24Hour = p[Keys.use24Hour] ?: true,
            manualOffsets = prayerNames.associateWith { p[offsetKey(it)] ?: 0 },
            alerts = alerts, hijriDayOffset = p[Keys.hijriOffset] ?: 0,
            showSunrise = p[Keys.showSunrise] ?: true,
            darkMode = p[Keys.darkMode] ?: false,
            themeMode = p[Keys.themeMode] ?: if (p[Keys.darkMode] == true) "DARK" else if (p.contains(Keys.darkMode)) "LIGHT" else "SYSTEM"
        )
    }

    suspend fun saveLocation(latitude: Double, longitude: Double, city: String = "") {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        context.appDataStore.edit { it[Keys.latitude] = latitude; it[Keys.longitude] = longitude; it[Keys.city] = city }
    }
    suspend fun saveCity(city: String) { context.appDataStore.edit { it[Keys.city] = city.trim() } }
    suspend fun saveAdhkarCount(count: Int) { context.appDataStore.edit { it[Keys.adhkarCount] = count.coerceAtLeast(0) } }
    suspend fun saveAdhkarProgress(category: String, index: Int, count: Int) {
        require(category in setOf("الصباح", "المساء", "بعد الصلاة")); require(index >= 0)
        context.appDataStore.edit { it[Keys.adhkarCategory] = category; it[Keys.adhkarIndex] = index; it[Keys.adhkarCount] = count.coerceAtLeast(0) }
    }
    suspend fun saveTasbeeh(count: Int, target: Int) {
        require(target in setOf(33, 34, 99, 100, 1000))
        context.appDataStore.edit { it[Keys.tasbeehCount] = count.coerceAtLeast(0); it[Keys.tasbeehTarget] = target }
    }
    suspend fun saveCalculationMethod(method: String) {
        require(method in setOf("EGYPT", "MWL", "UMM_AL_QURA", "KARACHI", "ISNA", "EGYPT_HANAFI"))
        context.appDataStore.edit { it[Keys.method] = method }
    }
    suspend fun saveClockFormat(use24Hour: Boolean) { context.appDataStore.edit { it[Keys.use24Hour] = use24Hour } }
    suspend fun savePrayerOffset(prayer: String, minutes: Int) {
        require(prayer in prayerNames && minutes in -30..30)
        context.appDataStore.edit { it[offsetKey(prayer)] = minutes }
    }
    suspend fun savePrayerAlert(prayer: String, value: PrayerAlertSettings) {
        require(prayer in prayerNames)
        context.appDataStore.edit {
            it[alertBoolKey(prayer, "enabled")] = value.enabled
            it[alertIntKey(prayer, "pre")] = value.preAlertMinutes
            it[alertBoolKey(prayer, "at")] = value.atPrayerTime
            it[alertKey(prayer, "sound")] = value.sound.name
            it[alertBoolKey(prayer, "vibration")] = value.vibration
            it[alertIntKey(prayer, "volume")] = value.volumePercent
            it[alertBoolKey(prayer, "repeat")] = value.repeat
        }
    }

    suspend fun savePrayerVolume(prayer: String, volumePercent: Int) {
        require(prayer in prayerNames && volumePercent in 0..100)
        context.appDataStore.edit { it[alertIntKey(prayer, "volume")] = volumePercent }
    }

    suspend fun setAllPrayerAlertsEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            prayerNames.forEach { prayer ->
                preferences[alertBoolKey(prayer, "enabled")] = enabled
            }
        }
    }
    suspend fun saveHijriDayOffset(days: Int) { require(days in -3..3); context.appDataStore.edit { it[Keys.hijriOffset] = days } }
    suspend fun saveShowSunrise(show: Boolean) { context.appDataStore.edit { it[Keys.showSunrise] = show } }
    suspend fun saveDarkMode(enabled: Boolean) { context.appDataStore.edit { it[Keys.darkMode] = enabled } }
    suspend fun saveThemeMode(mode: String) {
        require(mode in setOf("SYSTEM", "LIGHT", "DARK"))
        context.appDataStore.edit { it[Keys.themeMode] = mode }
    }

    suspend fun savePrayerTimesCache(signature: String, entries: List<CachedPrayerDay>) {
        require(entries.size == 30)
        val payload = entries.joinToString("\n") { day ->
            listOf(day.date, day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha).joinToString("|")
        }
        context.appDataStore.edit {
            it[Keys.prayerCacheSignature] = signature
            it[Keys.prayerCachePayload] = payload
        }
    }

    suspend fun hasPrayerTimesCache(signature: String, firstDate: String): Boolean {
        val prefs = context.appDataStore.data.map { it }.first()
        if (prefs[Keys.prayerCacheSignature] != signature) return false
        val rows = prefs[Keys.prayerCachePayload]?.lines().orEmpty()
        return rows.size == 30 && rows.firstOrNull()?.substringBefore("|") == firstDate
    }
}

data class CachedPrayerDay(
    val date: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)
