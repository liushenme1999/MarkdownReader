package space.liushenme.markdownreader.model

/**
 * 阅读翻页 / 浏览方式（全局偏好）。
 */
enum class ReaderPageTurnMode(val label: String) {
    /** 单页纵向滚动（默认） */
    VerticalScroll("上下滚动"),

    /** 横向分页滑动 */
    HorizontalSwipe("左右滑动"),

    /** 横向分页 + 绕边旋转的类仿真效果 */
    SimulationPageTurn("仿真翻页"),

    /** 横向分页 + 整页平移覆盖 */
    CoverPageTurn("覆盖翻页"),
}
