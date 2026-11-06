package io.github.nihil.orca

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A committed, checksummed snapshot outside the directory owned by the upstream updater. */
internal class EngineBackup(private val binary: File, private val directory: File) {
    val snapshot = File(directory, "engine-backup.zip")

    fun prepare() {
        check(binary.isFile && binary.length() > 0) { "No working engine to back up." }
        directory.mkdirs()
        val temporary = File(directory, "engine-backup.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                val archive = ZipOutputStream(stream)
                archive.putNextEntry(ZipEntry("engine"))
                binary.inputStream().use { it.copyTo(archive) }
                archive.closeEntry()
                archive.putNextEntry(ZipEntry("sha256"))
                archive.write(digest(binary).toByteArray(Charsets.US_ASCII))
                archive.closeEntry()
                archive.finish()
                stream.fd.sync()
            }
            atomicReplace(temporary, snapshot)
        } finally { temporary.delete() }
    }

    /** Invalid or unfinished snapshots never replace the installed engine. */
    fun restore(): Boolean {
        if (!snapshot.exists()) return false
        binary.parentFile!!.mkdirs()
        val temporary = File(binary.parentFile, "yt-dlp.restore")
        try {
            ZipFile(snapshot).use { archive ->
                val payload = requireNotNull(archive.getEntry("engine"))
                val expected = archive.getInputStream(requireNotNull(archive.getEntry("sha256"))).bufferedReader().use { it.readText() }
                FileOutputStream(temporary).use { output ->
                    archive.getInputStream(payload).use { it.copyTo(output) }
                    output.fd.sync()
                }
                check(temporary.length() > 0 && digest(temporary) == expected) { "Engine backup is incomplete." }
            }
            atomicReplace(temporary, binary)
            return true
        } finally { temporary.delete() }
    }

    fun commit() { check(!snapshot.exists() || snapshot.delete()) { "Could not finish the engine update." } }

    private fun atomicReplace(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
