package space.liushenme.markdownreader.model

import androidx.annotation.StringRes
import space.liushenme.markdownreader.R

/**
 * 阅读翻页 / 浏览方式（全局偏好）。
 */
enum class ReaderPageTurnMode(
    @StringRes val labelRes: Int,
    @StringRes val hintRes: Int,
) {
    /** 单页纵向滚动（默认） */
    VerticalScroll(R.string.page_turn_vertical_scroll, R.string.page_turn_vertical_scroll_hint),

    /** 横向分页滑动 */
    HorizontalSwipe(R.string.page_turn_horizontal_swipe, R.string.page_turn_horizontal_swipe_hint),

    /** 横向分页 + 绕边旋转的类仿真效果 */
    SimulationPageTurn(R.string.page_turn_simulation, R.string.page_turn_simulation_hint),

    /** 横向分页 + 整页平移覆盖 */
    CoverPageTurn(R.string.page_turn_cover, R.string.page_turn_cover_hint),
}
