package space.liushenme.markdownreader.markdown

/** 供 [ReaderTableRowSpan] 在 layout 阶段抵消 TextView 行距倍数对表格行的撑高。 */
internal object ReaderTableSpacing {
    @Volatile
    var lineSpacingMultiplier: Float = 1.5f
}
