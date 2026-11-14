package space.liushenme.markdownreader.markdown

/** 渲染代码块前写入，供 Markwon span factory 读取。 */
internal object ReaderCodeBlockSettings {
    @Volatile
    var wrapEnabled: Boolean = true

    /** 代码块左侧是否显示行号（Markdown 代码块与纯代码文件共用该开关）。 */
    @Volatile
    var showLineNumbers: Boolean = true

    /** 代码窗口可视宽度（已扣 TextView 左右 padding）。 */
    @Volatile
    var viewportWidthPx: Int = 0

    /**
     * 阅读器 TextView 当前可视窗口在正文布局中的顶部，以及窗口高度。
     *
     * Hardware-rendered TextViews may hand ReplacementSpan.draw a Canvas whose clip still
     * spans the whole laid-out document. Using that clip for a full-screen code block makes
     * every scroll frame walk tens of thousands of lines, so SafeReaderTextView publishes the
     * tight viewport here immediately before drawing.
     */
    @Volatile
    var viewportTopPx: Int = 0

    @Volatile
    var viewportHeightPx: Int = 0
}
