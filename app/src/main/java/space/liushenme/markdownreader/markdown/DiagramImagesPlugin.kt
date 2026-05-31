package space.liushenme.markdownreader.markdown

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.image.AsyncDrawableSpan

/**
 * 在 Markwon 完成 setText 后，对正文中的 diagram:// [AsyncDrawable] 启动非阻塞渲染。
 */
internal class DiagramImagesPlugin(
    private val context: Context,
) : AbstractMarkwonPlugin() {

    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        cancelDiagramDrawables(textView)
    }

    override fun afterSetText(textView: TextView) {
        DiagramImageLoader.scheduleForTextView(textView)
    }

    private fun scheduleDiagramDrawables(textView: TextView, attempt: Int) {
        DiagramImageLoader.scheduleForTextView(textView, attempt)
    }

    private fun cancelDiagramDrawables(textView: TextView) {
        val text = textView.text
        if (text !is Spanned) return
        val spans = text.getSpans(0, text.length, AsyncDrawableSpan::class.java) ?: return
        for (span in spans) {
            val drawable = span.drawable
            if (drawable.destination.startsWith("${DiagramSchemeHandler.SCHEME}://")) {
                DiagramImageLoader.cancel(drawable)
            }
        }
    }

    companion object {
        fun create(context: Context): DiagramImagesPlugin =
            DiagramImagesPlugin(context.applicationContext)
    }
}
