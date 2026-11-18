package space.liushenme.markdownreader.ui.screens.reader

import space.liushenme.markdownreader.data.local.entity.BookmarkEntity

/** 无可见范围时，判定「同一位置」允许的字符误差。 */
internal const val BOOKMARK_SAME_POSITION_EPSILON = 2

/**
 * 「下拉/右滑加书签」时决定是新增还是取消。
 *
 * 规则（[position] / [rangeStart] / [rangeEnd] 都是全书字符坐标）：
 * 1. 优先按**当前可见范围**判断：范围内已有书签 → 取消它；否则新增。
 *    分页模式传当前页范围，滚动模式传视口范围。
 * 2. 没有范围时退化为「几乎同一位置」（±[BOOKMARK_SAME_POSITION_EPSILON] 字符）。
 *
 * 历史 bug：曾用 `(total / 40).coerceIn(300, 1500)` 的字符窗口去重，
 * 长书里 1-2 页（约 600~1200 字）仍在窗口内，于是「在下一页加书签」会把上一页的书签删掉，
 * 提示也变成「已取消书签」。
 */
internal fun findBookmarkToToggle(
    bookmarks: List<BookmarkEntity>,
    position: Int,
    rangeStart: Int? = null,
    rangeEnd: Int? = null,
): BookmarkEntity? {
    if (rangeStart != null && rangeEnd != null && rangeEnd > rangeStart) {
        return bookmarks.firstOrNull { it.position in rangeStart until rangeEnd }
    }
    return bookmarks.firstOrNull {
        kotlin.math.abs(it.position - position) <= BOOKMARK_SAME_POSITION_EPSILON
    }
}
