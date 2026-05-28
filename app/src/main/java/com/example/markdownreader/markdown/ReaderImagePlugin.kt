package com.example.markdownreader.markdown

import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.SpanFactory
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageProps
import org.commonmark.node.Image

/** 覆盖 Markwon 默认图片 span：顶对齐，避免行距倍数在图片上方产生空白。 */
internal object ReaderImagePlugin {

    fun create(): MarkwonPlugin = object : AbstractMarkwonPlugin() {
        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
            builder.setFactory(Image::class.java, ReaderImageSpanFactory())
        }
    }

    private class ReaderImageSpanFactory : SpanFactory {
        override fun getSpans(configuration: MarkwonConfiguration, props: RenderProps): Any {
            return ReaderAsyncDrawableSpan(
                theme = configuration.theme(),
                drawable = AsyncDrawable(
                    ImageProps.DESTINATION.require(props),
                    configuration.asyncDrawableLoader(),
                    configuration.imageSizeResolver(),
                    ImageProps.IMAGE_SIZE.get(props),
                ),
                replacementTextIsLink = ImageProps.REPLACEMENT_TEXT_IS_LINK.get(props, false),
            )
        }
    }
}
