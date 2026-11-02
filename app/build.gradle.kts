import java.util.Properties
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.io.ByteArrayOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile as UnixZipFile
buildscript {
    repositories { mavenCentral() }
    // Match the compression API shipped by the pinned Gradle 8.11.1 runtime.
    dependencies { classpath("org.apache.commons:commons-compress:1.26.1") }
}
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val signing = Properties().apply { rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
// 0.18.1 bundles five WebP libraries with 4 KB ELF segments. Replace only those
// with ABI-compatible, 16 KB-aligned Termux builds; keep the engine and FFmpeg unchanged.
val ffmpegOriginal by configurations.creating { isTransitive = false }
val patchedFfmpeg = layout.buildDirectory.file("native/ffmpeg-0.18.1-patched.aar")
val prepareFfmpeg by tasks.registering {
    inputs.files(ffmpegOriginal)
    inputs.dir(rootProject.file("vendor/webp"))
    outputs.file(patchedFfmpeg)
    doLast {
        val destination = patchedFfmpeg.get().asFile.apply { parentFile.mkdirs() }
        ZipFile(ffmpegOriginal.singleFile).use { source ->
            ZipOutputStream(destination.outputStream()).use { target ->
                source.entries().asSequence().forEach { entry ->
                    var bytes = source.getInputStream(entry).use { it.readBytes() }
                    val abi = entry.name.split('/').getOrNull(1)
                    if (entry.name == "jni/$abi/libffmpeg.zip.so" && abi in setOf("arm64-v8a", "x86_64")) {
                        val buffer = ByteArrayOutputStream()
                        val originalArchive = layout.buildDirectory.file("native/input-$abi.zip").get().asFile.apply { writeBytes(bytes) }
                        UnixZipFile(originalArchive).use { archive ->
                            ZipArchiveOutputStream(buffer).use { output ->
                                val writer: org.apache.commons.compress.archivers.ArchiveOutputStream<ZipArchiveEntry> = output
                                archive.entries.asSequence().forEach { nested ->
                                    val replacement = rootProject.file("vendor/webp/$abi/${nested.name.substringAfterLast('/')}")
                                    writer.putArchiveEntry(ZipArchiveEntry(nested.name).apply { time = 0; unixMode = nested.unixMode })
                                    if (nested.name.startsWith("usr/lib/") && replacement.isFile) replacement.inputStream().use { it.copyTo(output) } else archive.getInputStream(nested).use { it.copyTo(output) }
                                    output.closeArchiveEntry()
                                }
                            }
                        }
                        originalArchive.delete()
                        bytes = buffer.toByteArray()
                    }
                    target.putNextEntry(ZipEntry(entry.name).apply { time = 0 }); target.write(bytes); target.closeEntry()
                }
            }
        }
    }
}
android {
    namespace = "io.github.nihil.orca"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.nihil.orca"
        minSdk = 34
        targetSdk = 35
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs { if (signing.isNotEmpty()) create("personal") {
        storeFile = file(signing.getProperty("storeFile")); storePassword = signing.getProperty("storePassword")
        keyAlias = signing.getProperty("keyAlias"); keyPassword = signing.getProperty("keyPassword")
    } }
    buildTypes { release { isMinifyEnabled = false; if (signing.isNotEmpty()) signingConfig = signingConfigs.getByName("personal") } }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = false } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = true; resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Expose the JSON parser already shipped by the download engine to the updater.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    add("ffmpegOriginal", "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1@aar")
    implementation(files(patchedFfmpeg).builtBy(prepareFfmpeg))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
