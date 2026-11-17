package space.liushenme.markdownreader.model

import androidx.annotation.StringRes
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.repository.ReadingFormatProfile

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

        /**
         * 指定格式可选的翻页方式。
         *
         * Markdown 只保留上下滚动：标题、表格、代码块、公式等块级元素在横向分页里会被切页，
         * 阅读体验差，因此不再提供左右滑动。
         */
        fun selectableModesFor(profile: ReadingFormatProfile): List<ReaderPageTurnMode> =
            if (profile == ReadingFormatProfile.MARKDOWN) {
                listOf(VerticalScroll)
            } else {
                selectableModes
            }

        /** 该格式是否支持横向翻页（Markdown 不支持）。 */
        fun supportsHorizontal(profile: ReadingFormatProfile): Boolean =
            selectableModesFor(profile).contains(HorizontalSwipe)

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
