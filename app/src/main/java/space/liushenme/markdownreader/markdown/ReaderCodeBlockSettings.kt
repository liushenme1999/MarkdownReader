package space.liushenme.markdownreader.markdown

/** 渲染代码块前写入，供 Markwon span factory 读取。 */
internal object ReaderCodeBlockSettings {
    @Volatile
    var wrapEnabled: Boolean = true

    /** 代码窗口可视宽度（已扣 TextView 左右 padding）。 */
    @Volatile
    var viewportWidthPx: Int = 0
}
