package com.mas.alsalah

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/* ============================ data ============================ */

internal data class QuranSurah(val number: Int, val name: String, val ayahs: Int, val type: String, val page: Int)
internal data class QuranJuz(val number: Int, val page: Int)
internal data class QuranVerse(val surah: Int, val ayah: Int, val text: String, val norm: String)

internal class QuranData(
    val surahs: List<QuranSurah>,
    val juzs: List<QuranJuz>,
    private val pageStarts: IntArray
) {
    val pageCount: Int get() = pageStarts.size

    fun pageOf(surah: Int, ayah: Int): Int {
        val key = surah * 1000 + ayah
        var lo = 0
        var hi = pageStarts.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (pageStarts[mid] <= key) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans + 1
    }

    fun surahOf(page: Int): QuranSurah {
        val key = pageStarts[(page - 1).coerceIn(0, pageStarts.size - 1)]
        val number = key / 1000
        return surahs.firstOrNull { it.number == number } ?: surahs.first()
    }

    fun juzOf(page: Int): QuranJuz = juzs.lastOrNull { it.page <= page } ?: juzs.first()

    fun startOf(page: Int): Pair<Int, Int> {
        val key = pageStarts[(page - 1).coerceIn(0, pageStarts.size - 1)]
        return (key / 1000) to (key % 1000)
    }

    fun versesOfPage(page: Int): List<Pair<Int, Int>> {
        val start = startOf(page)
        val end = if (page < pageStarts.size) startOf(page + 1) else null
        val out = ArrayList<Pair<Int, Int>>()
        var surah = start.first
        var ayah = start.second
        var guard = 0
        while (guard < 40) {
            guard += 1
            if (end != null && (surah > end.first || (surah == end.first && ayah >= end.second))) break
            out.add(surah to ayah)
            val maxAyah = surahs.firstOrNull { it.number == surah }?.ayahs ?: 0
            if (ayah >= maxAyah) {
                surah += 1
                ayah = 1
                if (surah > 114) break
            } else {
                ayah += 1
            }
        }
        return out
    }
}

internal object QuranRepo {
    @Volatile private var cached: QuranData? = null
    @Volatile private var cachedVerses: List<QuranVerse>? = null

    fun data(context: Context): QuranData {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.assets.open("quran/meta.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val surahArray = root.getJSONArray("surahs")
            val surahs = ArrayList<QuranSurah>(surahArray.length())
            for (i in 0 until surahArray.length()) {
                val o = surahArray.getJSONObject(i)
                surahs.add(
                    QuranSurah(
                        number = o.getInt("n"),
                        name = o.getString("name"),
                        ayahs = o.getInt("ayahs"),
                        type = o.getString("type"),
                        page = o.getInt("page")
                    )
                )
            }
            val juzArray = root.getJSONArray("juzs")
            val juzs = ArrayList<QuranJuz>(juzArray.length())
            for (i in 0 until juzArray.length()) {
                val o = juzArray.getJSONObject(i)
                juzs.add(QuranJuz(o.getInt("n"), o.getInt("page")))
            }
            val pages = root.getJSONArray("pages")
            val starts = IntArray(pages.length())
            for (i in 0 until pages.length()) {
                val pair = pages.getJSONArray(i)
                starts[i] = pair.getInt(0) * 1000 + pair.getInt(1)
            }
            val loaded = QuranData(surahs, juzs, starts)
            cached = loaded
            return loaded
        }
    }

    fun verses(context: Context): List<QuranVerse> {
        cachedVerses?.let { return it }
        synchronized(this) {
            cachedVerses?.let { return it }
            val list = ArrayList<QuranVerse>(6236)
            context.assets.open("quran/quran_uthmani.txt").bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val first = line.indexOf('|')
                    if (first <= 0) return@forEach
                    val second = line.indexOf('|', first + 1)
                    if (second <= first) return@forEach
                    val surah = line.substring(0, first).toIntOrNull() ?: return@forEach
                    val ayah = line.substring(first + 1, second).toIntOrNull() ?: return@forEach
                    val body = line.substring(second + 1)
                    list.add(QuranVerse(surah, ayah, body, normalizeArabic(body)))
                }
            }
            cachedVerses = list
            return list
        }
    }
}

