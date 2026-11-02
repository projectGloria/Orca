package io.github.nihil.orca

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Invoked in two separate instrumentation processes by scripts/test-process-recovery.ps1. */
class ProcessRecoveryTest {
    @Test fun recoverPersistedTransferAfterForceStop(): Unit = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        assumeTrue(phase == "seed" || phase == "verify")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = context.repository
        val receipt = File(context.filesDir, "recovery-test-id")
        if (phase == "seed") {
            repo.next() // wait for initial reconciliation
            val item = DownloadItem(url = "https://example.org/recovery.mp4", title = "Process recovery test", state = "DOWNLOADING")
            val partial = File(context.filesDir, "staging/${item.id}/media.mp4.part").apply { parentFile!!.mkdirs(); writeText("recoverable partial") }
            repo.dao.put(item.copy(staging = partial.absolutePath))
            receipt.writeText(item.id)
        } else {
            repo.next()
            val item = repo.dao.get(receipt.readText())!!
            assertEquals("INTERRUPTED", item.state)
            assertEquals("recoverable partial", File(item.staging).readText())
            repo.delete(item); receipt.delete()
        }
    }
}
