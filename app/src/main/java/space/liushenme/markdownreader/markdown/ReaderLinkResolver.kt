package space.liushenme.markdownreader.markdown

import android.net.Uri
import android.view.View
import android.widget.TextView
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.screens.reader.applyPendingScrollToCharOffset
import io.noties.markwon.LinkResolver
import io.noties.markwon.LinkResolverDef

internal class ReaderLinkResolver : LinkResolver {

    override fun resolve(view: View, link: String) {
        if (link.startsWith("#")) {
            val slug = Uri.decode(link.removePrefix("#"))
            val tv = view as? TextView ?: return
            val index = tv.getTag(R.id.markdown_anchor_index) as? MarkdownAnchorIndex ?: return
            val offset = RenderedAnchorBinder.offsetForSlug(tv, slug, index)
            if (offset != null) {
                applyPendingScrollToCharOffset(tv, offset)
            }
            return
        }
        if (MarkdownLinkDispatcher.isWebUrl(link)) {
            MarkdownLinkDispatcher.openWebUrl(link)
            return
        }
        // 其余 scheme（如 mailto:）仍走系统默认处理
        LinkResolverDef().resolve(view, link)
    }
}