internal fun normalizeArabic(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
        when (ch) {
            in '\u064B'..'\u0655', '\u0670', in '\u06D6'..'\u06ED', '\u0640', '\u06E1' -> Unit
            'أ', 'إ', 'آ', 'ٱ', 'ٲ', 'ٳ' -> sb.append('ا')
            'ة' -> sb.append('ه')
            'ى' -> sb.append('ي')
            'ؤ' -> sb.append('و')
            'ئ' -> sb.append('ي')
            'ک' -> sb.append('ك')
            'ی' -> sb.append('ي')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun arabicNumber(value: Int): String {
    val digits = "٠١٢٣٤٥٦٧٨٩"
    return value.toString().map { if (it in '0'..'9') digits[it - '0'] else it }.joinToString("")
}

/* ============================ page bitmaps ============================ */

private val QuranPaper = Color(0xFFF6EFDC)
private val QuranInk = Color(0xFF14251D)
private val QuranGreen = Color(0xFF0B6B4A)

private object QuranPages {
    private val cache = object : LruCache<String, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val gate = Semaphore(1)

    private fun cacheBytes(): Int {
        val budget = (Runtime.getRuntime().maxMemory() / 6).toInt()
        return budget.coerceIn(24 * 1024 * 1024, 96 * 1024 * 1024)
    }

    fun cached(page: Int): ImageBitmap? = cache.get("p" + page)

    suspend fun load(context: Context, page: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "p" + page
        val hit = cache.get(key)
        if (hit != null) return@withContext hit
        var decoded: ImageBitmap? = null
        gate.withPermit {
            val again = cache.get(key)
            if (again != null) {
                decoded = again
            } else {
                val fresh = decode(context, page)
                if (fresh != null) cache.put(key, fresh)
                decoded = fresh
            }
        }
        decoded
    }

    private fun decode(context: Context, page: Int): ImageBitmap? = try {
        context.assets.open("quran/pages/p%03d.png".format(page)).use { stream ->
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    } catch (t: Throwable) {
        Log.w("QuranPages", "decode failed for page " + page, t)
        null
    }

    suspend fun prefetch(context: Context, page: Int) {
        if (page < 1 || page > 604) return
        if (cache.get("p" + page) != null) return
        runCatching { load(context, page) }
    }
}

private fun readBookmarks(prefs: SharedPreferences): Set<Int> =
    prefs.getStringSet("bookmarks", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

private fun writeBookmarks(prefs: SharedPreferences, pages: Set<Int>) {
    prefs.edit().putStringSet("bookmarks", pages.map { it.toString() }.toSet()).apply()
}

/* ============================ section ============================ */

@Composable
internal fun QuranSection(open: (Int) -> Unit, back: () -> Unit, initialTab: Int = 0) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("quran", Context.MODE_PRIVATE) }
    var data by remember { mutableStateOf<QuranData?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var searchOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(readBookmarks(prefs)) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { runCatching { QuranRepo.data(context) }.getOrNull() }
        if (loaded == null) failed = true else data = loaded
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Outlined.Search, "بحث في القرآن", { searchOpen = true }, size = 42.dp)
                GlassIconButton(Icons.Outlined.MenuBook, "متابعة القراءة", {
                    open(prefs.getInt("last_page", 1).coerceIn(1, 604))
                }, size = 42.dp)
                GlassIconButton(Icons.Outlined.MoreVert, "مصادر وتراخيص", { sourcesOpen = true }, size = 42.dp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("قرآن", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Lux.goldBrush))
            }
        }

        Spacer(Modifier.height(10.dp))
        QuranTabs(tab) { tab = it }
        Spacer(Modifier.height(8.dp))

        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("تعذر تحميل بيانات القرآن الكريم", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            tab == 0 -> SurahIndex(data!!) { open(it) }
            tab == 1 -> JuzIndex(data!!) { open(it) }
            else -> BookmarksIndex(data!!, bookmarks, open) { pages ->
                bookmarks = pages
                writeBookmarks(prefs, pages)
            }
        }
    }

    if (searchOpen && data != null) {
        QuranSearch(data!!, onClose = { searchOpen = false }, onOpenPage = { searchOpen = false; open(it) })
    }
    if (sourcesOpen) QuranSources { sourcesOpen = false }
}

