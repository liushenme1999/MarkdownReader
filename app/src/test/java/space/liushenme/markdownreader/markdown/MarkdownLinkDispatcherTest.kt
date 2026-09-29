package space.liushenme.markdownreader.markdown

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MarkdownLinkDispatcherTest {

    @After
    fun tearDown() {
        MarkdownLinkDispatcher.openUrl = null
        MarkdownLinkDispatcher.onRequestOpenWebUrl = null
        MarkdownLinkDispatcher.onBeforeOpenWebUrl = null
    }

    @Test
    fun openWebUrl_requestsConfirmationBeforeOpening() {
        val events = mutableListOf<String>()
        var proceed: (() -> Unit)? = null
        MarkdownLinkDispatcher.onBeforeOpenWebUrl = { events += "before" }
        MarkdownLinkDispatcher.openUrl = { events += "open:$it" }
        MarkdownLinkDispatcher.onRequestOpenWebUrl = { url, confirm ->
            events += "request:$url"
            proceed = confirm
        }

        MarkdownLinkDispatcher.openWebUrl("  https://example.com/path  ")

        assertEquals(listOf("request:https://example.com/path"), events)
        assertFalse(proceed == null)

        proceed!!.invoke()

        assertEquals(
            listOf(
                "request:https://example.com/path",
                "before",
                "open:https://example.com/path",
            ),
            events,
        )
    }

    @Test
    fun openWebUrl_withoutConfirmationHandlerKeepsDirectOpenBehavior() {
        val events = mutableListOf<String>()
        MarkdownLinkDispatcher.onBeforeOpenWebUrl = { events += "before" }
        MarkdownLinkDispatcher.openUrl = { events += "open:$it" }

        MarkdownLinkDispatcher.openWebUrl("https://example.com")

        assertEquals(listOf("before", "open:https://example.com"), events)
    }

    @Test
    fun openWebUrl_ignoresNonHttpSchemes() {
        var requested = false
        var opened = false
        MarkdownLinkDispatcher.onRequestOpenWebUrl = { _, _ -> requested = true }
        MarkdownLinkDispatcher.openUrl = { opened = true }

        MarkdownLinkDispatcher.openWebUrl("mailto:user@example.com")
        MarkdownLinkDispatcher.openWebUrl("ftp://example.com/file")
        MarkdownLinkDispatcher.openWebUrl("not a url")

        assertFalse(requested)
        assertFalse(opened)
    }

    @Test
    fun isWebUrl_acceptsOnlyHttpAndHttps() {
        assertTrue(MarkdownLinkDispatcher.isWebUrl("http://example.com"))
        assertTrue(MarkdownLinkDispatcher.isWebUrl("https://example.com"))
        assertFalse(MarkdownLinkDispatcher.isWebUrl("mailto:user@example.com"))
        assertFalse(MarkdownLinkDispatcher.isWebUrl("javascript:alert(1)"))
    }
}
