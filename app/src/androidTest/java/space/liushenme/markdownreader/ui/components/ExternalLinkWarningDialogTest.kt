package space.liushenme.markdownreader.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import space.liushenme.markdownreader.R
import androidx.test.core.app.ApplicationProvider

@RunWith(AndroidJUnit4::class)
class ExternalLinkWarningDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun cancelDismissesWithoutConfirming() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                ExternalLinkWarningDialog(
                    url = "https://example.com/a-long-path",
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()

        assertFalse(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun confirmInvokesConfirmCallbackAndShowsFullUrl() {
        var confirmed = false
        val url = "https://example.com/a-long-path?query=value"
        composeRule.setContent {
            MaterialTheme {
                ExternalLinkWarningDialog(
                    url = url,
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(url).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.external_link_warning_continue))
            .performClick()

        assertTrue(confirmed)
    }

    @Test
    fun copyButtonDoesNotConfirm() {
        var confirmed = false
        val url = "https://example.com/a-long-path?query=value"
        composeRule.setContent {
            MaterialTheme {
                ExternalLinkWarningDialog(
                    url = url,
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.external_link_warning_copy))
            .performClick()

        assertFalse(confirmed)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(url, clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
    }
}
