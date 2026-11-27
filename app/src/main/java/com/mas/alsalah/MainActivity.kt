package com.mas.alsalah

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import android.net.Uri
import android.provider.Settings
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.location.Geocoder
import android.os.Bundle
import android.os.Build
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mas.alsalah.core.*
import com.mas.alsalah.data.*
import com.mas.alsalah.notifications.PrayerNotificationScheduler
import com.mas.alsalah.notifications.DailyMotivationCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val LuxuryInk = Color(0xFF101A2B)
private val LuxuryMuted = Color(0xFF71829B)
private val LuxurySilver = Color(0xFFFFFFFF)
private val LightCanvas = Color(0xFFF3F7F3)
private val DarkCanvas = Color(0xFF081A15)
private val HeritageGold = Color(0xFF8B7CF6)
private val HeritageNight = Color(0xFFBFEFFF)
private val HeritageCream = Color(0xFFDCEBFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialThemeMode = runCatching {
            runBlocking(Dispatchers.IO) { SettingsRepository(this@MainActivity).settings.first().themeMode }
        }.getOrDefault("SYSTEM")
        val initialDark = when (initialThemeMode) {
            "DARK" -> true
            "LIGHT" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val initialBarColor = if (initialDark) android.graphics.Color.rgb(3, 20, 14) else android.graphics.Color.rgb(237, 245, 240)
        window.statusBarColor = initialBarColor
        window.navigationBarColor = initialBarColor
        window.setBackgroundDrawable(ColorDrawable(initialBarColor))
        window.decorView.systemUiVisibility = if (initialDark) 0 else
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        PrayerNotificationScheduler.createChannel(this)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { App(initialThemeMode) } }
    }

    override fun onResume() {
        super.onResume()
        PrayerNotificationScheduler.scheduleNext(this)
    }
}
private data class EgyptGovernorate(val id: String, val name: String)
private data class EgyptCenter(val governorateId: String, val name: String)
private data class EgyptLocationData(val governorates: List<EgyptGovernorate>, val centers: List<EgyptCenter>)
private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("الرئيسية", Icons.Outlined.Home),
    QIBLA("القبلة", Icons.Outlined.Explore),
    ADHKAR("الأذكار", Icons.Outlined.AutoStories),
    TASBEEH("المسبحة", Icons.Outlined.Fingerprint),
    MORE("المزيد", Icons.Outlined.GridView)
}
private data class Dhikr(val text: String, val repetitions: Int, val eveningText: String? = null, val source: String = "")
private val dhikrList = listOf(
    Dhikr("اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",1),
    Dhikr("قُلْ هُوَ اللَّهُ أَحَدٌ ۝ اللَّهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ",3),
    Dhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ",1,
        "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا"),
    Dhikr("اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ",1,
        "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ"),
    Dhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",100),
    Dhikr("لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",100),
    Dhikr("اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",10),
    Dhikr("أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",100),
    Dhikr("قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۝ مِن شَرِّ مَا خَلَقَ ۝ وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ ۝ وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۝ وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ",3),
    Dhikr("قُلْ أَعُوذُ بِرَبِّ النَّاسِ ۝ مَلِكِ النَّاسِ ۝ إِلَٰهِ النَّاسِ ۝ مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ۝ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ۝ مِنَ الْجِنَّةِ وَالنَّاسِ",3),
    Dhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",3),
    Dhikr("رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا",3),
    Dhikr("حَسْبِيَ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",7),
    Dhikr("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",3),
    Dhikr("اللَّهُمَّ إِنِّي أَصْبَحْتُ أُشْهِدُكَ وَأُشْهِدُ حَمَلَةَ عَرْشِكَ وَمَلَائِكَتَكَ وَجَمِيعَ خَلْقِكَ أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ وَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ",4,
        "اللَّهُمَّ إِنِّي أَمْسَيْتُ أُشْهِدُكَ وَأُشْهِدُ حَمَلَةَ عَرْشِكَ وَمَلَائِكَتَكَ وَجَمِيعَ خَلْقِكَ أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ وَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ", "سنن أبي داود"),
    Dhikr("اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ",1,
        "اللَّهُمَّ مَا أَمْسَى بِي مِنْ نِعْمَةٍ فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ"),
    Dhikr("يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ",1),
    Dhikr("اللَّهُمَّ عَالِمَ الْغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَاوَاتِ وَالْأَرْضِ رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا أَنْتَ",1),
    Dhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ وَرِضَا نَفْسِهِ وَزِنَةَ عَرْشِهِ وَمِدَادَ كَلِمَاتِهِ",3),
    Dhikr("اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",1,
        "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ", "صحيح البخاري"),
    Dhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَيْنِ يَدَيَّ وَمِنْ خَلْفِي وَعَنْ يَمِينِي وَعَنْ شِمَالِي وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي",1,
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَيْنِ يَدَيَّ وَمِنْ خَلْفِي وَعَنْ يَمِينِي وَعَنْ شِمَالِي وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي", "سنن أبي داود"),
    Dhikr("اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَهَ إِلَّا أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ وَالْفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ، لَا إِلَهَ إِلَّا أَنْتَ",3,
        "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَهَ إِلَّا أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ وَالْفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ، لَا إِلَهَ إِلَّا أَنْتَ", "سنن أبي داود")
)
private val afterPrayerDhikr = listOf(
    Dhikr("أَسْتَغْفِرُ اللَّهَ",3),
    Dhikr("اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ",1),
    Dhikr("لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، اللَّهُمَّ لَا مَانِعَ لِمَا أَعْطَيْتَ وَلَا مُعْطِيَ لِمَا مَنَعْتَ وَلَا يَنْفَعُ ذَا الْجَدِّ مِنْكَ الْجَدُّ",1),
    Dhikr("سُبْحَانَ اللَّهِ",33),
    Dhikr("الْحَمْدُ لِلَّهِ",33),
    Dhikr("اللَّهُ أَكْبَرُ",33),
    Dhikr("لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",1)
)
private data class DuaItem(val text: String, val source: String, val category: String)
private val duas = listOf(
    DuaItem("رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ", "سورة البقرة: 201", "قرآني"),
    DuaItem("رَبِّ اشْرَحْ لِي صَدْرِي ۝ وَيَسِّرْ لِي أَمْرِي", "سورة طه: 25–26", "قرآني"),
    DuaItem("رَبَّنَا لَا تُزِغْ قُلُوبَنَا بَعْدَ إِذْ هَدَيْتَنَا وَهَبْ لَنَا مِن لَّدُنكَ رَحْمَةً إِنَّكَ أَنتَ الْوَهَّابُ", "سورة آل عمران: 8", "قرآني"),
    DuaItem("اللَّهُمَّ إِنِّي أَسْأَلُكَ الْهُدَى وَالتُّقَى وَالْعَفَافَ وَالْغِنَى", "صحيح مسلم: 2721", "نبوي"),
    DuaItem("اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ", "سنن أبي داود: 1522", "نبوي"),
    DuaItem("اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ وَالْعَجْزِ وَالْكَسَلِ", "صحيح البخاري: 6369", "نبوي")
)

