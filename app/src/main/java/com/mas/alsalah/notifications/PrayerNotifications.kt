package com.mas.alsalah.notifications

import android.app.AlarmManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.provider.Settings
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.graphics.BitmapFactory
import com.mas.alsalah.R
import android.speech.tts.TextToSpeech
import android.util.Log
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mas.alsalah.MainActivity
import com.mas.alsalah.core.PrayerCalculator
import com.mas.alsalah.core.PrayerMethod
import com.mas.alsalah.data.AlertSound
import com.mas.alsalah.data.SavedSettings
import com.mas.alsalah.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

private const val EXTRA_PRAYER = "prayer_name"
private const val EXTRA_PRE_ALERT = "is_pre_alert"
private const val EXTRA_REPEAT = "is_repeat"
private const val NOTIFICATION_ID = 5102
private const val ACTION_STOP_ADHAN = "com.mas.alsalah.action.STOP_ADHAN"

object AdhanAudio {
    fun isDoNotDisturbActive(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && manager.isNotificationPolicyAccessGranted) {
            return manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }
        return runCatching {
            Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 0
        }.getOrDefault(false)
    }

    private const val CUSTOM_PREFS = "custom_adhan_preferences"
    private const val SELECTED_ADHAN = "selected_custom_adhan"

    fun selectedCustomAudioPath(context: Context): String? {
        val name = context.getSharedPreferences(CUSTOM_PREFS, Context.MODE_PRIVATE).getString(SELECTED_ADHAN, null)
            ?: return null
        val file = java.io.File(java.io.File(context.filesDir, "custom_adhans"), name)
        return file.takeIf { it.isFile && it.length() > 0L }?.absolutePath
    }

    fun play(context: Context, volumePercent: Int, audioPath: String? = null, prayerName: String = "الصلاة"): Boolean {
        if (isDoNotDisturbActive(context)) return false
        return runCatching {
            val intent = Intent(context, AdhanPlaybackService::class.java)
                .putExtra("adhan_volume", volumePercent.coerceIn(0, 100))
                .putExtra("adhan_custom_path", audioPath ?: selectedCustomAudioPath(context))
            context.startForegroundService(intent)
            true
        }.getOrDefault(false)
    }

    fun stop(context: Context) {
        runCatching {
            context.startService(Intent(context, AdhanPlaybackService::class.java).setAction(ACTION_STOP_ADHAN))
        }
    }
}

