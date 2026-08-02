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

    /** 已下线：横向分页 + 仿真效果（仅兼容旧偏好存储） */
    SimulationPageTurn(R.string.page_turn_simulation, R.string.page_turn_simulation_hint),

    /** 已下线：横向分页 + 覆盖效果（仅兼容旧偏好存储） */
    CoverPageTurn(R.string.page_turn_cover, R.string.page_turn_cover_hint),
    ;

    companion object {
        /** 设置页与阅读器内可选的翻页方式 */
        val selectableModes: List<ReaderPageTurnMode> = listOf(
            VerticalScroll,
            HorizontalSwipe,
        )

        fun fromStored(raw: String?): ReaderPageTurnMode {
            val parsed = entries.find { it.name == raw } ?: VerticalScroll
            return normalize(parsed)
        }

        /** 将已下线模式归一为左右滑动 */
        fun normalize(mode: ReaderPageTurnMode): ReaderPageTurnMode = when (mode) {
            SimulationPageTurn, CoverPageTurn -> HorizontalSwipe
            else -> mode
        }
    }
}
