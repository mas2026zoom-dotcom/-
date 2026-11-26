package com.mas.alsalah

import android.content.Context
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.mas.alsalah.notifications.AdhanAudio
import java.io.File
import java.util.Locale

private const val CUSTOM_ADHAN_PREFS = "custom_adhan_preferences"
private const val CUSTOM_ADHAN_SELECTED = "selected_custom_adhan"
private const val CUSTOM_ADHAN_LIMIT = 3
private const val CUSTOM_ADHAN_MAX_BYTES = 10L * 1024L * 1024L

@Composable
fun CustomAdhanManager(context: Context = LocalContext.current) {
    val directory = remember(context) { File(context.filesDir, "custom_adhans").apply { mkdirs() } }
    val preferences = remember(context) { context.getSharedPreferences(CUSTOM_ADHAN_PREFS, Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedName by remember { mutableStateOf(preferences.getString(CUSTOM_ADHAN_SELECTED, null)) }
    var playing by remember { mutableStateOf(false) }
    val audioFiles = remember(revision) {
        directory.listFiles()?.filter { it.isFile && it.length() > 0L }?.sortedBy { it.lastModified() } ?: emptyList()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var destination: File? = null
            try {
                val currentCount = directory.listFiles()?.count { it.isFile && it.length() > 0L } ?: 0
                if (currentCount >= CUSTOM_ADHAN_LIMIT) {
                    Toast.makeText(context, "يمكنك حفظ 3 أصوات فقط", Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
                val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty()
                val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
                val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT).ifBlank {
                    when (mime) {
                        "audio/mpeg", "audio/mp3" -> "mp3"
                        "audio/mp4", "audio/x-m4a" -> "m4a"
                        "audio/ogg", "application/ogg" -> "ogg"
                        "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "wav"
                        else -> ""
                    }
                }
                if (extension !in setOf("mp3", "m4a", "ogg", "wav")) {
                    Toast.makeText(context, "الصيغ المدعومة: MP3 وM4A وOGG وWAV", Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
                val targetFile = File(directory, "adhan_${System.currentTimeMillis()}.$extension")
                destination = targetFile
                var copiedBytes = 0L
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("تعذر فتح الملف")
                input.use { source ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            copiedBytes += read
                            if (copiedBytes > CUSTOM_ADHAN_MAX_BYTES) throw IllegalArgumentException("FILE_TOO_LARGE")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (copiedBytes == 0L) throw IllegalArgumentException("EMPTY_FILE")
                revision++
                Toast.makeText(context, "تم رفع الصوت بنجاح", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                destination?.delete()
                val message = when (error.message) {
                    "FILE_TOO_LARGE" -> "حجم الملف أكبر من 10 ميجابايت"
                    "EMPTY_FILE" -> "الملف فارغ"
                    else -> "تعذر إضافة الملف، جرّب ملفًا صوتيًا آخر"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("أصواتي", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${audioFiles.size} من 3 أصوات • 10 ميجابايت كحد أقصى لكل ملف",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            audioFiles.forEach { file ->
                val active = selectedName == file.name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    RadioButton(selected = active, onClick = {
                        selectedName = file.name
                        preferences.edit().putString(CUSTOM_ADHAN_SELECTED, file.name).apply()
                    })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.name.substringAfter('_').substringBeforeLast('.').let { "تسجيل أذان" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        playing = AdhanAudio.play(context, 100, file.absolutePath)
                        if (!playing) Toast.makeText(context, "تعذر تشغيل الملف الصوتي", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.PlayCircle, contentDescription = "تجربة الصوت")
                    }
                    IconButton(onClick = {
                        if (selectedName == file.name) {
                            selectedName = null
                            preferences.edit().remove(CUSTOM_ADHAN_SELECTED).apply()
                        }
                        if (file.delete()) {
                            revision++
                            Toast.makeText(context, "تم حذف الصوت", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "تعذر حذف الملف", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الصوت", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Button(
                onClick = { filePicker.launch(arrayOf("audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/*")) },
                enabled = audioFiles.size < CUSTOM_ADHAN_LIMIT,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (audioFiles.size < CUSTOM_ADHAN_LIMIT) "إضافة صوت من الهاتف" else "وصلت للحد الأقصى (3 أصوات)")
            }
            Text(
                "اختر أحد التسجيلات ليكون صوت الأذان عند تفعيل «أذان كامل». الملفات محفوظة على هذا الهاتف ولا تحتاج إلى إنترنت.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