class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private val channelId = "adhan_playback_service"
    private val notificationId = 5150

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            player?.let { runCatching { it.stop() }; runCatching { it.release() } }
            player = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (AdhanAudio.isDoNotDisturbActive(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(NotificationChannel(channelId, "تشغيل الأذان", NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعار مؤقت أثناء تشغيل الأذان"
                setSound(null, null)
                enableVibration(false)
            })
        }
        val stopPlayback = PendingIntent.getService(
            this, 5151,
            Intent(this, AdhanPlaybackService::class.java).setAction(ACTION_STOP_ADHAN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle("تشغيل الأذان")
            .setContentText("اضغط إيقاف الأذان لإنهاء التشغيل فورًا")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان", stopPlayback)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notificationId, notification)
        }

        player?.release()
        val media = MediaPlayer()
        player = media
        return try {
            media.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            val customPath = intent?.getStringExtra("adhan_custom_path")
            val customFile = customPath?.let { java.io.File(it) }
            if (customFile != null && customFile.isFile && customFile.length() > 0L &&
                customFile.canonicalPath.startsWith(java.io.File(filesDir, "custom_adhans").canonicalPath + java.io.File.separator)) {
                media.setDataSource(customFile.absolutePath)
            } else {
                resources.openRawResourceFd(R.raw.beautiful_adhan).use { descriptor ->
                    media.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                }
            }
            val volume = (intent?.getIntExtra("adhan_volume", 80) ?: 80).coerceIn(0, 100) / 100f
            media.setVolume(volume, volume)
            media.setOnPreparedListener {
                if (AdhanAudio.isDoNotDisturbActive(this)) {
                    stopSelf(startId)
                } else {
                    it.start()
                }
            }
            media.setOnCompletionListener {
                it.release()
                player = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            media.setOnErrorListener { mp, _, _ ->
                mp.release()
                player = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                true
            }
            media.prepareAsync()
            START_NOT_STICKY
        } catch (_: Exception) {
            runCatching { media.release() }
            player = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

object PrayerNotificationScheduler {
    private const val CHANNEL_PREFIX = "prayer_alert_"
    private const val REQUEST_CODE = 5102

    private data class ScheduledEvent(val prayer: String, val triggerAt: ZonedDateTime, val isPreAlert: Boolean, val isRepeat: Boolean = false)

    fun createChannel(context: Context) {
        createChannel(context, "default", AlertSound.TONE, true)
    }

    private fun createChannel(context: Context, channelId: String, sound: AlertSound, vibration: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = CHANNEL_PREFIX + channelId
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(id, "تنبيهات الصلاة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيهات مستقلة لمواقيت الصلاة"
            enableVibration(vibration)
            if (sound == AlertSound.TONE) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                )
            } else setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun scheduleNext(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = SettingsRepository(context).settings.first()
            val latitude = settings.latitude ?: return@launch
            val longitude = settings.longitude ?: return@launch
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val method = PrayerMethod.values().firstOrNull { it.id == settings.calculationMethod } ?: PrayerMethod.EGYPT
            val candidates = mutableListOf<ScheduledEvent>()
            for (dayOffset in 0..2) {
                val date = LocalDate.now(zone).plusDays(dayOffset.toLong())
                val times = runCatching {
                    PrayerCalculator.calculate(date, latitude, longitude, zone, method.calculation())
                }.getOrNull() ?: continue
                listOf(
                    "الفجر" to times.fajr, "الظهر" to times.dhuhr, "العصر" to times.asr,
                    "المغرب" to times.maghrib, "العشاء" to times.isha
                ).forEach { (name, localTime) ->
                    val alert = settings.alertFor(name)
                    if (!alert.enabled) return@forEach
                    val prayerDateTime = date.atTime(localTime).atZone(zone).plusMinutes(settings.offsetFor(name).toLong())
                    if (alert.preAlertMinutes > 0) {
                        val preTime = prayerDateTime.minusMinutes(alert.preAlertMinutes.toLong())
                        if (preTime.isAfter(now)) candidates += ScheduledEvent(name, preTime, true)
                    }
                    if (alert.atPrayerTime && prayerDateTime.isAfter(now)) {
                        candidates += ScheduledEvent(name, prayerDateTime, false)
                    }
                    // Repeat once, five minutes after the prayer-time alert when enabled.
                    val repeatTime = prayerDateTime.plusMinutes(5)
                    if (alert.enabled && alert.atPrayerTime && alert.repeat && repeatTime.isAfter(now)) {
                        candidates += ScheduledEvent(name, repeatTime, false, true)
                    }
                }
            }

            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val oldPending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, Intent(context, PrayerAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (oldPending != null) alarm.cancel(oldPending)
            val next = candidates.minByOrNull { it.triggerAt.toInstant() } ?: return@launch
            val nextPending = PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, PrayerAlarmReceiver::class.java)
                    .putExtra(EXTRA_PRAYER, next.prayer)
                    .putExtra(EXTRA_PRE_ALERT, next.isPreAlert)
                    .putExtra(EXTRA_REPEAT, next.isRepeat),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerMillis = next.triggerAt.toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, nextPending)
            } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, nextPending)
        }
    }

    internal fun ensureChannel(context: Context, settings: SavedSettings, prayer: String): String {
        val alert = settings.alertFor(prayer)
        val id = "${alert.sound.name.lowercase()}_${if (alert.vibration) "vibrate" else "silent"}"
        createChannel(context, id, alert.sound, alert.vibration)
        return CHANNEL_PREFIX + id
    }
}

class PrayerSpeechService : Service() {
    private var speechEngine: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var started = false
    private var clipPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true
        val phrase = intent?.getStringExtra("speech_text") ?: "اقتربت الصلاة"
        val volume = (intent?.getIntExtra("speech_volume", 100) ?: 100).coerceIn(0, 100) / 100f
        val channelId = "prayer_voice_announcement"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(NotificationChannel(channelId, "الإعلان الصوتي للصلاة", NotificationManager.IMPORTANCE_LOW).apply {
                description = "تشغيل الإعلان الصوتي قبل موعد الصلاة"
                setSound(null, null)
                enableVibration(false)
            })
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle("تنبيه الصلاة الصوتي")
            .setContentText("جارٍ تشغيل التنبيه الصوتي")
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(5190, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(5190, notification)
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        audioManager = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager?.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }

        val clipName = when {
            phrase.contains("الفجر") -> "voice_fajr"
            phrase.contains("الظهر") -> "voice_dhuhr"
            phrase.contains("العصر") -> "voice_asr"
            phrase.contains("المغرب") -> "voice_maghrib"
            phrase.contains("العشاء") -> "voice_isha"
            else -> null
        }
        val clipRes = clipName?.let { resources.getIdentifier(it, "raw", packageName) } ?: 0
        if (clipRes != 0) {
            try {
                val sessionId = audioManager?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE
                val mp = MediaPlayer.create(applicationContext, clipRes, attributes, sessionId)
                if (mp != null) {
                    clipPlayer = mp
                    mp.setVolume(volume, volume)
                    mp.setOnCompletionListener { Log.i("AlSalahVoice", "spoken done (clip)"); stopSelf(startId) }
                    mp.setOnErrorListener { _, _, _ -> Log.e("AlSalahVoice", "clip error"); stopSelf(startId); true }
                    mp.start()
                    Log.i("AlSalahVoice", "speaking (clip $clipName)")
                    return START_NOT_STICKY
                }
            } catch (e: Exception) {
                Log.e("AlSalahVoice", "clip failed, using TTS", e)
            }
        }