@Composable private fun App(initialThemeMode: String = "SYSTEM") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: AppViewModel = viewModel()
    val settings by viewModel.settings.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var page by remember { mutableStateOf("") }
    val pageStack = remember { mutableStateListOf<String>() }
    val tabStack = remember { mutableStateListOf<Tab>() }
    var city by remember(settings.city) { mutableStateOf(settings.city) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    val openPage: (String) -> Unit = { destination ->
        if (destination != page) { pageStack.add(page); page = destination }
    }
    val goBack: () -> Unit = {
        if (pageStack.isNotEmpty()) page = pageStack.removeAt(pageStack.lastIndex)
        else if (tabStack.isNotEmpty()) tab = tabStack.removeAt(tabStack.lastIndex)
        else if (tab != Tab.HOME) { tabStack.add(tab); tab = Tab.HOME }
    }
    val selectTab: (Tab) -> Unit = { destination ->
        if (destination != tab) { tabStack.add(tab); tab = destination; pageStack.clear(); page = "" }
    }
    BackHandler(enabled = pageStack.isNotEmpty() || page.isNotEmpty() || tabStack.isNotEmpty() || tab != Tab.HOME) { goBack() }
    val zone = remember { ZoneId.systemDefault() }
    var appNow by remember { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) { appNow = ZonedDateTime.now(zone); delay(60_000) }
    }
    val today = appNow.toLocalDate()
    LaunchedEffect(settings.latitude, settings.longitude, settings.calculationMethod, today, zone.id) {
        viewModel.cachePrayerTimes(settings)
    }
    val latitude = settings.latitude
    val longitude = settings.longitude
    val qiblaDeclination = remember(latitude, longitude) {
        if (latitude != null && longitude != null)
            GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()).declination
        else 0f
    }
    val method = PrayerMethod.values().firstOrNull { it.id == settings.calculationMethod } ?: PrayerMethod.EGYPT
    LaunchedEffect(settings.latitude, settings.longitude, settings.calculationMethod) {
        if (settings.latitude != null && settings.longitude != null) PrayerNotificationScheduler.scheduleNext(context)
    }
    val prayerTimes = remember(settings.latitude, settings.longitude, settings.calculationMethod, today) {
        if (latitude != null && longitude != null)
            runCatching { PrayerCalculator.calculate(today, latitude, longitude, zone, method.calculation()) }.getOrNull()
        else null
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            try {
                LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) scope.launch {
                            locationMessage = "جارٍ تحديد عنوان موقعك..."
                            val resolvedAddress = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (Geocoder.isPresent()) Geocoder(context, Locale("ar", "EG"))
                                        .getFromLocation(loc.latitude, loc.longitude, 1)
                                        ?.firstOrNull()?.let { it.subLocality ?: it.locality ?: it.subAdminArea ?: it.adminArea }
                                    else null
                                }.getOrNull()
                            }
                            val address = resolvedAddress?.takeIf { it.isNotBlank() }
                                ?: "موقعك الحالي"
                            city = address
                            viewModel.saveLocation(loc.latitude, loc.longitude, address)
                            locationMessage = "تم تحديد الموقع: $address"
                        } else locationMessage = "تعذر الحصول على موقعك. شغّل GPS وحاول مرة أخرى."
                    }
            } catch (_: SecurityException) { locationMessage = "لم يتم منح إذن الموقع." }
        } else locationMessage = "لم تسمح بإذن الموقع. يمكنك تفعيله من إعدادات التطبيق."
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) PrayerNotificationScheduler.scheduleNext(context)
        else locationMessage = "الإشعارات متوقفة؛ فعّلها من إعدادات Android حتى تصلك مواقيت الصلاة."
    }
    val enableNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else PrayerNotificationScheduler.scheduleNext(context)
    }
    val enableExactAlarms: () -> Unit = {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
        } else PrayerNotificationScheduler.scheduleNext(context)
    }
    val locate: () -> Unit = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    val systemDark = isSystemInDarkTheme()
    val effectiveThemeMode = if (settings.themeMode == "SYSTEM" && initialThemeMode != "SYSTEM")
        initialThemeMode else settings.themeMode
    val isDark = when (effectiveThemeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }
    val appColors = if (isDark) {
        darkColorScheme(
            primary = Color(0xFFF2CB6B), onPrimary = Color(0xFF2A1D03),
            background = Color(0xFF04140E), surface = Color(0xFF0C2218),
            onSurface = Color(0xFFF6F2E4), surfaceVariant = Color(0xFF16352A),
            onSurfaceVariant = Color(0xFFABC5B7), outline = Color(0x33FFFFFF),
            secondary = Color(0xFF6FE0B0), onSecondary = Color(0xFF04140E),
            secondaryContainer = Color(0xFF1B5E45), onSecondaryContainer = Color(0xFFFFE9A8)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0B6B4A), onPrimary = Color.White, background = Color(0xFFEDF5F0),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0E2A1F),
            surfaceVariant = Color(0xFFDCEBE1), onSurfaceVariant = Color(0xFF4F6B5E),
            outline = Color(0x330B6B4A), secondary = Color(0xFFA67C1F), onSecondary = Color.White,
            secondaryContainer = Color(0xFFCFE9DA), onSecondaryContainer = Color(0xFF0B3D2B)
        )
    }
    MaterialTheme(
        colorScheme = appColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(26.dp)
        )
    ) {
        SideEffect {
            (context as? MainActivity)?.window?.let { window ->
                val bar = if (isDark) android.graphics.Color.rgb(3, 20, 14) else android.graphics.Color.rgb(237, 245, 240)
                window.statusBarColor = bar
                window.navigationBarColor = bar
                window.decorView.systemUiVisibility = if (isDark) 0 else
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        AuroraBackground {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    val activePage = page
                    val activeTab = tab
                    Crossfade(
                        targetState = if (activePage.isEmpty()) "tab:${activeTab.name}" else "page:$activePage",
                        animationSpec = tween(durationMillis = 170),
                        label = "screen-transition"
                    ) { destination ->
                        if (destination.startsWith("page:")) {
                            when (val dest = destination.removePrefix("page:")) {
                                "tasbeeh" -> TasbeehPage(settings.tasbeehCount, settings.tasbeehTarget, viewModel::saveTasbeeh, goBack)
                                "prayer" -> PrayerPage(prayerTimes, locate, settings.use24Hour, settings.showSunrise, latitude, longitude, method, zone, settings.hijriDayOffset)
                                "favorites" -> FavoritesPage(goBack)
                                "about" -> AboutPage(goBack)
                                "calendar" -> CalendarPage(goBack, settings.hijriDayOffset)
                                "duas" -> DuasPage(goBack)
                                "quran" -> QuranSection(open = { openPage("mushaf:$it") }, back = goBack)
                                "settings" -> SettingsPage(
                                    settings, city, { value -> city = value; viewModel.saveCity(value) }, locate,
                                    { id -> viewModel.saveCalculationMethod(id) },
                                    enableNotifications, enableExactAlarms,
                                    { lat, lon, name -> city = name; viewModel.saveLocation(lat, lon, name) },
                                    locationMessage, viewModel::savePrayerAlert, viewModel::savePrayerVolume, viewModel::setAllPrayerAlertsEnabled, viewModel::savePrayerOffset,
                                    viewModel::saveClockFormat, viewModel::saveHijriDayOffset, viewModel::saveShowSunrise,
                                    viewModel::saveThemeMode, goBack
                                )
                                else -> if (dest.startsWith("mushaf:"))
                                    MushafScreen(startPage = dest.removePrefix("mushaf:").toIntOrNull() ?: 1, back = goBack)
                                else MorePage(openPage)
                            }
                        } else {
                            when (Tab.valueOf(destination.removePrefix("tab:"))) {
                                Tab.HOME -> HomePage(settings.city, prayerTimes, locate, { openPage("prayer") }, { selectTab(Tab.ADHKAR) }, latitude, longitude, method, zone, settings.use24Hour, settings.showSunrise)
                                Tab.QIBLA -> QiblaPage(
                                    if (latitude != null && longitude != null) QiblaCalculator.bearing(latitude, longitude).toFloat() else null,
                                    qiblaDeclination
                                )
                                Tab.ADHKAR -> AdhkarPage(settings.adhkarCategory, settings.adhkarIndex, settings.adhkarCount) { category, index, count -> viewModel.saveAdhkarProgress(category, index, count) }
                                Tab.TASBEEH -> TasbeehPage(settings.tasbeehCount, settings.tasbeehTarget, viewModel::saveTasbeeh) { selectTab(Tab.HOME) }
                                Tab.MORE -> MorePage(openPage)
                            }
                        }
                    }
                }
                if (page.isEmpty()) BottomBar(tab, selectTab)
            }
            var splash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(900); splash = false }
            val splashAlpha by animateFloatAsState(if (splash) 1f else 0f, tween(450), label = "splash")
            if (splashAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = splashAlpha }.background(if (isDark) Lux.Night else Color(0xFFEDF5F0)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Image(painterResource(R.drawable.logo_full), contentDescription = null,
                            modifier = Modifier.size(150.dp).clip(RoundedCornerShape(38.dp)), contentScale = ContentScale.Fit)
                        Text("الصلاة", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("AL-SALAH", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, letterSpacing = 4.sp)
                    }
                }
            }
        }
    }
}

