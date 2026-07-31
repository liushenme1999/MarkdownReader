package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import space.liushenme.markdownreader.model.HighlightStyle
import kotlin.math.roundToInt

internal val HighlightPickerColors = listOf(
    Color(0xFFFFFF00),
    Color(0xFF7CFF7C),
    Color(0xFF6EE7FF),
    Color(0xFFFF8AD8),
    Color(0xFFFFB347),
)

@Composable
internal fun HighlightStylePopup(
    selectionBoundsInWindow: Rect,
    selectedColor: Color,
    selectedStyle: HighlightStyle,
    onPick: (color: Color, style: HighlightStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(selectionBoundsInWindow, gapPx) {
        HighlightPopupPositionProvider(selectionBoundsInWindow, gapPx)
    }
    val view = LocalView.current
    DisposableEffect(view) {
        enablePopupBackgroundBlur(view, radiusPx = 48)
        onDispose { }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassColor = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.58f)
    }
    val glassBorder = if (isDark) {
        Color.White.copy(alpha = 0.28f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    val shape = RoundedCornerShape(18.dp)

    // 必须 focusable=false：可聚焦 Popup 会抢焦点，MIUI FloatingActionMode（复制/划线）会立刻被系统关掉。
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Row(
            modifier = Modifier
                .shadow(elevation = 10.dp, shape = shape, clip = false)
                .clip(shape)
                .background(glassColor)
                .border(width = 0.8.dp, color = glassBorder, shape = shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightStyle.entries.forEach { style ->
                    StyleChip(
                        style = style,
                        color = selectedColor,
                        selected = style == selectedStyle,
                        onClick = { onPick(selectedColor, style) },
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier.height(26.dp),
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightPickerColors.forEach { color ->
                    val selected = colorsNearlyEqual(color, selectedColor)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                                },
                                shape = CircleShape,
                            )
                            .clickable { onPick(color, selectedStyle) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleChip(
    style: HighlightStyle,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    }
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(28.dp)
            .border(width = 1.2.dp, color = border, shape = RoundedCornerShape(8.dp))
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            HighlightStyle.Background -> {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(12.dp)
                        .background(color.copy(alpha = 0.85f), RoundedCornerShape(3.dp)),
                )
            }
            HighlightStyle.Underline -> {
                Canvas(modifier = Modifier.width(22.dp).height(12.dp)) {
                    val y = size.height - 2.dp.toPx()
                    drawLine(
                        color = color,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            HighlightStyle.Wavy -> {
                Canvas(modifier = Modifier.width(22.dp).height(12.dp)) {
                    val y = size.height - 2.5.dp.toPx()
                    val path = Path()
                    var x = 0f
                    var up = true
                    path.moveTo(x, y)
                    while (x < size.width) {
                        val next = (x + 5f).coerceAtMost(size.width)
                        val mid = (x + next) / 2f
                        path.quadraticTo(mid, if (up) y - 3f else y + 3f, next, y)
                        up = !up
                        x = next
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

private fun colorsNearlyEqual(a: Color, b: Color): Boolean =
    a.red == b.red && a.green == b.green && a.blue == b.blue && a.alpha == b.alpha

/** API 31+ 为 Popup 窗口开启背后模糊，配合半透明底色形成毛玻璃。 */
private fun enablePopupBackgroundBlur(view: View, radiusPx: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    var current: View? = view
    while (current != null) {
        val params = current.layoutParams
        if (params is WindowManager.LayoutParams) {
            try {
                current.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.setBlurBehindRadius(radiusPx)
                val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.updateViewLayout(current, params)
            } catch (_: Throwable) {
                // 部分 ROM / 无模糊能力时忽略
            }
            return
        }
        current = current.parent as? View
    }
}

private class HighlightPopupPositionProvider(
    private val selectionBounds: Rect,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val popupW = popupContentSize.width
        val popupH = popupContentSize.height
        val selTop = selectionBounds.top
        val selBottom = selectionBounds.bottom
        val selCenterX = (selectionBounds.left + selectionBounds.right) / 2
        val selHeight = (selBottom - selTop).coerceAtLeast(0)
        val fillsScreen = selHeight >= (windowSize.height * 0.6f).roundToInt()

        val y = when {
            fillsScreen ->
                (windowSize.height - popupH - gapPx * 3).coerceAtLeast(gapPx)
            selBottom + gapPx + popupH <= windowSize.height ->
                selBottom + gapPx
            selTop - gapPx - popupH >= 0 ->
                selTop - gapPx - popupH
            else ->
                (windowSize.height - popupH - gapPx * 3).coerceAtLeast(gapPx)
        }

        val x = (selCenterX - popupW / 2).coerceIn(
            gapPx,
            (windowSize.width - popupW - gapPx).coerceAtLeast(gapPx),
        )
        return IntOffset(x, y)
    }
}
