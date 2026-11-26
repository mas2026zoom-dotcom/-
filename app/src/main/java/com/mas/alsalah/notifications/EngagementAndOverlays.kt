package com.mas.alsalah.notifications

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@androidx.compose.runtime.Composable
fun DailyMotivationCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("weekly_motivation", Context.MODE_PRIVATE) }
    val today = java.time.LocalDate.now()
    val startEpoch = prefs.getLong("week_start", -1L).let { stored ->
        if (stored >= 0L) stored else today.toEpochDay().also { prefs.edit().putLong("week_start", it).apply() }
    }
    val start = java.time.LocalDate.ofEpochDay(startEpoch)
    val elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, today).coerceAtLeast(0)
    val week = (elapsed / 7).toInt() + 1
    val day = (elapsed % 7).toInt()
    val key = "week_${week}_day_${day}"
    var done by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf(prefs.getBoolean(key, false)) }
    val prompts = listOf(
        "ابدأ يومك بركعتين وذكر الله", "اقرأ صفحة من القرآن بتدبر", "حافظ على أذكار الصباح والمساء",
        "صلّ على النبي ﷺ مئة مرة", "تصدّق ولو بالقليل", "صلّ رحمك أو اسأل عن شخص تحبه", "راجع ما أنجزته واشكر الله"
    )
    androidx.compose.material3.Surface(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(15.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            androidx.compose.material3.Text("تحدي الأسبوع $week • اليوم ${day + 1} من ٧",
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.material3.Text(prompts[day], color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            androidx.compose.material3.Text("إنجاز الأسبوع: " + (0..6).count { prefs.getBoolean("week_${week}_day_${it}", false) } + " / ٧",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            androidx.compose.material3.Button(onClick = {
                done = !done
                prefs.edit().putBoolean(key, done).apply()
            }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text(if (done) "تم إنجاز مهمة اليوم ✓" else "سجّل إنجاز اليوم")
            }
        }
    }
}