private fun rows(p: PrayerCalculation) = listOf("الفجر" to p.fajr, "الشروق" to p.sunrise, "الظهر" to p.dhuhr, "العصر" to p.asr, "المغرب" to p.maghrib, "العشاء" to p.isha)
private fun time(t: java.time.LocalTime, use24Hour: Boolean = true) = t.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "hh:mm a", Locale("ar")))
private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
}
private fun shareText(context: Context, title: String, value: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, value)
    }
    context.startActivity(Intent.createChooser(send, "مشاركة $title"))
}
private fun savedSet(context: Context, key: String): MutableSet<String> =
    context.getSharedPreferences("al_salah_favorites", Context.MODE_PRIVATE)
        .getStringSet(key, emptySet()).orEmpty().toMutableSet()
private fun storeSavedSet(context: Context, key: String, values: Set<String>) {
    context.getSharedPreferences("al_salah_favorites", Context.MODE_PRIVATE)
        .edit().putStringSet(key, values.toSet()).apply()
}

@Composable private fun HeroCountdown(nextPrayerAt: ZonedDateTime, startAt: ZonedDateTime?, use24Hour: Boolean) {
    var now by remember(nextPrayerAt) { mutableStateOf(ZonedDateTime.now(nextPrayerAt.zone)) }
    LaunchedEffect(nextPrayerAt) {
        while (true) {
            now = ZonedDateTime.now(nextPrayerAt.zone)
            delay(1000)
        }
    }
    val remaining = Duration.between(now, nextPrayerAt).seconds.coerceAtLeast(0)
    val total = if (startAt == null) 0L else Duration.between(startAt, nextPrayerAt).seconds
    val progress = if (total <= 0L) 0f else (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        ProgressRing(progress, Modifier.fillMaxSize(), stroke = 7.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%02d:%02d:%02d".format(remaining / 3600, (remaining % 3600) / 60, remaining % 60),
                color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("متبقي على الأذان", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
        }
    }
}

private fun prayerIcon(name: String) = when (name) {
    "الفجر" -> Icons.Outlined.NightsStay
    "الشروق" -> Icons.Outlined.WbTwilight
    "الظهر" -> Icons.Outlined.WbSunny
    "العصر" -> Icons.Outlined.WbCloudy
    "المغرب" -> Icons.Outlined.WbTwilight
    else -> Icons.Outlined.DarkMode
}

@Composable private fun HomePage(
    city: String, prayers: PrayerCalculation?, locate: () -> Unit, openPrayer: () -> Unit, openDhikr: () -> Unit,
    latitude: Double?, longitude: Double?, method: PrayerMethod, zone: ZoneId,
    use24Hour: Boolean, showSunrise: Boolean
) {
    var now by remember { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            now = ZonedDateTime.now(zone)
            delay(60_000)
        }
    }
    val prayerRows = prayers?.let { rows(it).filter { row -> row.first != "الشروق" } }.orEmpty()
    val nextPrayer = remember(prayers, now.toLocalDate(), now.minute, now.hour, latitude, longitude, method) {
        if (prayers == null || latitude == null || longitude == null) null else {
            val upcoming = prayerRows.firstOrNull { it.second.isAfter(now.toLocalTime()) }
            if (upcoming != null) upcoming.first to now.toLocalDate().atTime(upcoming.second).atZone(zone)
            else {
                val tomorrow = now.toLocalDate().plusDays(1)
                val next = PrayerCalculator.calculate(tomorrow, latitude, longitude, zone, method.calculation())
                "الفجر" to tomorrow.atTime(next.fajr).atZone(zone)
            }
        }
    }
    val prevAt: ZonedDateTime? = nextPrayer?.let { np ->
        val d = np.second.toLocalDate()
        val idx = prayerRows.indexOfFirst { it.first == np.first }
        when {
            d != now.toLocalDate() -> prayerRows.lastOrNull()?.let { now.toLocalDate().atTime(it.second).atZone(zone) }
            idx > 0 -> d.atTime(prayerRows[idx - 1].second).atZone(zone)
            else -> prayerRows.lastOrNull()?.let { d.minusDays(1).atTime(it.second).atZone(zone) }
        }
    }
    Page {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.logo_full), contentDescription = "شعار الصلاة",
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Fit)
                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("AL-SALAH", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                    Text("صلاتك في وقتها", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.glass(RoundedCornerShape(50)).clickable(onClick = locate).padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(city.ifBlank { "حدد موقعك" }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Icon(Icons.Outlined.MyLocation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
        Text(now.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.End))
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF117A55), Color(0xFF0A4A35), Color(0xFF052A1E))))
                .border(1.dp, Brush.linearGradient(listOf(Lux.Gold.copy(alpha = 0.9f), Lux.Gold.copy(alpha = 0.10f), Lux.Gold.copy(alpha = 0.55f))), RoundedCornerShape(32.dp))
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-60).dp).size(210.dp)
                    .background(Brush.radialGradient(listOf(Lux.Gold.copy(alpha = 0.34f), Color.Transparent)), CircleShape)
            )
            Image(painterResource(R.drawable.logo_full), contentDescription = null,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = (-34).dp, y = 38.dp).size(150.dp)
                    .graphicsLayer { alpha = 0.10f },
                contentScale = ContentScale.Fit)
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.End) {
                if (nextPrayer != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        HeroCountdown(nextPrayer.second, prevAt, use24Hour)
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("الصلاة القادمة", color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp)
                            Text(nextPrayer.first, color = Lux.GoldSoft, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(time(nextPrayer.second.toLocalTime(), use24Hour), color = Color.White,
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.AccessTime, null, tint = Lux.Gold, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("مواقيت الصلاة", color = Lux.GoldSoft, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                            Text("حدد موقعك لحساب المواقيت بدقة", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GoldButton("تحديد الموقع", locate, Modifier.fillMaxWidth(), Icons.Outlined.MyLocation)
                }
            }
        }
        SectionTitle("مواقيت اليوم", "عرض الكل", openPrayer)
        if (prayers == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("فعّل الموقع لعرض مواقيت الصلاة المحسوبة لمكانك.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                GoldButton("تحديد الموقع الآن", locate, Modifier.fillMaxWidth())
            }
        } else {
            val shown = rows(prayers).filter { showSunrise || it.first != "الشروق" }
            val nowTime = now.toLocalTime()
            shown.forEach { row ->
                val isNext = row.first == nextPrayer?.first
                val passed = !isNext && !row.second.isAfter(nowTime)
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), isNext)
                        .padding(horizontal = 16.dp, vertical = 12.dp).graphicsLayer { alpha = if (passed) 0.55f else 1f },
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(time(row.second, use24Hour),
                        color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isNext) Text("القادمة", color = Lux.OnGold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Lux.goldBrush).padding(horizontal = 9.dp, vertical = 3.dp))
                        Text(row.first, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp,
                            fontWeight = if (isNext) FontWeight.ExtraBold else FontWeight.SemiBold)
                        IconBadge(prayerIcon(row.first), size = 38.dp)
                    }
                }
            }
        }
        SectionTitle("اختصاراتك")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile("الأذكار", "وردك اليومي", Icons.Outlined.AutoStories, Modifier.weight(1f), openDhikr)
            QuickTile("مواقيت الصلاة", "جدول الشهر", Icons.Outlined.CalendarMonth, Modifier.weight(1f), openPrayer)
        }
        DailyMotivationCard()
        Text("﴿ أَقِمِ الصَّلَاةَ لِذِكْرِي ﴾", color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = TextAlign.Center)
    }
}


