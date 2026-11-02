package io.github.nihil.orca

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Build
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in check against the real published Actions release, never a dependency of offline tests. */
class AppUpdatesLiveTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    @Test fun officialReleaseSupportsCurrentAndOlderInstalls(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdates") == "true")
        val updates = GitHubAppUpdates()
        val abis = Build.SUPPORTED_ABIS.toList()
        assertEquals(AppUpdateResult.Current, updates.check(BuildConfig.VERSION_CODE.toLong(), abis))
        val result = updates.check(1, abis) as AppUpdateResult.Available
        assertTrue(result.release.versionCode > 1)
        assertTrue(result.release.downloadUrl.startsWith("https://github.com/projectGloria/Orca/releases/download/"))
    }
}
