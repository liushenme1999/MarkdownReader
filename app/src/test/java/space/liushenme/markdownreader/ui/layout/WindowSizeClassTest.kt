package space.liushenme.markdownreader.ui.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSizeClassTest {
    @Test fun breakpoints_are_stable() {
        assertEquals(AppWindowWidthClass.Compact, appWindowWidthClass(599.dp))
        assertEquals(AppWindowWidthClass.Medium, appWindowWidthClass(600.dp))
        assertEquals(AppWindowWidthClass.Medium, appWindowWidthClass(839.dp))
        assertEquals(AppWindowWidthClass.Expanded, appWindowWidthClass(840.dp))
    }

    @Test fun rail_and_wide_reader_only_expand_beyond_compact() {
        assertEquals(false, AppWindowWidthClass.Compact.usesNavigationRail())
        assertEquals(true, AppWindowWidthClass.Medium.usesNavigationRail())
        assertEquals(true, AppWindowWidthClass.Expanded.usesWideReaderNavigation())
    }
}
