package com.mas.alsalah.notifications

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mas.alsalah.GlassButton
import com.mas.alsalah.GlassCard
import com.mas.alsalah.GoldButton
import com.mas.alsalah.IconBadge

@Composable
fun DailyMotivationCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weekly_motivation", Context.MODE_PRIVATE) }
    val today = java.time.LocalDate.now()
    val startEpoch = prefs.getLong("week_start", -1L).let { stored ->
        if (stored >= 0L) stored else today.toEpochDay().also { prefs.edit().putLong("week_start", it).apply() }
    }
    val start = java.time.LocalDate.ofEpochDay(startEpoch)
    val elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, today).coerceAtLeast(0)
    val week = (elapsed / 7).toInt() + 1
    val day = (elapsed % 7).toInt()
    val key = "week_${week}_day_${day}"
    var done by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
    val prompts = listOf(
        "ابدأ يومك بركعتين وذكر الله", "اقرأ صفحة من القرآن بتدبر", "حافظ على أذكار الصباح والمساء",
        "صلّ على النبي ﷺ مئة مرة", "تصدّق ولو بالقليل", "صلّ رحمك أو اسأل عن شخص تحبه", "راجع ما أنجزته واشكر الله"
    )
    GlassCard(Modifier.fillMaxWidth(), emphasis = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "إنجاز الأسبوع: " + (0..6).count { prefs.getBoolean("week_${week}_day_${it}", false) } + " / ٧",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("تحدي الأسبوع $week", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("اليوم ${day + 1} من ٧", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                IconBadge(Icons.Outlined.EmojiEvents, size = 44.dp)
            }
        }
        Text(prompts[day], color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        if (done) {
            GlassButton("تم إنجاز مهمة اليوم ✓", {
                done = false
                prefs.edit().putBoolean(key, false).apply()
            }, Modifier.fillMaxWidth())
        } else {
            GoldButton("سجّل إنجاز اليوم", {
                done = true
                prefs.edit().putBoolean(key, true).apply()
            }, Modifier.fillMaxWidth(), height = 48.dp)
        }
    }
}
