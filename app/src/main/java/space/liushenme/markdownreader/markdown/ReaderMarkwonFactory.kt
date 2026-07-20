package space.liushenme.markdownreader.markdown

import android.content.Context
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/** 阅读器统一的 Markwon 实例（Markdown 书籍与 PDF 衍生 Markdown 共用）。 */
object ReaderMarkwonFactory {

    fun create(context: Context): Markwon {
        val appContext = context.applicationContext
        val metrics = appContext.resources.displayMetrics
        val fontScale = appContext.resources.configuration.fontScale
        val latexTextSize = 15f * metrics.density * fontScale
        return Markwon.builder(appContext)
            .usePlugin(CorePlugin.create())
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(latexTextSize) { builder ->
                    builder.inlinesEnabled(true)
                    builder.blocksEnabled(true)
                    ReaderLatexBlockStyle.configureBlockTheme(
                        context = appContext,
                        themeBuilder = builder.theme(),
                        density = metrics.density,
                    )
                },
            )
            // 在 JLatex 的 `$$...$$` 行内解析之后，补充 `$...$`（勿用预处理转成 $$，否则会块级换行）
            .usePlugin(ReaderSingleDollarLatexPlugin.create())
            .usePlugin(ReaderSpacingPlugin.create(appContext))
            .usePlugin(ReaderHtmlPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(appContext))
            .usePlugin(TaskListPlugin.create(appContext))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(
                ImagesPlugin.create { plugin ->
                    plugin
                        .addSchemeHandler(FileSchemeHandler.create())
                        .addSchemeHandler(CachedNetworkSchemeHandler.create(appContext))
                        .addSchemeHandler(DiagramSchemeHandler.create(appContext))
                        .placeholderProvider { drawable ->
                            NetworkImagePlaceholderProvider.provide(appContext, drawable)
                        }
                },
            )
            .usePlugin(DiagramImagesPlugin.create(appContext))
            .usePlugin(ReaderImagePlugin.create())
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.syntaxHighlight(ReaderSyntaxHighlight())
                        builder.linkResolver(ReaderLinkResolver())
                    }
                },
            )
            .build()
    }

    fun prepareMarkdown(source: String): PreparedMarkdown {
        val anchorIndex = MarkdownAnchorIndex.build(source)
        val text = MarkdownPreprocessor.prepare(source)
        return PreparedMarkdown(text = text, anchorIndex = anchorIndex)
    }
}
