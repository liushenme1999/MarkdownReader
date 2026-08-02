package space.liushenme.markdownreader.model

import androidx.annotation.StringRes
import space.liushenme.markdownreader.R

enum class BookshelfLayoutMode(
    @StringRes val labelRes: Int,
) {
    Grid(R.string.bookshelf_layout_mode_grid),
    List(R.string.bookshelf_layout_mode_list),
    ;

    companion object {
        fun fromStored(raw: String?): BookshelfLayoutMode =
            entries.find { it.name == raw } ?: Grid
    }
}

object BookshelfGridColumns {
    val OPTIONS = listOf(3, 4, 5)
    const val DEFAULT = 3

    fun coerce(value: Int): Int = when (value) {
        in OPTIONS -> value
        else -> DEFAULT
    }
}

/** 项目浏览器「继续阅读」折叠时展示条数。 */
object GitProjectRecentReadCount {
    val OPTIONS = listOf(1, 2, 3, 4, 5)
    const val DEFAULT = 3

    fun coerce(value: Int): Int = when (value) {
        in OPTIONS -> value
        else -> DEFAULT
    }
}
