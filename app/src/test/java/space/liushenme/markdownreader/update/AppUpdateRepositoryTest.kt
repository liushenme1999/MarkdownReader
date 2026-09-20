package space.liushenme.markdownreader.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateRepositoryTest {

    private val newer = AppUpdateInfo(
        versionName = "9.9.9",
        versionCode = 99,
        pageUrl = "https://example.com/app.apk",
    )

    @Test
    fun refresh_keepsNewerReleaseUntilAppIsUpdated() = runBlocking {
        val repo = AppUpdateRepository(fetcher = { newer }, localVersionCode = 13)
        val result = repo.refresh()
        assertTrue(result is AppUpdateRepository.CheckResult.Newer)
        assertEquals(newer, repo.availableUpdate.value)
    }

    @Test
    fun refresh_clearsBadgeWhenAlreadyLatest() = runBlocking {
        val latest = AppUpdateInfo("1.1.1", 13, "https://example.com/app.apk")
        val repo = AppUpdateRepository(
            fetcher = object : AppUpdateFetcher {
                private var calls = 0
                override fun fetchLatest(): AppUpdateInfo {
                    calls++
                    return if (calls == 1) newer else latest
                }
            },
            localVersionCode = 13,
        )
        repo.refresh()
        assertEquals(newer, repo.availableUpdate.value)
        val again = repo.refresh()
        assertTrue(again is AppUpdateRepository.CheckResult.AlreadyLatest)
        assertNull(repo.availableUpdate.value)
    }

    @Test
    fun refresh_failedCheckDoesNotClearExistingUpdate() = runBlocking {
        val repo = AppUpdateRepository(
            fetcher = object : AppUpdateFetcher {
                private var calls = 0
                override fun fetchLatest(): AppUpdateInfo {
                    calls++
                    if (calls == 1) return newer
                    error("network down")
                }
            },
            localVersionCode = 13,
        )
        repo.refresh()
        val failed = repo.refresh()
        assertTrue(failed is AppUpdateRepository.CheckResult.Failed)
        assertEquals(newer, repo.availableUpdate.value)
    }
}
