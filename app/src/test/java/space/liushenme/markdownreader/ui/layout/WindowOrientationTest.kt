package space.liushenme.markdownreader.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowOrientationTest {
    @Test
    fun orientationUsesOnlyWidthAndHeight() {
        assertEquals(AppWindowOrientation.Portrait, appWindowOrientation(1080, 1920))
        assertEquals(AppWindowOrientation.Landscape, appWindowOrientation(1920, 1080))
        assertEquals(AppWindowOrientation.Portrait, appWindowOrientation(1200, 1200))
    }

    @Test
    fun landscapeFlagDoesNotDependOnScreenSize() {
        assertTrue(isLandscapeWindow(800, 480))
        assertTrue(isLandscapeWindow(2400, 1600))
        assertFalse(isLandscapeWindow(480, 800))
    }
}