@Composable
private fun QuranTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("المرجعيات", "الجزء", "سورة")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { position, label ->
            val index = labels.size - 1 - position
            val active = index == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(index) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    color = if (active) Lux.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.width(54.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (active) Lux.goldBrush else SolidColor(Color.Transparent))
                )
            }
        }
    }
}

private sealed class QuranRow {
    data class Juz(val juz: QuranJuz) : QuranRow()
    data class Surah(val surah: QuranSurah) : QuranRow()
}

private fun buildRows(data: QuranData): List<QuranRow> {
    val rows = ArrayList<QuranRow>(data.surahs.size + data.juzs.size)
    var s = 0
    var j = 0
    while (s < data.surahs.size || j < data.juzs.size) {
        val surah = data.surahs.getOrNull(s)
        val juz = data.juzs.getOrNull(j)
        when {
            surah == null -> { rows.add(QuranRow.Juz(juz!!)); j++ }
            juz == null -> { rows.add(QuranRow.Surah(surah)); s++ }
            juz.page <= surah.page -> { rows.add(QuranRow.Juz(juz)); j++ }
            else -> { rows.add(QuranRow.Surah(surah)); s++ }
        }
    }
    return rows
}

@Composable
private fun SurahIndex(data: QuranData, open: (Int) -> Unit) {
    val rows = remember(data) { buildRows(data) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(rows) { row ->
            when (row) {
                is QuranRow.Juz -> JuzBand(row.juz) { open(row.juz.page) }
                is QuranRow.Surah -> SurahRow(row.surah) { open(row.surah.page) }
            }
        }
    }
}

