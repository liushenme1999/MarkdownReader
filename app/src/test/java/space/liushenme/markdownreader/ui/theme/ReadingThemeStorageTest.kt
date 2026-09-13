package space.liushenme.markdownreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import space.liushenme.markdownreader.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingThemeStorageTest {

    @Test
    fun fromLegacyName_mapsKnownPresets() {
        assertEquals(ReadingTheme.Paper, ReadingThemeStorage.fromLegacyName("Paper"))
        assertEquals(ReadingTheme.Dark, ReadingThemeStorage.fromLegacyName("Dark"))
        assertEquals(ReadingTheme.White, ReadingThemeStorage.fromLegacyName("White"))
        assertEquals(ReadingTheme.Green, ReadingThemeStorage.fromLegacyName("Green"))
        assertEquals(ReadingTheme.Sepia, ReadingThemeStorage.fromLegacyName("Sepia"))
    }

    @Test
    fun fromLegacyName_unknownFallsBackToPaper() {
        assertEquals(ReadingTheme.Paper, ReadingThemeStorage.fromLegacyName(null))
        assertEquals(ReadingTheme.Paper, ReadingThemeStorage.fromLegacyName("Unknown"))
    }

    @Test
    fun fromStored_migratesLegacyWhenNewKeysMissing() {
        val theme = ReadingThemeStorage.fromStored(
            textColorArgb = null,
            bgColorArgb = null,
            bgAlpha = null,
            bgImage = null,
            legacyName = "Dark",
        )
        assertEquals(ReadingTheme.Dark.textColor.toArgb(), theme.textColor.toArgb())
        assertEquals(ReadingTheme.Dark.backgroundColor.toArgb(), theme.backgroundColor.toArgb())
        assertEquals(100, theme.backgroundAlpha)
        assertNull(theme.backgroundImageAsset)
    }

    @Test
    fun fromStored_prefersNewKeysOverLegacyName() {
        val text = Color(0xFF112233).toArgb()
        val bg = Color(0xFF445566).toArgb()
        val theme = ReadingThemeStorage.fromStored(
            textColorArgb = text,
            bgColorArgb = bg,
            bgAlpha = 40,
            bgImage = "羊皮纸1.jpg",
            legacyName = "Paper",
        )
        assertEquals(text, theme.textColor.toArgb())
        assertEquals(bg, theme.backgroundColor.toArgb())
        assertEquals(40, theme.backgroundAlpha)
        assertEquals("羊皮纸1.jpg", theme.backgroundImageAsset)
    }

    @Test
    fun clampAlpha_limitsToZeroHundred() {
        assertEquals(0, ReadingThemeStorage.clampAlpha(-12))
        assertEquals(100, ReadingThemeStorage.clampAlpha(250))
        assertEquals(73, ReadingThemeStorage.clampAlpha(73))
    }

    @Test
    fun normalizeImageAsset_treatsBlankAsNone() {
        assertNull(ReadingThemeStorage.normalizeImageAsset(null))
        assertNull(ReadingThemeStorage.normalizeImageAsset(""))
        assertNull(ReadingThemeStorage.normalizeImageAsset("   "))
        assertEquals("护眼漫绿.jpg", ReadingThemeStorage.normalizeImageAsset(" 护眼漫绿.jpg "))
    }

    @Test
    fun paperBaseColor_usesMeanWhenPresent() {
        val bg = Color(0xFFF5F0E1)
        val mean = Color(0xFFCCBBAA)
        assertEquals(bg, ReadingThemeStorage.paperBaseColor(bg, null))
        assertEquals(mean, ReadingThemeStorage.paperBaseColor(bg, mean))
    }

    @Test
    fun matchesPreset_requiresSolidPresetColors() {
        assertTrue(ReadingTheme.Paper.matchesPreset(ReadingTheme.Paper))
        assertFalse(
            ReadingTheme.Paper.copy(backgroundAlpha = 50).matchesPreset(ReadingTheme.Paper),
        )
        assertFalse(
            ReadingTheme.Paper.copy(backgroundImageAsset = "羊皮纸1.jpg")
                .matchesPreset(ReadingTheme.Paper),
        )
        assertFalse(ReadingTheme.Dark.matchesPreset(ReadingTheme.Paper))
    }

    @Test
    fun defaultBackgroundAssets_listsLegadoDefaults() {
        assertEquals(14, ReaderBackgroundImages.defaultAssets.size)
        assertTrue(ReaderBackgroundImages.defaultAssets.all { it.endsWith(".jpg") })
        assertEquals("羊皮纸1", ReaderBackgroundImages.displayName("羊皮纸1.jpg"))
    }

    @Test
    fun migrateToStyleState_usesDefaultsAndLegacyIndex() {
        val state = ReadingThemeStorage.migrateToStyleState(
            stylesJson = null,
            selectedIndex = null,
            textColorArgb = null,
            bgColorArgb = null,
            bgAlpha = null,
            bgImage = null,
            legacyName = "Green",
        )
        assertEquals(5, state.styles.size)
        assertEquals(2, state.selectedIndex)
        assertEquals(ReadingTheme.Green.presetKey, state.current.presetKey)
    }

    @Test
    fun migrateToStyleState_appliesCustomColorsToSelectedSlot() {
        val text = Color(0xFF112233).toArgb()
        val bg = Color(0xFF445566).toArgb()
        val state = ReadingThemeStorage.migrateToStyleState(
            stylesJson = null,
            selectedIndex = null,
            textColorArgb = text,
            bgColorArgb = bg,
            bgAlpha = 40,
            bgImage = "羊皮纸1.jpg",
            legacyName = "Paper",
        )
        assertEquals(0, state.selectedIndex)
        assertEquals(text, state.current.textColor.toArgb())
        assertEquals(40, state.current.backgroundAlpha)
        assertEquals("羊皮纸1.jpg", state.current.backgroundImageAsset)
        assertEquals("Paper", state.current.presetKey)
    }

    @Test
    fun nameResForPreset_mapsFactoryKeys() {
        assertEquals(R.string.reading_theme_paper, ReadingThemeStorage.nameResForPreset("Paper"))
        assertEquals(R.string.reading_theme_dark, ReadingThemeStorage.nameResForPreset("Dark"))
        assertNull(ReadingThemeStorage.nameResForPreset(null))
        assertNull(ReadingThemeStorage.nameResForPreset(""))
    }

    @Test
    fun encodeDecodeStyles_roundTrip() {
        val original = listOf(
            ReadingTheme.Paper.copy(backgroundAlpha = 80),
            ReadingTheme.newCustom().copy(name = "自定义"),
        )
        val decoded = ReadingThemeStorage.decodeStyles(ReadingThemeStorage.encodeStyles(original))
        assertEquals(2, decoded?.size)
        assertEquals(original[0].textColor.toArgb(), decoded!![0].textColor.toArgb())
        assertEquals(80, decoded[0].backgroundAlpha)
        assertEquals("Paper", decoded[0].presetKey)
        assertEquals("自定义", decoded[1].name)
    }

    @Test
    fun addAndDeleteStyle_updatesSelection() {
        val (added, addIndex) = ReadingThemeStorage.addStyle(ReadingTheme.allThemes())
        assertEquals(6, added.size)
        assertEquals(5, addIndex)
        val (deleted, deleteIndex) = ReadingThemeStorage.deleteStyle(added, addIndex)
        assertEquals(5, deleted.size)
        assertEquals(4, deleteIndex)
        val (unchanged, keep) = ReadingThemeStorage.deleteStyle(listOf(ReadingTheme.Paper), 0)
        assertEquals(1, unchanged.size)
        assertEquals(0, keep)
    }

    @Test
    fun initialStyle_resetsOnlyMatchingPreset() {
        val editedPaper = ReadingTheme.Paper.copy(
            name = "我的纸",
            textColor = Color(0xFF112233),
            backgroundAlpha = 40,
            backgroundImageAsset = "羊皮纸1.jpg",
        )
        val reset = ReadingThemeStorage.initialStyle(editedPaper)
        assertEquals(ReadingTheme.Paper, reset)
        val custom = ReadingTheme.newCustom().copy(
            name = "自定义",
            backgroundAlpha = 20,
        )
        assertEquals(ReadingTheme.newCustom(), ReadingThemeStorage.initialStyle(custom))
    }

    @Test
    fun resetAllPresets_restoresFactoryAndOptionallyKeepsCustom() {
        val custom = ReadingTheme.newCustom().copy(name = "自定义")
        val styles = listOf(
            ReadingTheme.Paper.copy(backgroundAlpha = 40),
            ReadingTheme.White,
            custom,
        )
        val (kept, keptIndex) = ReadingThemeStorage.resetAllPresets(
            styles = styles,
            selectedIndex = 2,
            keepCustom = true,
        )
        assertEquals(6, kept.size)
        assertEquals(ReadingTheme.Paper, kept[0])
        assertEquals(ReadingTheme.allThemes(), kept.take(5))
        assertEquals("自定义", kept[5].name)
        assertEquals(5, keptIndex)

        val (cleared, clearedIndex) = ReadingThemeStorage.resetAllPresets(
            styles = styles,
            selectedIndex = 2,
            keepCustom = false,
        )
        assertEquals(ReadingTheme.allThemes(), cleared)
        assertEquals(0, clearedIndex)
    }

    @Test
    fun parseHexColor_acceptsCommonFormats() {
        assertEquals(Color(0xFFAABBCC).toArgb(), ReadingThemeStorage.parseHexColor("#AABBCC")?.toArgb())
        assertEquals(Color(0xFFAABBCC).toArgb(), ReadingThemeStorage.parseHexColor("aabbcc")?.toArgb())
        assertEquals(Color(0xFFAABBCC).toArgb(), ReadingThemeStorage.parseHexColor("#ABC")?.toArgb())
        assertEquals(Color(0xFF112233).toArgb(), ReadingThemeStorage.parseHexColor("#FF112233")?.toArgb())
        assertNull(ReadingThemeStorage.parseHexColor("xyz"))
        assertNull(ReadingThemeStorage.parseHexColor("#12"))
    }
}
