package com.mas.alsalah

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

internal data class PageLoad(val bitmap: ImageBitmap?, val error: String?)

private object QuranStore {
    private const val ZIP_URL = "https://github.com/mas2026zoom-dotcom/-/releases/download/quran-pages/quran-pages.zip"
    const val PAGES = 604

    fun dir(context: Context): File = File(context.filesDir, "quran")
    fun pagesDir(context: Context): File = File(dir(context), "pages")
    fun pageFile(context: Context, page: Int): File = File(pagesDir(context), "p" + page.toString().padStart(3, '0') + ".png")

    fun ready(context: Context): Boolean {
        if (!File(dir(context), "ready").exists()) return false
        val files = pagesDir(context).listFiles { f -> f.name.endsWith(".png") } ?: return false
        return files.size >= PAGES
    }

    fun storedBytes(context: Context): Long {
        val files = pagesDir(context).listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }

    suspend fun download(context: Context, onProgress: (Long, Long) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = dir(context)
            root.mkdirs()
            val zip = File(root, "madina.zip")
            val have = if (zip.exists()) zip.length() else 0L
            val connection = (URL(ZIP_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AL-SALAH-Android/3.2")
                if (have > 0) setRequestProperty("Range", "bytes=" + have + "-")
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP " + code)
            val append = code == 206 && have > 0
            val total = if (append) have + connection.contentLengthLong else connection.contentLengthLong
            var done = if (append) have else 0L
            var lastReport = 0L
            connection.inputStream.use { input ->
                FileOutputStream(zip, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done - lastReport > 512 * 1024) {
                            lastReport = done
                            onProgress(done, total)
                        }
                    }
                }
            }
            onProgress(done, total)
            if (total > 0 && zip.length() < total && !append) throw IOException("تنزيل غير مكتمل")
            val pages = pagesDir(context)
            pages.mkdirs()
            var count = 0
            ZipInputStream(zip.inputStream().buffered()).use { zipped ->
                var entry = zipped.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && name.startsWith("w1024_page") && name.endsWith(".png")) {
                        val number = name.removePrefix("w1024_page").removeSuffix(".png").toIntOrNull()
                        if (number != null && number in 1..PAGES) {
                            val target = File(pages, "p" + number.toString().padStart(3, '0') + ".png")
                            target.outputStream().use { out -> zipped.copyTo(out) }
                            val head = ByteArray(8)
                            target.inputStream().use { it.read(head) }
                            val png = head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte()
                            if (!png || target.length() < 2000L) throw IOException("صفحة تالفة " + number)
                            count++
                        }
                    }
                    zipped.closeEntry()
                    entry = zipped.nextEntry
                }
            }
            if (count < PAGES) throw IOException("نزلت " + count + " من " + PAGES + " صفحة")
            zip.delete()
            File(root, "ready").writeText(count.toString())
            count
        }
    }
}

private object QuranPages {
    private val cache = object : LruCache<String, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 2
    }
    private val gate = Semaphore(1)

    private fun cacheBytes(): Int {
        val budget = (Runtime.getRuntime().maxMemory() / 8).toInt()
        return budget.coerceIn(16 * 1024 * 1024, 64 * 1024 * 1024)
    }

    fun cached(page: Int): ImageBitmap? = cache.get("p" + page)

    suspend fun load(context: Context, page: Int): PageLoad = withContext(Dispatchers.IO) {
        val key = "p" + page
        val hit = cache.get(key)
        if (hit != null) return@withContext PageLoad(hit, null)
        var result = PageLoad(null, "لم تبدأ القراءة")
        gate.withPermit {
            val again = cache.get(key)
            if (again != null) {
                result = PageLoad(again, null)
            } else {
                val attempt = decode(context, page)
                if (attempt.bitmap != null) cache.put(key, attempt.bitmap)
                result = attempt
            }
        }
        result
    }

    private fun decode(context: Context, page: Int): PageLoad {
        val file = QuranStore.pageFile(context, page)
        if (file.exists()) {
            val fromFile = decodeFile(file, 1)
            if (fromFile.bitmap != null) return fromFile
            cache.evictAll()
            val smaller = decodeFile(file, 2)
            if (smaller.bitmap != null) return smaller
            return smaller
        }
        val name = "quran/pages/p" + page.toString().padStart(3, '0') + ".png"
        val first = decodeWith(context, name, 1)
        if (first.bitmap != null) return first
        cache.evictAll()
        val second = decodeWith(context, name, 2)
        if (second.bitmap != null) return second
        return PageLoad(null, (first.error ?: "خطأ") + " | " + (second.error ?: "خطأ"))
    }

    private fun decodeFile(file: File, sample: Int): PageLoad {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = sample
                inScaled = false
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap == null) PageLoad(null, "decodeFile=null (sample " + sample + ")") else PageLoad(bitmap.asImageBitmap(), null)
        } catch (t: Throwable) {
            Log.w("QuranPages", "decode failed for " + file.name + " sample=" + sample, t)
            PageLoad(null, t.javaClass.simpleName + ": " + (t.message ?: ""))
        }
    }

    private fun decodeWith(context: Context, name: String, sample: Int): PageLoad {
        return try {
            context.assets.open(name).use { stream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = sample
                    inScaled = false
                }
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                if (bitmap == null) {
                    PageLoad(null, "decodeStream=null (sample $sample)")
                } else {
                    PageLoad(bitmap.asImageBitmap(), null)
                }
            }
        } catch (t: Throwable) {
            Log.w("QuranPages", "decode failed for " + name + " sample=" + sample, t)
            PageLoad(null, t.javaClass.simpleName + ": " + (t.message ?: ""))
        }
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