@Composable private fun PrayerPage(
    prayers: PrayerCalculation?, locate: () -> Unit, use24Hour: Boolean, showSunrise: Boolean,
    latitude: Double?, longitude: Double?, method: PrayerMethod, zone: ZoneId, hijriOffset: Int
) {
    var monthStart by remember { mutableStateOf(HijrahDate.now().plus(hijriOffset.toLong(), ChronoUnit.DAYS).with(ChronoField.DAY_OF_MONTH, 1)) }
    val monthDays = remember(monthStart, latitude, longitude, method, zone, showSunrise) {
        (1..monthStart.lengthOfMonth()).map { day ->
            val hijri = monthStart.with(ChronoField.DAY_OF_MONTH, day.toLong())
            val gregorian = LocalDate.from(hijri)
            val times = if (latitude != null && longitude != null)
                runCatching { PrayerCalculator.calculate(gregorian, latitude, longitude, zone, method.calculation()) }.getOrNull()
            else null
            Triple(hijri, gregorian, times)
        }
    }
    val todayDate = LocalDate.now()
    Page {
        Header("مواقيت الصلاة", "جدول الشهر الهجري كاملًا")
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Outlined.ChevronLeft, "الشهر السابق",
                { monthStart = monthStart.minus(1, ChronoUnit.MONTHS).with(ChronoField.DAY_OF_MONTH, 1) }, size = 40.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)
                Text("الشهر الهجري", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            GlassIconButton(Icons.Outlined.ChevronRight, "الشهر التالي",
                { monthStart = monthStart.plus(1, ChronoUnit.MONTHS).with(ChronoField.DAY_OF_MONTH, 1) }, size = 40.dp)
        }
        if (latitude == null || longitude == null) GlassCard(Modifier.fillMaxWidth()) {
            Text("نحتاج إلى موقعك لحساب مواقيت الشهر بالكامل.", color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
            GoldButton("تحديد الموقع", locate, Modifier.fillMaxWidth(), Icons.Outlined.MyLocation)
        } else {
            monthDays.forEach { (hijri, gregorian, times) ->
                val isToday = gregorian == todayDate
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), isToday).padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(gregorian.format(DateTimeFormatter.ofPattern("EEE d/M", Locale("ar"))), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isToday) Text("اليوم", color = Lux.OnGold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Lux.goldBrush).padding(horizontal = 9.dp, vertical = 3.dp))
                            Text(hijri.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ar"))), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (times != null) {
                        val visibleRows = rows(times).filter { showSunrise || it.first != "الشروق" }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            visibleRows.forEach { (name, timeValue) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(time(timeValue, use24Hour), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    } else Text("تعذر حساب المواقيت لهذا اليوم", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text("المواقيت محسوبة حسب موقعك وطريقة الحساب المحددة في الإعدادات.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.End)
    }
}

@Composable private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier.glass(RoundedCornerShape(22.dp), accent).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable private fun ColumnScope.QiblaBody(relativeQibla: Float, correctedAzimuth: Float, bearing: Float?, azimuth: Float) {
    val aligned = !relativeQibla.isNaN() && kotlin.math.abs(relativeQibla) < 4f
    GlassCard(Modifier.fillMaxWidth(), emphasis = aligned) {
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(288.dp).graphicsLayer { rotationZ = if (correctedAzimuth.isNaN()) 0f else -correctedAzimuth },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0x3312A36B), Color(0x0A12A36B)), center = Offset(cx, cy), radius = r),
                        radius = r, center = Offset(cx, cy)
                    )
                    drawCircle(color = Lux.Gold.copy(alpha = 0.6f), radius = r - 2.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    drawCircle(color = Color.White.copy(alpha = 0.12f), radius = r * 0.60f, center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
                    val outer = r - 8.dp.toPx()
                    for (tick in 0 until 72) {
                        val angle = Math.toRadians(tick * 5.0)
                        val major = tick % 6 == 0
                        val inner = outer - if (major) 15.dp.toPx() else 7.dp.toPx()
                        drawLine(
                            color = if (major) Lux.Gold else Color.White.copy(alpha = 0.32f),
                            start = Offset(cx + kotlin.math.sin(angle).toFloat() * inner, cy - kotlin.math.cos(angle).toFloat() * inner),
                            end = Offset(cx + kotlin.math.sin(angle).toFloat() * outer, cy - kotlin.math.cos(angle).toFloat() * outer),
                            strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx()
                        )
                    }
                }
                Text("شمال", color = Lux.Gold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
                Text("جنوب", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
                Text("شرق", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp))
                Text("غرب", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 30.dp))
            }
            Icon(
                Icons.Outlined.Navigation, "اتجاه القبلة",
                tint = if (aligned) Lux.Emerald else Lux.Gold,
                modifier = Modifier.size(112.dp).graphicsLayer { rotationZ = if (relativeQibla.isNaN()) 0f else relativeQibla }
            )
            Box(Modifier.size(20.dp).clip(CircleShape).background(Lux.goldBrush))
        }
        Text(
            if (bearing == null) "حدد موقعك لحساب اتجاه القبلة"
            else if (azimuth.isNaN()) "فعّل مستشعر البوصلة أو تحقق من الهاتف"
            else if (aligned) "أنت الآن في اتجاه القبلة ✓"
            else "حرّك الهاتف حتى يتجه المؤشر نحو القبلة",
            color = if (aligned) Lux.OnGold else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                .then(if (aligned) Modifier.background(Lux.goldBrush) else Modifier.glass(RoundedCornerShape(50)))
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(if (bearing == null) "--°" else "${bearing.roundToInt()}°", "زاوية القبلة من الشمال", Modifier.weight(1f), true)
        StatTile(if (correctedAzimuth.isNaN()) "--°" else "${correctedAzimuth.roundToInt()}°", "اتجاه الهاتف", Modifier.weight(1f))
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Info, size = 40.dp)
            Text("لأفضل دقة: أبعد الهاتف عن المعادن والمغناطيس، وعاير البوصلة بتحريك الهاتف على شكل رقم 8.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable private fun AdhkarPage(savedCategory: String, savedIndex: Int, savedCount: Int, save: (String, Int, Int) -> Unit) {
    var category by remember(savedCategory) { mutableStateOf(savedCategory) }
    var index by remember(savedCategory, savedIndex) { mutableIntStateOf(savedIndex) }
    var count by remember(savedCategory, savedIndex, savedCount) { mutableIntStateOf(savedCount) }
    val context = LocalContext.current
    val favoriteId = "$category:$index"
    val favoritesKey = "adhkar"
    var favorite by remember(favoriteId) { mutableStateOf(savedSet(context, favoritesKey).contains(favoriteId)) }
    val activeList = when (category) {
        "بعد الصلاة" -> afterPrayerDhikr
        else -> dhikrList
    }
    val safeIndex = index.coerceIn(activeList.indices)
    val item = activeList[safeIndex]
    val text = if (category == "المساء") item.eveningText ?: item.text else item.text
    val tap: () -> Unit = {
        if (count + 1 >= item.repetitions) {
            count = 0
            index = if (safeIndex < activeList.lastIndex) safeIndex + 1 else 0
        } else count++
        save(category, index, count)
    }
    Page {
        Header("الأذكار", "ألا بذكر الله تطمئن القلوب")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("بعد الصلاة", "المساء", "الصباح").forEach { name ->
                PillTab(name, category == name, {
                    category = name; index = 0; count = 0; save(name, 0, 0)
                }, Modifier.weight(1f))
            }
        }
        GlassCard(Modifier.fillMaxWidth(), emphasis = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("التكرار ${item.repetitions}", color = Lux.OnGold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Lux.goldBrush).padding(horizontal = 11.dp, vertical = 4.dp))
                Text("الذكر " + (safeIndex + 1) + " من " + activeList.size, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, lineHeight = 40.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
            if (item.source.isNotBlank()) Text("المصدر: " + item.source, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Box(Modifier.align(Alignment.CenterHorizontally).size(190.dp).clip(CircleShape).clickable(onClick = tap), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().glass(CircleShape))
            ProgressRing((count.toFloat() / item.repetitions.coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.fillMaxSize(), stroke = 9.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", color = MaterialTheme.colorScheme.onSurface, fontSize = 50.sp, fontWeight = FontWeight.ExtraBold)
                Text("اضغط للتسبيح", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Outlined.SkipNext, "تخطي الذكر", { index = (safeIndex + 1) % activeList.size; count = 0; save(category, index, 0) })
            GlassIconButton(Icons.Outlined.Refresh, "تصفير العداد", { count = 0; save(category, index, 0) })
            GlassIconButton(Icons.Outlined.Share, "مشاركة الذكر", { shareText(context, "الذكر", text) })
            GlassIconButton(Icons.Outlined.ContentCopy, "نسخ الذكر", { copyToClipboard(context, "الذكر", text) })
            GlassIconButton(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "إضافة للمفضلة", {
                val values = savedSet(context, favoritesKey)
                if (favorite) values.remove(favoriteId) else values.add(favoriteId)
                storeSavedSet(context, favoritesKey, values)
                favorite = !favorite
            }, tint = if (favorite) Lux.Gold else Color.Unspecified, active = favorite)
        }
    }
}

@Composable private fun MorePage(open: (String) -> Unit) {
    val entries = listOf(
        Triple("القرآن الكريم", "المصحف الشريف بدون إنترنت", Icons.Outlined.MenuBook) to "quran",
        Triple("التقويم الهجري", "التاريخ والمناسبات", Icons.Outlined.CalendarMonth) to "calendar",
        Triple("مواقيت الصلاة", "جدول الشهر الهجري", Icons.Outlined.AccessTime) to "prayer",
        Triple("الأدعية", "من القرآن والسنة", Icons.Outlined.FavoriteBorder) to "duas",
        Triple("السبحة", "عداد الذكر", Icons.Outlined.Fingerprint) to "tasbeeh",
        Triple("المفضلة", "ما حفظته", Icons.Outlined.Favorite) to "favorites",
        Triple("الإعدادات", "خصص تجربتك", Icons.Outlined.Settings) to "settings",
        Triple("حول التطبيق", "AL-SALAH by MAS", Icons.Outlined.Info) to "about"
    )
    Page {
        Header("اكتشف المزيد", "أدواتك اليومية في مكان واحد")
        entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (entry, key) ->
                    FeatureTile(entry.first, entry.second, entry.third, Modifier.weight(1f)) { open(key) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun QiblaPage(bearing: Float?, declination: Float) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(Float.NaN) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        var gravity: FloatArray? = null
        var magnetic: FloatArray? = null
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity = event.values.clone()
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magnetic = event.values.clone()
                val g = gravity ?: return
                val m = magnetic ?: return
                if (SensorManager.getRotationMatrix(rotationMatrix, null, g, m)) {
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val heading = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                    val previous = azimuth
                    if (previous.isNaN()) azimuth = heading
                    else {
                        val delta = ((heading - previous + 540f) % 360f) - 180f
                        if (kotlin.math.abs(delta) >= 0.35f) azimuth = (previous + delta * 0.24f + 360f) % 360f
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
    val correctedAzimuth = if (azimuth.isNaN()) Float.NaN else (azimuth + declination + 360f) % 360f
    val relativeQibla = if (bearing == null || correctedAzimuth.isNaN()) Float.NaN
        else ((bearing - correctedAzimuth + 540f) % 360f) - 180f
    Page {
        Header("اتجاه القبلة", "بوصلة تفاعلية لتحديد اتجاه الكعبة")
        QiblaBody(relativeQibla, correctedAzimuth, bearing, azimuth)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsPage(
    settings: SavedSettings, city: String, onCity: (String) -> Unit, locate: () -> Unit,
    setMethod: (String) -> Unit, enableNotifications: () -> Unit, enableExactAlarms: () -> Unit,
    saveManualLocation: (Double, Double, String) -> Unit, locationMessage: String?,
    saveAlert: (String, PrayerAlertSettings) -> Unit, saveVolume: (String, Int) -> Unit, setAllPrayerAlertsEnabled: (Boolean) -> Unit, saveOffset: (String, Int) -> Unit,
    saveClockFormat: (Boolean) -> Unit, saveHijriOffset: (Int) -> Unit,
    saveShowSunrise: (Boolean) -> Unit, saveThemeMode: (String) -> Unit, back: () -> Unit
) {
    var section by remember { mutableStateOf("home") }
    var latitudeText by remember(settings.latitude) { mutableStateOf(settings.latitude?.toString() ?: "") }
    var longitudeText by remember(settings.longitude) { mutableStateOf(settings.longitude?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var permissionRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    val systemNotificationManager = remember(context) {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    val notificationsAllowed = remember(permissionRefresh) {
        systemNotificationManager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                context.packageManager.checkPermission(Manifest.permission.POST_NOTIFICATIONS, context.packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val exactAlarmsAllowed = remember(permissionRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }
    val scope = rememberCoroutineScope()
    val prayers = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
    val title = when(section) {
        "location" -> "الموقع والمدينة"; "prayers" -> "مواقيت الصلاة"; "alerts" -> "تنبيهات الصلاة"
        "appearance" -> "المظهر والعرض"; "calendar" -> "التقويم الهجري"; "system" -> "أذونات التطبيق"; else -> "الإعدادات"
    }
    Page {
        Header(title, if(section=="home") "كل إعداد في مكانه.. براحة ووضوح" else "تحكم في التفاصيل واحفظ تفضيلاتك",
            if(section=="home") back else ({ section="home" }))
        Spacer(Modifier.height(16.dp))
        if(section=="home") {
            Text("إعداداتك", color=MaterialTheme.colorScheme.onSurface, fontSize=19.sp, fontWeight=FontWeight.ExtraBold)
            val entries=listOf(
                Triple("location","الموقع والمدينة","العنوان الحالي وتحديد الموقع بدقة") to Icons.Outlined.LocationOn,
                Triple("prayers","مواقيت الصلاة","طريقة الحساب وتنسيق الوقت والشروق") to Icons.Outlined.AccessTime,
                Triple("alerts","تنبيهات الصلاة","الأذان والتنبيه الصوتي قبل الصلاة") to Icons.Outlined.NotificationsActive,
                Triple("appearance","المظهر والعرض","الوضع الليلي وطريقة العرض") to Icons.Outlined.Palette,
                Triple("calendar","التقويم الهجري","ضبط التاريخ حسب بلدك") to Icons.Outlined.CalendarMonth,
                Triple("system","أذونات التطبيق","الإشعارات والمنبهات الدقيقة") to Icons.Outlined.Tune
            )
            entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (entry, icon) ->
                        FeatureTile(entry.second, entry.third, icon, Modifier.weight(1f)) { section = entry.first }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("ملخص سريع",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                SettingInfoRow("الموقع",if(settings.latitude!=null) settings.city.ifBlank{"تم حفظ الإحداثيات"} else "لم يتم تحديده")
                SettingInfoRow("طريقة الحساب",PrayerMethod.values().firstOrNull{it.id==settings.calculationMethod}?.titleArabic ?: "مصر")
            }
        } else if(section=="location") {
            val locationData = remember { mutableStateOf<EgyptLocationData?>(null) }
            LaunchedEffect(Unit) {
                if (locationData.value == null) {
                    val loaded = withContext(Dispatchers.IO) {
                        runCatching {
                            val root = org.json.JSONObject(context.assets.open("egypt_locations.json").bufferedReader().use { it.readText() })
                            val governorates = root.getJSONArray("governorates").let { array ->
                                (0 until array.length()).map { index ->
                                    val item = array.getJSONObject(index)
                                    EgyptGovernorate(item.getString("id"), item.getString("name"))
                                }
                            }
                            val centers = root.getJSONArray("centers").let { array ->
                                (0 until array.length()).map { index ->
                                    val item = array.getJSONObject(index)
                                    EgyptCenter(item.getString("governorateId"), item.getString("name"))
                                }
                            }
                            EgyptLocationData(governorates, centers)
                        }.getOrNull()
                    }
                    locationData.value = loaded
                }
            }
            var manualLocationStatus by remember { mutableStateOf<String?>(null) }
            var selectedGovernorate by remember { mutableStateOf("") }
            var selectedCenter by remember { mutableStateOf("") }
            var governorateExpanded by remember { mutableStateOf(false) }
            var centerExpanded by remember { mutableStateOf(false) }
            val data = locationData.value
            val selectedGov = data?.governorates?.firstOrNull { it.id == selectedGovernorate }
            val availableCenters = remember(data, selectedGovernorate) {
                data?.centers?.asSequence()?.filter { it.governorateId == selectedGovernorate }
                    ?.map { it.name }?.distinct()?.sorted()?.toList().orEmpty()
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("الموقع الحالي",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                Text(if(settings.latitude==null) "لم يتم تحديد موقع بعد" else settings.city.ifBlank { "تم تحديد الموقع" },
                    color=MaterialTheme.colorScheme.primary,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                locationMessage?.let{Text(it,color=MaterialTheme.colorScheme.primary,fontSize=12.sp)}
                manualLocationStatus?.let{Text(it,color=MaterialTheme.colorScheme.primary,fontSize=12.sp)}
                Button(onClick=locate,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.MyLocation,null);Spacer(Modifier.width(8.dp));Text("تحديد موقعي تلقائيًا")
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("تحديد الموقع يدويًا",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                Text("اختر المحافظة ثم المركز أو المدينة. سيتم تحديد الإحداثيات بدقة من اسم المكان.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                if (data == null) {
                    CircularProgressIndicator(modifier=Modifier.align(Alignment.CenterHorizontally))
                    Text("جارٍ تحميل بيانات محافظات ومراكز مصر...",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                } else {
                    ExposedDropdownMenuBox(expanded=governorateExpanded,onExpandedChange={governorateExpanded=it}) {
                        OutlinedTextField(
                            value=selectedGov?.name.orEmpty(),onValueChange={},readOnly=true,
                            modifier=Modifier.menuAnchor().fillMaxWidth(),
                            label={Text("المحافظة")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(governorateExpanded)},
                            singleLine=true
                        )
                        ExposedDropdownMenu(expanded=governorateExpanded,onDismissRequest={governorateExpanded=false}) {
                            data.governorates.forEach { governorate ->
                                DropdownMenuItem(text={Text(governorate.name)},onClick={
                                    selectedGovernorate=governorate.id
                                    selectedCenter=""
                                    governorateExpanded=false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ExposedDropdownMenuBox(expanded=centerExpanded,onExpandedChange={centerExpanded=it && selectedGovernorate.isNotBlank()}) {
                        OutlinedTextField(
                            value=selectedCenter,onValueChange={},readOnly=true,
                            enabled=selectedGovernorate.isNotBlank(),
                            modifier=Modifier.menuAnchor().fillMaxWidth(),
                            label={Text("المركز / المدينة")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(centerExpanded)},
                            singleLine=true
                        )
                        ExposedDropdownMenu(expanded=centerExpanded,onDismissRequest={centerExpanded=false}) {
                            availableCenters.forEach { center ->
                                DropdownMenuItem(text={Text(center)},onClick={selectedCenter=center;centerExpanded=false})
                            }
                        }
                    }
                    Button(
                        onClick={
                            if(selectedGov==null || selectedCenter.isBlank()) {
                                error="اختار المحافظة والمركز أولاً"
                            } else {
                                error=null
                                scope.launch {
                                    manualLocationStatus="جارٍ تحديد إحداثيات $selectedCenter..."
                                    val result=withContext(Dispatchers.IO) {
                                        runCatching {
                                            if(!Geocoder.isPresent()) null
                                            else Geocoder(context,Locale("ar","EG"))
                                                .getFromLocationName("$selectedCenter، ${selectedGov.name}، مصر",5)
                                                ?.firstOrNull()
                                        }.getOrNull()
                                    }
                                    if(result!=null) {
                                        val displayName="$selectedCenter، ${selectedGov.name}"
                                        saveManualLocation(result.latitude,result.longitude,displayName)
                                        manualLocationStatus="تم حفظ موقع $displayName بنجاح"
                                    } else {
                                        manualLocationStatus="تعذر تحديد إحداثيات هذا المركز تلقائيًا. جرّب اختيار الموقع الحالي أو أعد المحاولة."
                                    }
                                }
                            }
                        },
                        enabled=selectedGovernorate.isNotBlank() && selectedCenter.isNotBlank(),
                        modifier=Modifier.fillMaxWidth()
                    ) { Text("حفظ الموقع المختار") }
                }
                error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                Text("بيانات المحافظات والمراكز متاحة داخل التطبيق وتعمل دون إنترنت. يلزم اتصال عند تحويل الاسم المختار إلى إحداثيات دقيقة.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)
            }
        } else if(section=="prayers") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("طريقة حساب مواقيت الصلاة",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                PrayerMethod.values().forEach{m->Row(Modifier.fillMaxWidth().clickable{setMethod(m.id)}.padding(vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){RadioButton(settings.calculationMethod==m.id,{setMethod(m.id)});Text(m.titleArabic,color=MaterialTheme.colorScheme.onSurface)}}
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("تنسيق المواقيت",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                SettingSwitchRow("نظام 24 ساعة",settings.use24Hour,saveClockFormat)
                SettingSwitchRow("إظهار وقت الشروق",settings.showSunrise,saveShowSunrise)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("ضبط يدوي لموعد الصلاة",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                prayers.forEach{p->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    OutlinedButton(onClick={saveOffset(p,(settings.offsetFor(p)-1).coerceAtLeast(-30))}){Text("−")}
                    Text("$p • ${settings.offsetFor(p)} دقيقة",color=MaterialTheme.colorScheme.onSurface,fontSize=12.sp)
                    OutlinedButton(onClick={saveOffset(p,(settings.offsetFor(p)+1).coerceAtMost(30))}){Text("+")}
                }}
            }
        } else if(section=="alerts") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(13.dp).size(25.dp))
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("تنبيهات الصلاة والأذان", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("صوت الأذان في موعد الصلاة، وإعلان صوتي باسم الصلاة قبلها بالمدة التي تختارها.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End)
                        }
                    }
                    SettingSwitchRow("تفعيل تنبيهات الصلوات", prayers.any { settings.alertFor(it).enabled },
                        { setAllPrayerAlertsEnabled(it) })
                }

                val allPrayerAlertsEnabled = prayers.all { settings.alertFor(it).enabled }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { setAllPrayerAlertsEnabled(!allPrayerAlertsEnabled) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor =
                                if (allPrayerAlertsEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                            Text(if (allPrayerAlertsEnabled) "إيقاف الكل" else "تشغيل الكل")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("التحكم في الصلوات الخمس", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            Text("يمكن تخصيص كل صلاة بشكل مستقل", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp)
                        }
                    }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Button(onClick = {
                        runCatching {
                            val i = android.content.Intent(ctx, com.mas.alsalah.notifications.PrayerSpeechService::class.java)
                                .putExtra("speech_text", "اقتربت صلاة العصر")
                                .putExtra("speech_fallback", "Prayer time is approaching")
                                .putExtra("speech_volume", 100)
                            ctx.startForegroundService(i)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("تجربة التنبيه الصوتي \"اقتربت الصلاة\"") }
                }

                prayers.forEach { prayer ->
                    val alert = settings.alertFor(prayer)
                    var custom by remember(prayer, alert.preAlertMinutes) {
                        mutableStateOf(if (alert.preAlertMinutes !in listOf(0, 5, 10, 15, 20, 30))
                            alert.preAlertMinutes.toString() else "")
                    }
                    val customValue = custom.toIntOrNull()
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Switch(checked = alert.enabled,
                                onCheckedChange = { saveAlert(prayer, alert.copy(enabled = it, sound = AlertSound.TAKBEER)) })
                            Column(horizontalAlignment = Alignment.End) {
                                Text("صلاة $prayer", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(if (alert.enabled) "التنبيه مفعّل" else "التنبيه متوقف",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingSwitchRow("إشعار عند دخول وقت الصلاة", alert.atPrayerTime,
                            { saveAlert(prayer, alert.copy(atPrayerTime = it, sound = AlertSound.TAKBEER)) }, alert.enabled)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("التذكير قبل الصلاة", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0, 5, 10, 15, 20, 30).forEach { minutes ->
                                    FilterChip(selected = alert.preAlertMinutes == minutes,
                                        onClick = { saveAlert(prayer, alert.copy(preAlertMinutes = minutes, sound = AlertSound.TAKBEER)) },
                                        enabled = alert.enabled,
                                        label = { Text(if (minutes == 0) "إيقاف" else "$minutes د", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = custom,
                                    onValueChange = { custom = it.filter(Char::isDigit).take(2) },
                                    modifier = Modifier.weight(1f), enabled = alert.enabled, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), label = { Text("مدة مخصصة بالدقائق") },
                                    placeholder = { Text("1–60") })
                                OutlinedButton(onClick = {
                                    customValue?.let { saveAlert(prayer, alert.copy(preAlertMinutes = it, sound = AlertSound.TAKBEER)) }
                                }, enabled = alert.enabled && customValue != null && customValue in 1..60) {
                                    Text("حفظ")
                                }
                            }
                        }
                        SettingSwitchRow("اهتزاز الإشعار", alert.vibration,
                            { saveAlert(prayer, alert.copy(vibration = it, sound = AlertSound.TAKBEER)) }, alert.enabled)
                        SettingSwitchRow("تكرار الإشعار بعد 5 دقائق", alert.repeat,
                            { saveAlert(prayer, alert.copy(repeat = it, sound = AlertSound.TAKBEER)) }, alert.enabled)
                    }
                }
                CustomAdhanManager()
            }
        } else if(section=="appearance") {
            GlassCard(Modifier.fillMaxWidth()){
                Text("مظهر التطبيق",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("SYSTEM" to "تلقائي","LIGHT" to "فاتح","DARK" to "داكن").forEach{(mode,label)->FilterChip(settings.themeMode==mode,{saveThemeMode(mode)},label={Text(label)},modifier=Modifier.weight(1f) )}}
            }
        } else if(section=="calendar") {
            GlassCard(Modifier.fillMaxWidth()){
                Text("تعديل التاريخ الهجري",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                Text("فرق الأيام: ${settings.hijriDayOffset}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OutlinedButton(onClick={saveHijriOffset((settings.hijriDayOffset-1).coerceAtLeast(-3))},modifier=Modifier.weight(1f)){Text("− يوم")}
                    OutlinedButton(onClick={saveHijriOffset((settings.hijriDayOffset+1).coerceAtMost(3))},modifier=Modifier.weight(1f)){Text("+ يوم")}
                }
            }
        } else if(section=="system") {
            GlassCard(Modifier.fillMaxWidth()){
                Text("أذونات التشغيل",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                PermissionStatusRow(
                    title = "إشعارات الصلاة",
                    enabled = notificationsAllowed,
                    enabledLabel = "الإشعارات مفعّلة",
                    disabledLabel = "الإشعارات متوقفة",
                    actionLabel = if (notificationsAllowed) "مفعّل ✓" else "تفعيل الإشعارات",
                    onClick = enableNotifications
                )
                PermissionStatusRow(
                    title = "المنبهات الدقيقة",
                    enabled = exactAlarmsAllowed,
                    enabledLabel = "السماح بالمنبهات الدقيقة مفعّل",
                    disabledLabel = "السماح بالمنبهات الدقيقة غير مفعّل",
                    actionLabel = if (exactAlarmsAllowed) "مفعّل ✓" else "تفعيل المنبهات الدقيقة",
                    onClick = enableExactAlarms
                )
                OutlinedButton(
                    onClick={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))},
                    modifier=Modifier.fillMaxWidth()
                ) {
                    Icon(if (notificationsAllowed) Icons.Outlined.CheckCircle else Icons.Outlined.NotificationsOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (notificationsAllowed) "إعدادات إشعارات Android • مفعّلة" else "إعدادات إشعارات Android • تحتاج تفعيل")
                }
                Text(
                    "يتم تحديث حالة الأذونات تلقائيًا عند الرجوع من إعدادات Android.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp, textAlign=TextAlign.End
                )
            }
        }
    }
}
@Composable
private fun PermissionStatusRow(
    title: String,
    enabled: Boolean,
    enabledLabel: String,
    disabledLabel: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(if (enabled) enabledLabel else disabledLabel, fontSize = 11.sp,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        if (!enabled) {
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Check, null)
                Spacer(Modifier.width(7.dp))
                Text(actionLabel)
            }
        } else {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = false) {
                Icon(Icons.Outlined.CheckCircle, null)
                Spacer(Modifier.width(7.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}








@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.glass(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconBadge(icon, size = 44.dp)
        Spacer(Modifier.height(4.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val dark = isDarkTheme()
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(shape)
            .background(if (dark) Color(0xEE07190F) else Color(0xF2FFFFFF))
            .border(1.dp, Brush.linearGradient(listOf(Lux.Gold.copy(alpha = if (dark) 0.5f else 0.7f), Color.White.copy(alpha = 0.06f), Lux.Gold.copy(alpha = 0.3f))), shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().reversed().forEach { item ->
            val active = item == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                    .then(if (active) Modifier.background(Lux.goldBrush) else Modifier)
                    .clickable { onSelect(item) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.icon, contentDescription = item.label,
                    tint = if (active) Lux.OnGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
                Text(item.label, fontSize = 10.sp, maxLines = 1,
                    color = if (active) Lux.OnGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TasbeehPage(initialCount: Int, target: Int, save: (Int, Int) -> Unit, back: () -> Unit) {
    val adhkar = listOf(
        "سُبْحَانَ اللَّهِ" to 33, "الْحَمْدُ لِلَّهِ" to 33, "اللَّهُ أَكْبَرُ" to 34,
        "لَا إِلَهَ إِلَّا اللَّهُ" to 100, "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ" to 100,
        "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ" to 100,
        "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ" to 100,
        "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ" to 100
    )
    var index by remember { mutableIntStateOf(0) }
    var count by remember(initialCount) { mutableIntStateOf(initialCount.coerceAtLeast(0)) }
    var goal by remember(target) { mutableIntStateOf(target.coerceIn(1, 1000)) }
    var completedRounds by remember { mutableIntStateOf(0) }
    val current = adhkar[index]
    val context = LocalContext.current
    val tap: () -> Unit = {
        if (count + 1 >= goal) {
            count = 0
            completedRounds++
            index = (index + 1) % adhkar.size
            save(0, goal)
            Toast.makeText(context, "اكتمل الذكر، انتقلنا إلى الذكر التالي", Toast.LENGTH_SHORT).show()
        } else {
            count++
            save(count, goal)
        }
    }
    Page {
        Header("السبحة الإلكترونية", "ورد متنوع وعدّاد يحفظ تقدمك", back)
        GlassCard(Modifier.fillMaxWidth(), emphasis = true) {
            Text("الذكر الحالي", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(current.first, color = MaterialTheme.colorScheme.primary, fontSize = 25.sp,
                lineHeight = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        Box(Modifier.align(Alignment.CenterHorizontally).size(240.dp).clip(CircleShape).clickable(onClick = tap), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().glass(CircleShape, true))
            ProgressRing((count.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.fillMaxSize(), stroke = 11.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", color = MaterialTheme.colorScheme.onSurface, fontSize = 68.sp, fontWeight = FontWeight.ExtraBold)
                Text("من $goal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp).size(26.dp))
            }
        }
        Text("اختر العدد المستهدف", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1000, 100, 99, 34, 33).forEach { amount ->
                PillTab(amount.toString(), goal == amount, { goal = amount; count = 0; save(0, amount) }, Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("تخطي", { index = (index + 1) % adhkar.size; count = 0; save(0, goal) }, Modifier.weight(1f), Icons.Outlined.SkipNext)
            GlassButton("تصفير", { count = 0; save(0, goal) }, Modifier.weight(1f), Icons.Outlined.Refresh)
            GlassButton("تراجع", { if (count > 0) { count--; save(count, goal) } }, Modifier.weight(1f), Icons.Outlined.Undo)
        }
        Text("الذكر ${index + 1} من ${adhkar.size} • مرات إتمام الورد: $completedRounds",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun FavoritesPage(back: () -> Unit) {
    Page {
        Header("المفضلة", "العناصر التي تحفظها للرجوع إليها", back)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Outlined.FavoriteBorder, size = 62.dp)
                Text("لا توجد عناصر محفوظة حتى الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AboutPage(back: () -> Unit) {
    Page {
        Header("عن التطبيق", "AL-SALAH", back)
        GlassCard(Modifier.fillMaxWidth(), emphasis = true) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.logo_full), contentDescription = "شعار الصلاة",
                    modifier = Modifier.size(132.dp).clip(RoundedCornerShape(34.dp)), contentScale = ContentScale.Fit)
                Text("تطبيق الصلاة من MAS", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary)
                Text("مواقيت الصلاة، اتجاه القبلة، الأذكار والتنبيهات في مكان واحد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text("الإصدار 3.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CalendarPage(back: () -> Unit, hijriOffset: Int) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    val firstDay = displayedMonth.atDay(1)
    val leading = firstDay.dayOfWeek.value % 7
    val cells = (0 until 42).map { index ->
        val day = index - leading + 1
        if (day in 1..displayedMonth.lengthOfMonth()) displayedMonth.atDay(day) else null
    }
    val today = LocalDate.now()
    Page {
        Header("التقويم الهجري والميلادي", "الشهر كاملًا بالتاريخين معًا", back)
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Outlined.ChevronLeft, "الشهر السابق", { displayedMonth = displayedMonth.minusMonths(1) }, size = 40.dp)
            Text(displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            GlassIconButton(Icons.Outlined.ChevronRight, "الشهر التالي", { displayedMonth = displayedMonth.plusMonths(1) }, size = 40.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("ح", "ن", "ث", "ر", "خ", "ج", "س").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).height(60.dp)
                        .then(if (date == null) Modifier else Modifier.glass(RoundedCornerShape(14.dp), date == today))
                        .padding(3.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val hijri = HijrahDate.from(date).plus(hijriOffset.toLong(), ChronoUnit.DAYS)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(date.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = if (date == today) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(hijri.get(ChronoField.DAY_OF_MONTH).toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("الميلادي بالأعلى والهجري بالأسفل داخل كل يوم.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("اليوم: " + HijrahDate.now().plus(hijriOffset.toLong(), ChronoUnit.DAYS).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar"))) + " هـ",
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar"))) + " م",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
    }
}


@Composable
private fun DuasPage(back: () -> Unit) {
    val entries = listOf(
        "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ" to "البقرة: 201",
        "رَبِّ اشْرَحْ لِي صَدْرِي ۝ وَيَسِّرْ لِي أَمْرِي" to "طه: 25–26",
        "رَبَّنَا لَا تُزِغْ قُلُوبَنَا بَعْدَ إِذْ هَدَيْتَنَا وَهَبْ لَنَا مِن لَّدُنكَ رَحْمَةً" to "آل عمران: 8",
        "رَبِّ زِدْنِي عِلْمًا" to "طه: 114",
        "رَبَّنَا ظَلَمْنَا أَنْفُسَنَا وَإِنْ لَمْ تَغْفِرْ لَنَا وَتَرْحَمْنَا لَنَكُونَنَّ مِنَ الْخَاسِرِينَ" to "الأعراف: 23",
        "لَا إِلَهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ" to "الأنبياء: 87",
        "رَبَّنَا هَبْ لَنَا مِنْ أَزْوَاجِنَا وَذُرِّيَّاتِنَا قُرَّةَ أَعْيُنٍ وَاجْعَلْنَا لِلْمُتَّقِينَ إِمَامًا" to "الفرقان: 74",
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْهُدَى وَالتُّقَى وَالْعَفَافَ وَالْغِنَى" to "صحيح مسلم: 2721",
        "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ" to "أبو داود: 1522",
        "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ وَالْعَجْزِ وَالْكَسَلِ" to "صحيح البخاري: 6369",
        "يَا مُقَلِّبَ الْقُلُوبِ ثَبِّتْ قَلْبِي عَلَى دِينِكَ" to "الترمذي: 2140",
        "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي" to "الترمذي: 3513",
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ" to "أبو داود: 5074",
        "رَبِّ اغْفِرْ لِي وَلِوَالِدَيَّ وَلِلْمُؤْمِنِينَ يَوْمَ يَقُومُ الْحِسَابُ" to "إبراهيم: 41",
        "رَبَّنَا أَفْرِغْ عَلَيْنَا صَبْرًا وَتَوَفَّنَا مُسْلِمِينَ" to "الأعراف: 126",
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ مِنَ الْخَيْرِ كُلِّهِ عَاجِلِهِ وَآجِلِهِ" to "ابن ماجه: 3846"
    )
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val filtered = entries.filter { it.first.contains(query.trim()) || it.second.contains(query.trim()) }
    Page {
        Header("الأدعية", "مختارات من القرآن الكريم والسنة النبوية", back)
        OutlinedTextField(value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                focusedContainerColor = Color.White.copy(alpha = 0.07f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f)),
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary) }, placeholder = { Text("ابحث في الأدعية") })
        Text("${filtered.size} دعاء", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        filtered.forEachIndexed { index, item ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text("دعاء ${index + 1}", color = Lux.OnGold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Lux.goldBrush).padding(horizontal = 11.dp, vertical = 3.dp))
                Text(item.first, color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp,
                    lineHeight = 34.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.Outlined.Share, "مشاركة الدعاء", { shareText(context, "دعاء", item.first) }, size = 40.dp)
                    Text(item.second, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    GlassIconButton(Icons.Outlined.ContentCopy, "نسخ الدعاء", { copyToClipboard(context, "دعاء", item.first) }, size = 40.dp)
                }
            }
        }
    }
}
