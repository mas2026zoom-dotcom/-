import java.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mas.alsalah"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mas.alsalah"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "3.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    System.getenv("STABLE_DEBUG_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { ks ->
        signingConfigs.getByName("debug").apply {
            storeFile = file(ks)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!releaseKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}



// Generate launcher resources from the user-approved AL-SALAH brand icon.
// Preserve the original artwork aspect ratio and leave safe padding so Android's
// adaptive-icon mask never crops the logo.
val generateAlSalahLauncherIcons by tasks.registering {
    val sourceAsset = layout.projectDirectory.file("src/main/icon/al_salah_icon.jpg.b64")
    inputs.file(sourceAsset)
    outputs.dir(layout.projectDirectory.dir("src/main/res/mipmap-mdpi"))
    outputs.dir(layout.projectDirectory.dir("src/main/res/drawable-nodpi"))
    doLast {
        val encoded = sourceAsset.asFile.readText().filterNot { it.isWhitespace() }
        val imageBytes = Base64.getDecoder().decode(encoded)
        val source = ImageIO.read(ByteArrayInputStream(imageBytes))
            ?: error("AL-SALAH launcher icon asset is not a valid image")

        fun renderContained(canvasSize: Int, maxFraction: Double): BufferedImage {
            val canvas = BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB)
            val graphics = canvas.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val scale = minOf(
                canvasSize * maxFraction / source.width,
                canvasSize * maxFraction / source.height
            )
            val width = (source.width * scale).toInt().coerceAtLeast(1)
            val height = (source.height * scale).toInt().coerceAtLeast(1)
            val left = (canvasSize - width) / 2
            val top = (canvasSize - height) / 2
            graphics.drawImage(source, left, top, width, height, null)
            graphics.dispose()
            return canvas
        }

        val densities = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192
        )
        densities.forEach { (folder, size) ->
            val dir = layout.projectDirectory.dir("src/main/res/$folder").asFile
            dir.mkdirs()
            // Legacy launchers: 10% breathing room around the complete logo.
            ImageIO.write(renderContained(size, 0.90), "png", File(dir, "ic_launcher.png"))
        }

        val adaptiveDir = layout.projectDirectory.dir("src/main/res/drawable-nodpi").asFile
        adaptiveDir.mkdirs()
        // Adaptive launchers apply a circular/squircle mask; keep the artwork
        // inside the central safe zone to prevent any edge clipping.
        ImageIO.write(renderContained(432, 0.68), "png", File(adaptiveDir, "ic_launcher_art.png"))
        ImageIO.write(renderContained(512, 1.0), "png", File(adaptiveDir, "logo_full.png"))
    }
}
// Restore the verified bundled Adhan recording used by the prayer-time playback service.
val downloadVerifiedAdhan by tasks.registering {
    val adhanFile = layout.projectDirectory.file("src/main/res/raw/beautiful_adhan.ogg").asFile
    outputs.file(adhanFile)
    doLast {
        adhanFile.parentFile.mkdirs()
        val sourceUrl = "https://upload.wikimedia.org/wikipedia/commons/b/b0/Beautiful_adhan.ogg"
        val connection = URL(sourceUrl).openConnection().apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; AL-SALAH-Android/2.0)")
            setRequestProperty("Referer", "https://commons.wikimedia.org/")
            setRequestProperty("Accept", "audio/ogg,application/ogg,*/*")
        }
        connection.getInputStream().use { input -> adhanFile.outputStream().use { output -> input.copyTo(output) } }
        val actualSha1 = MessageDigest.getInstance("SHA-1").digest(adhanFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(actualSha1 == "a1fa4fd942401922c5c3301816384aae86956522") { "Downloaded Adhan audio checksum did not match verified CC0 source" }
    }
}

// Bundle the fully offline Quran section: Madinah Mushaf page images (604 pages)
// and the Tanzil Uthmani text used for offline search. Both are fetched once at
// build time and baked into the APK assets, so the app never needs the network.
val fetchQuranAssets by tasks.registering {
    val assetsDir = layout.projectDirectory.dir("src/main/assets/quran").asFile
    outputs.dir(assetsDir)
    doLast {
        assetsDir.mkdirs()
        val pagesDir = File(assetsDir, "pages")
        pagesDir.mkdirs()
        val havePages = pagesDir.listFiles { f -> f.name.endsWith(".png") }?.size ?: 0
        if (havePages < 604) {
            val archive = File(temporaryDir, "madina.zip")
            val source = "https://codeload.github.com/tarekeldeeb/madina_images/zip/refs/heads/w1024"
            val connection = URL(source).openConnection().apply {
                connectTimeout = 20000
                readTimeout = 300000
                setRequestProperty("User-Agent", "AL-SALAH-Android/3.1")
            }
            connection.getInputStream().use { input -> archive.outputStream().use { output -> input.copyTo(output) } }
            ZipInputStream(archive.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && name.startsWith("w1024_page") && name.endsWith(".png")) {
                        val page = name.removePrefix("w1024_page").removeSuffix(".png").toIntOrNull()
                        if (page != null && page in 1..604) {
                            File(pagesDir, "p%03d.png".format(page)).outputStream().use { out -> zip.copyTo(out) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            archive.delete()
            val count = pagesDir.listFiles { f -> f.name.endsWith(".png") }?.size ?: 0
            check(count == 604) { "Quran page images incomplete: $count of 604" }
        }
        val textFile = File(assetsDir, "quran_uthmani.txt")
        if (!textFile.exists() || textFile.length() < 1000000L) {
            val source = "https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt-2&agree=true"
            val connection = URL(source).openConnection().apply {
                connectTimeout = 20000
                readTimeout = 120000
                setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; AL-SALAH-Android/3.1)")
            }
            connection.getInputStream().use { input -> textFile.outputStream().use { output -> input.copyTo(output) } }
            check(textFile.length() > 1000000L) { "Tanzil Quran text download failed" }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateAlSalahLauncherIcons, downloadVerifiedAdhan, fetchQuranAssets)
}
