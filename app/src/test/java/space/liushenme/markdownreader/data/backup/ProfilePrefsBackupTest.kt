package space.liushenme.markdownreader.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 按格式的阅读样式键必须随 WebDAV 备份同步，否则换设备会丢样式。 */
class ProfilePrefsBackupTest {

    @Test
    fun profileScopedReadingStyleKeysAreBackedUp() {
        val keys = listOf(
            "reader_reading_theme@markdown",
            "reader_reading_styles@pdf",
            "reader_text_color@code",
            "reader_bg_color@txt",
            "reader_font_size@pdf",
            "reader_reader_padding_dp@code",
            "reader_line_spacing_multiplier@html",
            "reader_code_block_wrap@markdown",
            "reader_code_line_numbers@code",
            "reader_code_highlight_theme@markdown",
            "reader_page_turn_mode@pdf",
        )
        keys.forEach { key ->
            assertTrue("$key 应进入备份", isSharedPreferenceKeyBackedUp(key))
        }
    }

    @Test
    fun deviceLocalAndWebDavKeysStayOutOfBackup() {
        assertFalse(isSharedPreferenceKeyBackedUp("bookshelf_layout_mode"))
        assertFalse(isSharedPreferenceKeyBackedUp("bookshelf_grid_columns"))
    }
}