        speechEngine = TextToSpeech(applicationContext) { status ->
            val engine = speechEngine
            if (status != TextToSpeech.SUCCESS || engine == null) {
                stopSelf(startId)
                return@TextToSpeech
            }
            engine.setAudioAttributes(attributes)
            var spoken = phrase
            var language = engine.setLanguage(Locale("ar", "EG"))
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                language = engine.setLanguage(Locale("ar"))
            }
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("AlSalahVoice", "Arabic TTS unavailable, using fallback voice")
                spoken = intent?.getStringExtra("speech_fallback") ?: "Prayer time is approaching"
                language = engine.setLanguage(Locale.ENGLISH)
            }
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("AlSalahVoice", "No TTS voice available")
                stopSelf(startId)
                return@TextToSpeech
            }
            engine.setSpeechRate(0.94f)
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                private fun finish() {
                    Handler(Looper.getMainLooper()).post { stopSelf(startId) }
                }
                override fun onStart(utteranceId: String?) { Log.i("AlSalahVoice", "speaking") }
                override fun onDone(utteranceId: String?) { Log.i("AlSalahVoice", "spoken done"); finish() }
                override fun onError(utteranceId: String?) { Log.e("AlSalahVoice", "speech error"); finish() }
            })
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }
            if (engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, "al_salah_voice_$startId") == TextToSpeech.ERROR) {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        clipPlayer?.release()
        clipPlayer = null
        speechEngine?.stop()
        speechEngine?.shutdown()
        speechEngine = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prayer = intent.getStringExtra(EXTRA_PRAYER) ?: "الصلاة"
                val isPreAlert = intent.getBooleanExtra(EXTRA_PRE_ALERT, false)
                val isRepeat = intent.getBooleanExtra(EXTRA_REPEAT, false)
                val settings = SettingsRepository(context).settings.first()
                if (AdhanAudio.isDoNotDisturbActive(context)) {
                    PrayerNotificationScheduler.scheduleNext(context)
                    return@launch
                }
                val alert = settings.alertFor(prayer)
                if (!alert.enabled) return@launch
                val channelId = PrayerNotificationScheduler.ensureChannel(context, settings, prayer)
                val openApp = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val title = when {
                    isRepeat -> "تذكير: صلاة $prayer الآن"
                    isPreAlert -> "اقتربت صلاة $prayer"
                    else -> "صلاة $prayer الآن"
                }
                val message = when (alert.sound) {
                    AlertSound.TAKBEER -> if (isPreAlert) "اقتربت صلاة $prayer" else "الله أكبر، الله أكبر"
                    AlertSound.TONE -> if (isPreAlert) "باقي ${alert.preAlertMinutes} دقيقة على الصلاة" else "حيّ على الصلاة، حيّ على الفلاح"
                    AlertSound.SILENT -> if (isPreAlert) "تذكير صامت قبل موعد الصلاة" else "حان وقت الصلاة"
                }
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_stat_prayer)
                    .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_art))
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openApp)
                    .setOnlyAlertOnce(false)
                    .build()
                try {
                    val id = NOTIFICATION_ID + (prayer.hashCode() and 0x3FFF) + if (isPreAlert) 0x4000 else 0
                    NotificationManagerCompat.from(context).notify(id, notification)
                } catch (_: SecurityException) { }
                PrayerNotificationScheduler.scheduleNext(context)
                // The pre-prayer announcement is always spoken aloud when enabled,
                // regardless of the selected at-prayer sound mode (adhan/tone/silent).
                // The sound selector controls only what happens at the prayer time.
                if (isPreAlert) {
                    runCatching {
                        val speech = Intent(context, PrayerSpeechService::class.java)
                            .putExtra("speech_text", "اقتربت صلاة $prayer")
                            .putExtra("speech_fallback", "Prayer time is approaching")
                            .putExtra("speech_volume", 100)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(speech)
                        else context.startService(speech)
                    }
                } else if (alert.sound == AlertSound.TAKBEER) {
                    AdhanAudio.play(context, 100, AdhanAudio.selectedCustomAudioPath(context), prayer)
                }
            } finally { pendingResult.finish() }
        }
    }


}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) {
            PrayerNotificationScheduler.scheduleNext(context)
        }
    }
}
