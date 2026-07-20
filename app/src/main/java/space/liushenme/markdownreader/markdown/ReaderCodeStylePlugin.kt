package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Color
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.SpanFactory
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Code

internal object ReaderCodeStylePlugin {

    fun create(context: Context): MarkwonPlugin = object : AbstractMarkwonPlugin() {
        private val appContext = context.applicationContext

        override fun configureTheme(builder: MarkwonTheme.Builder) {
            builder.codeBackgroundColor(Color.TRANSPARENT)
        }

        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
            builder.setFactory(Code::class.java, ReaderInlineCodeSpanFactory(appContext))
        }
    }

    private class ReaderInlineCodeSpanFactory(
        private val context: Context,
    ) : SpanFactory {
        override fun getSpans(configuration: MarkwonConfiguration, props: RenderProps): Any {
            return ReaderInlineCodeSpan(configuration.theme(), context)
        }
    }
}
