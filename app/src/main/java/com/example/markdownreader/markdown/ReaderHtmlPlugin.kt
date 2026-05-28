package com.example.markdownreader.markdown

import android.graphics.Color
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.RenderProps
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.tag.SimpleTagHandler

/** 扩展 HTML：&lt;mark&gt; 高亮、&lt;u&gt; 下划线。 */
internal object ReaderHtmlPlugin {

    fun create(): MarkwonPlugin = HtmlPlugin.create { plugin ->
        plugin.addHandler(MarkHighlightTagHandler())
        plugin.addHandler(MarkUnderlineTagHandler())
    }

    private class MarkHighlightTagHandler : SimpleTagHandler() {
        override fun supportedTags(): Collection<String> = listOf("mark")

        override fun getSpans(
            configuration: MarkwonConfiguration,
            renderProps: RenderProps,
            tag: HtmlTag,
        ): Any = BackgroundColorSpan(Color.parseColor("#FFF59D"))
    }

    private class MarkUnderlineTagHandler : SimpleTagHandler() {
        override fun supportedTags(): Collection<String> = listOf("u")

        override fun getSpans(
            configuration: MarkwonConfiguration,
            renderProps: RenderProps,
            tag: HtmlTag,
        ): Any = UnderlineSpan()
    }
}
