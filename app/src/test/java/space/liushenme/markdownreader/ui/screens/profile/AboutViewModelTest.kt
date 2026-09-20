package space.liushenme.markdownreader.ui.screens.profile

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.update.AppUpdateInfo
import space.liushenme.markdownreader.update.AppUpdateRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AboutViewModelTest {

    private val newer = AppUpdateInfo(
        versionName = "9.9.9",
        versionCode = 99,
        pageUrl = "https://example.com/app.apk",
    )

    @Test
    fun clickUpdateVersion_opensDialogWithoutFetchingAgain() = runBlocking {
        var calls = 0
        val repo = AppUpdateRepository(
            fetcher = {
                calls++
                newer
            },
            localVersionCode = 13,
        )
        repo.refresh()
        assertEquals(1, calls)

        val viewModel = AboutViewModel(repo)
        assertFalse(viewModel.showUpdateDialog.value)

        viewModel.onUpdateActionClick()
        assertTrue(viewModel.showUpdateDialog.value)
        assertEquals(1, calls)
    }

    @Test
    fun dismissUpdateDialog_keepsAvailableUpdate() = runBlocking {
        val repo = AppUpdateRepository(fetcher = { newer }, localVersionCode = 13)
        repo.refresh()
        val viewModel = AboutViewModel(repo)
        viewModel.onUpdateActionClick()
        viewModel.dismissUpdateDialog()
        assertFalse(viewModel.showUpdateDialog.value)
        assertEquals(newer, viewModel.availableUpdate.value)
    }
}
