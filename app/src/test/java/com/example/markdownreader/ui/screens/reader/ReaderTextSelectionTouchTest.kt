package com.example.markdownreader.ui.screens.reader

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderTextSelectionTouchTest {

    @Test
    fun expandSelectionTouchRect_includesHorizontalSlopAndHandleBands() {
        val density = 2f
        val bounds = Rect(100, 200, 300, 240)
        val expanded = ReaderTextSelectionTouch.expandSelectionTouchRect(bounds, density)
        assertTrue(expanded.left < bounds.left)
        assertTrue(expanded.right > bounds.right)
        assertTrue(expanded.top < bounds.top - ReaderTextSelectionTouch.handleBandPx(density))
        assertTrue(expanded.bottom > bounds.bottom + ReaderTextSelectionTouch.handleBandPx(density))
    }

    @Test
    fun containsTouch_pointInsideExpandedRect_returnsTrue() {
        val expanded = Rect(0, 0, 200, 200)
        assertTrue(ReaderTextSelectionTouch.containsTouch(expanded, 50f, 50f))
    }

    @Test
    fun containsTouch_pointFarOutside_returnsFalse() {
        val expanded = Rect(0, 0, 100, 100)
        assertFalse(ReaderTextSelectionTouch.containsTouch(expanded, 500f, 500f))
    }

    @Test
    fun expandSelectionTouchRect_handleBandCoversTypicalHandleOffset() {
        val density = 3f
        val lineHeight = 48
        val bounds = Rect(20, 100, 280, 100 + lineHeight)
        val expanded = ReaderTextSelectionTouch.expandSelectionTouchRect(bounds, density)
        val handleBand = ReaderTextSelectionTouch.handleBandPx(density)
        // 模拟 start 句柄在选区顶线上方、end 句柄在底线下方的触点
        assertTrue(ReaderTextSelectionTouch.containsTouch(expanded, 30f, bounds.top - handleBand + 4f))
        assertTrue(ReaderTextSelectionTouch.containsTouch(expanded, 250f, bounds.bottom + handleBand - 4f))
    }
}
