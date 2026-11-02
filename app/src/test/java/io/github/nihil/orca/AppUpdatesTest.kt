package io.github.nihil.orca

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private val mapper = ObjectMapper()
    private val release = mapper.readTree("""{"tag_name":"v0.1.1","draft":false,"prerelease":false,"assets":[
        {"name":"Orca-0.1.1-arm64.apk","browser_download_url":"https://github.com/projectGloria/Orca/releases/download/v0.1.1/Orca-0.1.1-arm64.apk"},
        {"name":"Orca-0.1.1-x86_64.apk","browser_download_url":"https://github.com/projectGloria/Orca/releases/download/v0.1.1/Orca-0.1.1-x86_64.apk"}]}""")
    private fun metadata(code: Int = 2) = mapper.readTree("""{"schemaVersion":1,"applicationId":"io.github.nihil.orca","versionName":"0.1.1","versionCode":$code,"tag":"v0.1.1","assets":{
        "arm64-v8a":{"name":"Orca-0.1.1-arm64.apk"},"x86_64":{"name":"Orca-0.1.1-x86_64.apk"}}}""")
    @Test fun olderInstallGetsArm64Asset() {
        val result = parseUpdate(release, metadata(), 1, listOf("arm64-v8a", "armeabi-v7a")) as AppUpdateResult.Available
        assertTrue(result.release.downloadUrl.endsWith("-arm64.apk"))
    }
    @Test fun emulatorGetsX86Asset() {
        val result = parseUpdate(release, metadata(), 1, listOf("x86_64", "x86")) as AppUpdateResult.Available
        assertTrue(result.release.downloadUrl.endsWith("-x86_64.apk"))
    }
    @Test fun sameOrOlderReleaseNeverOffersDowngrade() {
        assertEquals(AppUpdateResult.Current, parseUpdate(release, metadata(), 2, listOf("arm64-v8a")))
        assertEquals(AppUpdateResult.Current, parseUpdate(release, metadata(), 9, listOf("arm64-v8a")))
    }
    @Test fun missingApkIsPreparing() {
        val withoutAssets = release.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().putArray("assets")
        val root = release.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply { set<com.fasterxml.jackson.databind.JsonNode>("assets", withoutAssets) }
        assertEquals(AppUpdateResult.Preparing, parseUpdate(root, metadata(), 1, listOf("arm64-v8a")))
    }
    @Test fun rejectsWrongAppAndWrongTag() {
        val wrong = metadata() as com.fasterxml.jackson.databind.node.ObjectNode
        wrong.put("applicationId", "another.app")
        assertThrows(IllegalArgumentException::class.java) { parseUpdate(release, wrong, 1, listOf("arm64-v8a")) }
        val tag = metadata() as com.fasterxml.jackson.databind.node.ObjectNode
        tag.put("tag", "v9.0.0")
        assertThrows(IllegalArgumentException::class.java) { parseUpdate(release, tag, 1, listOf("arm64-v8a")) }
    }
    @Test fun rejectsWrongHostAndRepository() {
        assertThrows(IllegalArgumentException::class.java) { requireReleaseUrl("https://github.com.evil.test/projectGloria/Orca/releases/download/v0.1.1/a.apk", "v0.1.1") }
        assertThrows(IllegalArgumentException::class.java) { requireReleaseUrl("https://github.com/other/Orca/releases/download/v0.1.1/a.apk", "v0.1.1") }
        assertThrows(IllegalArgumentException::class.java) { requireReleaseUrl("http://github.com/projectGloria/Orca/releases/download/v0.1.1/a.apk", "v0.1.1") }
    }
    @Test fun rejectsPrereleaseAndUnsupportedDevice() {
        val pre = release.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("prerelease", true)
        assertThrows(IllegalArgumentException::class.java) { parseUpdate(pre, metadata(), 1, listOf("arm64-v8a")) }
        assertThrows(IllegalStateException::class.java) { parseUpdate(release, metadata(), 1, listOf("x86")) }
    }
}