@Composable
private fun JuzBand(juz: QuranJuz, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Lux.Gold.copy(alpha = 0.13f)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arabicNumber(juz.page), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        Text(
            "الجزء " + arabicNumber(juz.number),
            color = Lux.GoldDeep, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SurahRow(surah: QuranSurah, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arabicNumber(surah.number), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(surah.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Text(
                surah.type + " - " + arabicNumber(surah.ayahs) + " آية",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun JuzIndex(data: QuranData, open: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(data.juzs) { juz ->
            val surah = data.surahOf(juz.page)
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { open(juz.page) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(arabicNumber(juz.page), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("الجزء " + arabicNumber(juz.number), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("يبدأ من " + surah.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun BookmarksIndex(data: QuranData, bookmarks: Set<Int>, open: (Int) -> Unit, onBookmarks: (Set<Int>) -> Unit) {
    if (bookmarks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.BookmarkBorder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(38.dp))
                Text("لا توجد صفحات محفوظة بعد", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("افتح المصحف واضغط علامة الحفظ لتسجيل الصفحة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val pages = bookmarks.sorted()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(pages) { page ->
            val surah = data.surahOf(page)
            val juz = data.juzOf(page)
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { open(page) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { onBookmarks(bookmarks - page) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Delete, "حذف العلامة", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f).padding(start = 10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("صفحة " + arabicNumber(page), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text(surah.name + " - الجزء " + arabicNumber(juz.number), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/* ============================ search ============================ */

@Composable
private fun QuranSearch(data: QuranData, onClose: () -> Unit, onOpenPage: (Int) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<QuranVerse>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val needle = normalizeArabic(query.trim())
        if (needle.length < 2) { results = emptyList(); busy = false; return@LaunchedEffect }
        busy = true
        val found = withContext(Dispatchers.Default) {
            val all = runCatching { QuranRepo.verses(context) }.getOrDefault(emptyList())
            all.asSequence().filter { it.norm.contains(needle) }.take(150).toList()
        }
        results = found
        busy = false
        ready = true
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Outlined.Close, "إغلاق البحث", onClose, size = 42.dp)
            Text("بحث في القرآن", color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("اكتب كلمة أو جزء من آية", fontSize = 13.sp) },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        when {
            busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (ready && query.trim().length >= 2) "لا توجد نتائج" else "اكتب حرفين على الأقل للبحث",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results) { verse ->
                    val surah = data.surahs.firstOrNull { it.number == verse.surah }
                    Column(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp))
                            .clickable { onOpenPage(data.pageOf(verse.surah, verse.ayah)) }
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            (surah?.name ?: "") + " - آية " + arabicNumber(verse.ayah),
                            color = Lux.GoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                        Text(verse.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

/* ============================ sources ============================ */

@Composable
private fun QuranSources(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Outlined.Close, "إغلاق", onClose, size = 42.dp)
            Text("مصادر النص والصور", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("نص القرآن الكريم", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    Text(
                        "نص عثماني من مشروع تنزيل (Tanzil.net)، يُستخدم كما هو دون تعديل مع الإشارة إلى المصدر. حقوق النص لمشروع تنزيل.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End
                    )
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("صور صفحات المصحف", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    Text(
                        "مصحف المدينة النبوية (15 سطرًا) من إصدار مجمع الملك فهد لطباعة المصحف الشريف، عبر مشروع quran.com-images والنسخة المضغوطة madina_images (رخصة المستودع GPLv3). حقوق الصفحات والخط لمجمع الملك فهد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End
                    )
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("بيانات السور والأجزاء", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    Text(
                        "أرقام الصفحات وبدايات الأجزاء من واجهة AlQuran Cloud (api.alquran.cloud) ومطابقة لمصحف المدينة ذي 604 صفحات.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End
                    )
                }
            }
            item {
                Text(
                    "كل المحتوى محفوظ داخل التطبيق ويعمل بدون إنترنت.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End
                )
            }
        }
    }
}

/* ============================ mushaf ============================ */

@Composable
internal fun MushafScreen(startPage: Int, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("quran", Context.MODE_PRIVATE) }
    var data by remember { mutableStateOf<QuranData?>(null) }
    var bookmarks by remember { mutableStateOf(readBookmarks(prefs)) }

    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) { runCatching { QuranRepo.data(context) }.getOrNull() }
    }

    val pager = rememberPagerState(initialPage = (startPage - 1).coerceIn(0, 603), pageCount = { 604 })
    val page = pager.currentPage + 1
    val surah = data?.surahOf(page)
    val juz = data?.juzOf(page)
    val marked = bookmarks.contains(page)

    LaunchedEffect(page) { prefs.edit().putInt("last_page", page).apply() }

    Column(Modifier.fillMaxSize().background(QuranPaper)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable {
                    val next = if (marked) bookmarks - page else bookmarks + page
                    bookmarks = next
                    writeBookmarks(prefs, next)
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (marked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    "حفظ الصفحة",
                    tint = if (marked) Lux.GoldDeep else QuranGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(surah?.name ?: "", color = QuranGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "صفحة " + arabicNumber(page) + "، جزء " + arabicNumber(juz?.number ?: 1),
                    color = QuranInk.copy(alpha = 0.62f), fontSize = 11.sp
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = back),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ArrowBack, "رجوع", tint = QuranGreen, modifier = Modifier.size(22.dp))
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            beyondViewportPageCount = 0
        ) { index ->
            MushafPageImage(index + 1)
        }
    }
}

@Composable
private fun MushafPageImage(page: Int) {
    val context = LocalContext.current
    var attempt by remember(page) { mutableIntStateOf(0) }
    var failed by remember(page, attempt) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(initialValue = QuranPages.cached(page), key1 = page, key2 = attempt) {
        if (value == null) {
            val loaded = QuranPages.load(context, page)
            value = loaded
            failed = loaded == null
        }
        if (value != null) {
            QuranPages.prefetch(context, page + 1)
            QuranPages.prefetch(context, page - 1)
        }
    }
    Box(Modifier.fillMaxSize().background(QuranPaper), contentAlignment = Alignment.Center) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = "صفحة " + arabicNumber(page),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تعذر تحميل الصفحة", color = QuranInk.copy(alpha = 0.7f), fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { attempt += 1 }) {
                    Text("إعادة المحاولة", color = QuranGreen, fontSize = 14.sp)
                }
            }
            else -> CircularProgressIndicator(color = QuranInk.copy(alpha = 0.35f), modifier = Modifier.size(28.dp))
        }
    }
}
