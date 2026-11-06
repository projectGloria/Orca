package io.github.nihil.orca

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineBackupTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture(): Pair<File, EngineBackup> {
        val binary = File(temporary.root, "upstream/yt-dlp").apply { parentFile!!.mkdirs(); writeText("working engine") }
        return binary to EngineBackup(binary, File(temporary.root, "orca-recovery"))
    }

    @Test fun updaterDeletingItsDirectoryDoesNotRemoveRecovery() {
        val (binary, backup) = fixture()
        backup.prepare()
        binary.parentFile!!.deleteRecursively()
        assertTrue(backup.snapshot.exists())
        assertTrue(backup.restore())
        assertEquals("working engine", binary.readText())
    }

    @Test fun partialUncommittedSnapshotNeverReplacesHealthyBinary() {
        val (binary, backup) = fixture()
        backup.snapshot.parentFile!!.mkdirs()
        File(backup.snapshot.parentFile, "engine-backup.tmp").writeText("partial")
        assertFalse(backup.restore())
        assertEquals("working engine", binary.readText())
    }

    @Test fun corruptedCommittedSnapshotNeverReplacesHealthyBinary() {
        val (binary, backup) = fixture()
        backup.prepare()
        backup.snapshot.writeText("truncated archive")
        assertThrows(Exception::class.java) { backup.restore() }
        assertEquals("working engine", binary.readText())
    }

    @Test fun restoringRemainsRepeatableUntilVerifiedCommit() {
        val (binary, backup) = fixture()
        backup.prepare()
        binary.writeText("broken update")
        assertTrue(backup.restore())
        assertTrue(backup.snapshot.exists())
        assertTrue(backup.restore())
        assertEquals("working engine", binary.readText())
        backup.commit()
        assertFalse(backup.restore())
    }

    @Test fun interruptedNewBackupKeepsPreviousCommittedSnapshot() {
        val (binary, backup) = fixture()
        backup.prepare()
        binary.writeText("new engine")
        File(backup.snapshot.parentFile, "engine-backup.tmp").writeText("partial new snapshot")
        assertTrue(backup.restore())
        assertEquals("working engine", binary.readText())
    }
}
