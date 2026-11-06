package io.github.nihil.orca

import org.junit.Assert.*
import org.junit.Test

class SelectionTest {
    @Test fun bestDoesNotCapOrExcludeCombinedStreams() { assertEquals("bestvideo*+bestaudio/best", formatSelector(DownloadOptions())) }
    @Test fun audioPrefersOriginalAudio() { assertEquals("bestaudio/best", formatSelector(DownloadOptions(audio = true))) }
    @Test fun limitAppliesToMergedAndCombinedFallback() { assertEquals("bestvideo*[height<=1080]+bestaudio/best[height<=1080]", formatSelector(DownloadOptions(height = 1080))) }
    @Test fun portraitIsNotRelabeledAsLandscape() { assertEquals("1920p · Portrait", Quality(1920, 1080, null).label) }
    @Test fun extractsEmbeddedSharedLink() { assertEquals("https://youtu.be/abc?list=123", LinkParser.fromText("Watch this: https://youtu.be/abc?list=123.")) }
    @Test fun rejectsNonWebLinks() { assertNull(LinkParser.fromText("file:///data/local/file")); assertNull(LinkParser.fromText("nothing")) }
    @Test fun videoWithPlaylistContextIsNotPlaylistOnly() { assertFalse(LinkParser.isPlaylistOnly("https://www.youtube.com/watch?v=abc&list=def")); assertTrue(LinkParser.hasPlaylist("https://www.youtube.com/watch?v=abc&list=def")); assertTrue(LinkParser.isPlaylistOnly("https://www.youtube.com/playlist?list=def")) }
    @Test fun arbitraryDomainIsNotYoutubePlaylist() { assertFalse(LinkParser.isPlaylistOnly("https://notyoutube.com/playlist?list=def")) }
    @Test fun unicodeFileNamesFitMediaStoreLimits() {
        val name = safeFileTitle("海🌊".repeat(100))
        assertTrue(name.toByteArray(Charsets.UTF_8).size + 45 < 255)
        assertFalse(name.last().isHighSurrogate())
        assertEquals("a_b_c", safeFileTitle("a/b:c"))
    }
    @Test fun sourceNamesDoNotTriggerStorageErrors() {
        assertFalse(friendlyError(Exception("SpaceX video unavailable")).contains("Storage"))
        assertTrue(friendlyError(Exception("No space left on device")).contains("Storage"))
        assertTrue(friendlyError(Exception("Your IP address is blocked from accessing this post")).contains("network or region"))
    }
    @Test fun forbiddenMediaSuggestsEngineUpdateWithoutAssumingLogin() {
        val message = friendlyError(Exception("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertTrue(message.contains("engine update"))
        assertFalse(message.contains("requires access"))
        assertTrue(friendlyError(Exception("HTTP Error 403: sign in to view this private video")).contains("requires access"))
        assertTrue(friendlyError(Exception("HTTP Error 403: Your IP address is blocked")).contains("network or region"))
    }
}
