package space.liushenme.markdownreader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun parseLatestJson_readsFields() {
        val info = AppUpdateChecker.parseLatestJson(
            """
            {
              "versionName": "1.2.0",
              "versionCode": 13,
              "pageUrl": "https://github.com/liushenme1999/MarkdownReader/blob/main/app/release/MD阅读器_release_1.2.0.apk"
            }
            """.trimIndent(),
        )
        requireNotNull(info)
        assertEquals("1.2.0", info.versionName)
        assertEquals(13, info.versionCode)
        assertTrue(info.pageUrl.startsWith("https://github.com/"))
    }

    @Test
    fun isNewerThan_comparesVersionCode() {
        val info = AppUpdateInfo(
            versionName = "1.2.0",
            versionCode = 13,
            pageUrl = "https://example.com/app.apk",
        )
        assertTrue(info.isNewerThan(12))
        assertFalse(info.isNewerThan(13))
        assertFalse(info.isNewerThan(14))
    }

    @Test
    fun parseLatestJson_rejectsMissingFields() {
        assertNull(AppUpdateChecker.parseLatestJson("{}"))
        assertNull(AppUpdateChecker.parseLatestJson("""{"versionName":"1.0.0"}"""))
        assertNull(
            AppUpdateChecker.parseLatestJson(
                """{"versionName":"1.0.0","versionCode":1,"pageUrl":"ftp://example.com/a.apk"}""",
            ),
        )
        assertNull(AppUpdateChecker.parseLatestJson("not-json"))
    }

    @Test
    fun candidateUrls_areHttpsMirrors() {
        assertTrue(AppUpdateChecker.CANDIDATE_URLS.size >= 2)
        assertEquals(AppUpdateChecker.LATEST_JSON_URL, AppUpdateChecker.CANDIDATE_URLS.first())
        AppUpdateChecker.CANDIDATE_URLS.forEach { url ->
            assertTrue(url, url.startsWith("https://"))
            assertTrue(url, url.endsWith("latest.json"))
        }
    }
}
