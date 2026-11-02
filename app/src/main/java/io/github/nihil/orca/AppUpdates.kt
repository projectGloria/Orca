package io.github.nihil.orca

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

data class AppRelease(val versionName: String, val versionCode: Long, val downloadUrl: String, val pageUrl: String)
sealed interface AppUpdateResult {
    data object Current : AppUpdateResult
    data object NoRelease : AppUpdateResult
    data object Preparing : AppUpdateResult
    data class Available(val release: AppRelease) : AppUpdateResult
}

/** Public release metadata only. No GitHub token, automatic download or installer permission. */
class GitHubAppUpdates {
    suspend fun check(installedCode: Long, abis: List<String>): AppUpdateResult = withContext(Dispatchers.IO) {
        val release = getJson("https://api.github.com/repos/$REPOSITORY/releases/latest") ?: return@withContext AppUpdateResult.NoRelease
        val assets = release.path("assets").toList()
        val manifest = assets.firstOrNull { it.path("name").asText() == "orca-update.json" }
            ?: return@withContext AppUpdateResult.Preparing
        val manifestUrl = manifest.path("browser_download_url").asText()
        requireReleaseUrl(manifestUrl, release.path("tag_name").asText())
        val metadata = getJson(manifestUrl) ?: return@withContext AppUpdateResult.Preparing
        parseUpdate(release, metadata, installedCode, abis)
    }

    private fun getJson(url: String): JsonNode? {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Orca/${BuildConfig.VERSION_NAME}")
            return when (connection.responseCode) {
                404 -> null
                403, 429 -> error("GitHub is limiting update checks. Try again later.")
                200 -> {
                    val bytes = connection.inputStream.use { it.readNBytes(1_048_577) }
                    check(bytes.size <= 1_048_576) { "The release metadata is too large." }
                    ObjectMapper().readTree(bytes)
                }
                else -> error("Could not check for updates. Try again later.")
            }
        } finally { connection.disconnect() }
    }

    companion object { const val REPOSITORY = "projectGloria/Orca" }
}

internal fun requireReleaseUrl(url: String, tag: String) {
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 && uri.fragment == null && uri.query == null) { "The release contains an invalid download link." }
    require(uri.path.startsWith("/${GitHubAppUpdates.REPOSITORY}/releases/download/$tag/")) { "The release download does not belong to Orca." }
}

internal fun parseUpdate(release: JsonNode, metadata: JsonNode, installedCode: Long, abis: List<String>): AppUpdateResult {
    require(!release.path("draft").asBoolean() && !release.path("prerelease").asBoolean()) { "This is not a stable release." }
    val tag = release.path("tag_name").asText()
    require(metadata.path("schemaVersion").asInt() == 1 && metadata.path("applicationId").asText() == "io.github.nihil.orca") { "The release metadata is not compatible with Orca." }
    require(metadata.path("tag").asText() == tag) { "The release version could not be verified." }
    val code = metadata.path("versionCode").asLong()
    require(code > 0) { "The release has an invalid version." }
    if (code <= installedCode) return AppUpdateResult.Current
    val name = metadata.path("versionName").asText()
    require(tag == "v$name" && name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "The release has an invalid version name." }
    val supported = metadata.path("assets")
    val abi = abis.firstOrNull { it in setOf("arm64-v8a", "x86_64") && supported.has(it) }
        ?: error("No compatible update APK is available for this device.")
    val assetName = supported.path(abi).path("name").asText()
    val suffix = if (abi == "arm64-v8a") "arm64" else "x86_64"
    require(assetName == "Orca-$name-$suffix.apk") { "The release APK name is invalid." }
    val asset = release.path("assets").firstOrNull { it.path("name").asText() == assetName }
        ?: return AppUpdateResult.Preparing
    val url = asset.path("browser_download_url").asText()
    requireReleaseUrl(url, tag)
    return AppUpdateResult.Available(AppRelease(name, code, url, "https://github.com/${GitHubAppUpdates.REPOSITORY}/releases/tag/$tag"))
}
