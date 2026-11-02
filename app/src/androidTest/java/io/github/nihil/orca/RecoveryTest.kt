package io.github.nihil.orca

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RecoveryTest {
    @Test fun restartRequiresResumeAndRetainsPublicationReceipt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, OrcaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dao = database.downloads()
            val pending = DownloadItem(url = "https://example.org/video.mp4", state = "FINISHING", staging = "/retained/media.mp4", uri = "content://media/external/video/media/1")
            val completed = DownloadItem(url = "https://example.org/other.mp4", state = "COMPLETED")
            dao.put(pending); dao.put(completed)
            val repo = DownloadRepository(context, dao, YtDlpEngine(context), scope)
            assertNull(repo.next())
            assertEquals("INTERRUPTED", dao.get(pending.id)?.state)
            assertEquals(pending.staging, dao.get(pending.id)?.staging)
            assertEquals(pending.uri, dao.get(pending.id)?.uri)
            assertEquals("COMPLETED", dao.get(completed.id)?.state)
        } finally { scope.cancel(); database.close() }
    }
}