private class MushafDownloadState(initialReady: Boolean) {
    var ready by mutableStateOf(initialReady)
    var downloading by mutableStateOf(false)
    var progress by mutableStateOf<Pair<Long, Long>?>(null)
    var error by mutableStateOf<String?>(null)
    var dismissed by mutableStateOf(false)

    fun start(context: Context, scope: CoroutineScope) {
        if (downloading) return
        downloading = true
        error = null
        progress = 0L to 0L
        scope.launch {
            val result = QuranStore.download(context) { done, total -> progress = done to total }
            downloading = false
            if (result.isSuccess) {
                ready = true
                progress = null
            } else {
                error = result.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + (it.message ?: "") }
            }
        }
    }
}

@Composable
private fun rememberMushafDownload(context: Context): MushafDownloadState =
    remember { MushafDownloadState(QuranStore.ready(context)) }

private fun megabytes(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f", bytes / (1024.0 * 1024.0))

@Composable
private fun MushafDownloadCard(
    state: MushafDownloadState,
    onDownload: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x14FFFFFF)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("تنزيل المصحف", color = QuranGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "صفحات المصحف (٦٠٤ صفحة، نحو ٥٠ ميجابايت) تنزل مرة واحدة، وبعدها يفتح المصحف بدون إنترنت. يعمل على الواي فاي أو بيانات الجوال.",
            color = QuranInk.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center
        )
        if (state.downloading) {
            val done = state.progress?.first ?: 0L
            val total = state.progress?.second ?: 0L
            val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(6.dp))
            Text(megabytes(done) + " من " + megabytes(total) + " م.ب", color = QuranInk.copy(alpha = 0.6f), fontSize = 11.sp)
        } else {
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFB3261E), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDownload) {
                    Text(if (state.error == null) "تنزيل المصحف" else "إعادة المحاولة", fontSize = 14.sp)
                }
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text("لاحقًا", color = QuranInk.copy(alpha = 0.7f), fontSize = 14.sp) }
                }
            }
        }
    }
}

/* ============================ section ============================ */

@Composable
internal fun QuranSection(open: (Int) -> Unit, back: () -> Unit, initialTab: Int = 0, autodownload: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("quran", Context.MODE_PRIVATE) }
    var data by remember { mutableStateOf<QuranData?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var searchOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(readBookmarks(prefs)) }
    val download = rememberMushafDownload(context)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { runCatching { QuranRepo.data(context) }.getOrNull() }
        if (loaded == null) failed = true else data = loaded
    }
    LaunchedEffect(autodownload) {
        if (autodownload && !download.ready) download.start(context, scope)
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

        if (!download.ready && !download.dismissed) {
            MushafDownloadCard(
                state = download,
                onDownload = { download.start(context, scope) },
                onDismiss = { download.dismissed = true }
            )
            Spacer(Modifier.height(12.dp))
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
internal fun MushafScreen(startPage: Int, back: () -> Unit, autodownload: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("quran", Context.MODE_PRIVATE) }
    var data by remember { mutableStateOf<QuranData?>(null) }
    var bookmarks by remember { mutableStateOf(readBookmarks(prefs)) }
    val download = rememberMushafDownload(context)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) { runCatching { QuranRepo.data(context) }.getOrNull() }
    }
    LaunchedEffect(autodownload) {
        if (autodownload && !download.ready) download.start(context, scope)
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

        if (!download.ready) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                MushafDownloadCard(
                    state = download,
                    onDownload = { download.start(context, scope) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
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
}

@Composable
private fun MushafPageImage(page: Int) {
    val context = LocalContext.current
    var attempt by remember(page) { mutableIntStateOf(0) }
    val state by produceState<PageLoad?>(initialValue = QuranPages.cached(page)?.let { PageLoad(it, null) }, key1 = page, key2 = attempt) {
        if (value == null) value = QuranPages.load(context, page)
        if (value?.bitmap != null) {
            QuranPages.prefetch(context, page + 1)
            QuranPages.prefetch(context, page - 1)
        }
    }
    Box(Modifier.fillMaxSize().background(QuranPaper), contentAlignment = Alignment.Center) {
        val image = state?.bitmap
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = "صفحة " + arabicNumber(page),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            state != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text("تعذر تحميل الصفحة", color = QuranInk.copy(alpha = 0.75f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(state?.error ?: "", color = QuranInk.copy(alpha = 0.55f), fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { attempt += 1 }) {
                    Text("إعادة المحاولة", color = QuranGreen, fontSize = 14.sp)
                }
            }
            else -> CircularProgressIndicator(color = QuranInk.copy(alpha = 0.35f), modifier = Modifier.size(28.dp))
        }
    }
}
