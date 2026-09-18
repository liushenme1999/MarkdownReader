package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.max

internal const val PDF_ZOOM_MIN = 1f
internal const val PDF_ZOOM_MAX = 8f
internal const val PDF_DOUBLE_TAP_ZOOM = 2.5f
internal const val PDF_SNAP_TO_MIN_SCALE = 1.08f
private const val PDF_ZOOMED_EPS = 1.01f

internal fun clampPdfZoom(scale: Float): Float = scale.coerceIn(PDF_ZOOM_MIN, PDF_ZOOM_MAX)

internal fun snapPdfZoom(scale: Float): Float {
    val clamped = clampPdfZoom(scale)
    return if (clamped < PDF_SNAP_TO_MIN_SCALE) PDF_ZOOM_MIN else clamped
}

internal fun pdfDoubleTapTargetScale(currentScale: Float): Float =
    if (pdfZoomIsActive(currentScale)) PDF_ZOOM_MIN else PDF_DOUBLE_TAP_ZOOM

internal fun clampPdfPan(offset: Offset, scale: Float, viewport: Size): Offset {
    if (scale <= PDF_ZOOMED_EPS || viewport.width <= 0f || viewport.height <= 0f) {
        return Offset.Zero
    }
    val maxX = viewport.width * (scale - 1f) / 2f
    val maxY = viewport.height * (scale - 1f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

internal fun pdfZoomIsActive(scale: Float): Boolean = scale > PDF_ZOOMED_EPS

internal fun pdfZoomOffsetForFocalPoint(
    tap: Offset,
    scale: Float,
    viewport: Size,
): Offset {
    if (!pdfZoomIsActive(scale) || viewport.width <= 0f || viewport.height <= 0f) {
        return Offset.Zero
    }
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    return clampPdfPan((tap - center) * (1f - scale), scale, viewport)
}

/**
 * 放大后平移超出左右夹紧范围时，返回翻页方向：-1 上一页，+1 下一页，0 不翻。
 */
internal fun pdfHorizontalOverscrollTurn(
    currentOffset: Offset,
    unclampedOffset: Offset,
    scale: Float,
    viewport: Size,
    overflowAccumX: Float,
    edgeSlop: Float,
): Int {
    if (!pdfZoomIsActive(scale) || viewport.width <= 0f || edgeSlop <= 0f) return 0
    val maxX = viewport.width * (scale - 1f) / 2f
    if (maxX <= 0f) return 0
    val atLeft = currentOffset.x >= maxX - 1f
    val atRight = currentOffset.x <= -maxX + 1f
    val beyond = unclampedOffset.x - currentOffset.x.coerceIn(-maxX, maxX)
    val nextAccum = overflowAccumX + beyond
    return when {
        atLeft && nextAccum > edgeSlop -> -1
        atRight && nextAccum < -edgeSlop -> 1
        else -> 0
    }
}

private class PdfZoomGestureState {
    var scale by mutableFloatStateOf(PDF_ZOOM_MIN)
    var offset by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(Size.Zero)
    var lastTapUptimeMs by mutableLongStateOf(0L)
    var lastTapPosition by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = PDF_ZOOM_MIN
        offset = Offset.Zero
    }
}

/**
 * PDF 阅读页双指缩放 + 双击放大 + 放大后单指拖动。
 *
 * 单指且未放大（或未过 slop）时不消费事件，交给 TextView / HorizontalPager（翻页、点顶栏）。
 * 双指在 [PointerEventPass.Initial] 消费，避免 AndroidView 把捏合吃掉。
 */
@Composable
internal fun PdfReaderZoomBox(
    enabled: Boolean,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    onZoomedChange: (Boolean) -> Unit = {},
    onTurnPage: ((direction: Int) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier.fillMaxSize()) { content() }
        return
    }

    val state = remember { PdfZoomGestureState() }
    val latestTurnPage = rememberUpdatedState(onTurnPage)

    LaunchedEffect(resetKey) {
        state.reset()
    }
    LaunchedEffect(state.scale) {
        onZoomedChange(pdfZoomIsActive(state.scale))
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.viewport = it.toSize() }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                awaitEachGesture {
                    var maxPointers = 0
                    var movedPastSlop = false
                    var downPos = Offset.Unspecified
                    var overflowX = 0f
                    var turned = 0
                    var sawDoubleTap = false
                    var pressedRemaining = true
                    try {
                        while (pressedRemaining) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            pressedRemaining = pressed.isNotEmpty()
                            if (pressed.isEmpty()) break
                            maxPointers = max(maxPointers, pressed.size)
                            val first = pressed.first()
                            if (downPos == Offset.Unspecified) {
                                downPos = first.position
                                val dt = first.uptimeMillis - state.lastTapUptimeMs
                                val close = (first.position - state.lastTapPosition).getDistance() <= slop * 2f
                                if (dt in 1..doubleTapTimeout && close) {
                                    sawDoubleTap = true
                                    val nextScale = pdfDoubleTapTargetScale(state.scale)
                                    state.scale = nextScale
                                    state.offset = pdfZoomOffsetForFocalPoint(
                                        first.position,
                                        nextScale,
                                        state.viewport,
                                    )
                                    state.lastTapUptimeMs = 0L
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                            }
                            if (sawDoubleTap) {
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (pressed.size >= 2) {
                                movedPastSlop = true
                                val nextScale = clampPdfZoom(state.scale * event.calculateZoom())
                                val nextOffset = state.offset + event.calculatePan()
                                state.scale = nextScale
                                state.offset = clampPdfPan(nextOffset, nextScale, state.viewport)
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                                overflowX = 0f
                                continue
                            }
                            val zoomed = pdfZoomIsActive(state.scale)
                            if (!zoomed || pressed.size != 1) continue
                            val pan = event.calculatePan()
                            if (!movedPastSlop) {
                                val travel = (first.position - downPos).getDistance()
                                if (travel < slop) continue
                                movedPastSlop = true
                            }
                            val unclamped = state.offset + pan
                            val turn = pdfHorizontalOverscrollTurn(
                                currentOffset = state.offset,
                                unclampedOffset = unclamped,
                                scale = state.scale,
                                viewport = state.viewport,
                                overflowAccumX = overflowX,
                                edgeSlop = slop * 1.5f,
                            )
                            val maxX = state.viewport.width * (state.scale - 1f) / 2f
                            val clampedX = state.offset.x.coerceIn(-maxX, maxX)
                            val beyond = unclamped.x - clampedX
                            overflowX = if (abs(beyond) < 0.5f) 0f else overflowX + beyond
                            if (turn != 0 && latestTurnPage.value != null) {
                                turned = turn
                                event.changes.forEach { it.consume() }
                                break
                            }
                            state.offset = clampPdfPan(unclamped, state.scale, state.viewport)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } finally {
                        if (turned != 0) {
                            state.reset()
                            latestTurnPage.value?.invoke(turned)
                        } else {
                            state.scale = snapPdfZoom(state.scale)
                            if (!pdfZoomIsActive(state.scale)) {
                                state.offset = Offset.Zero
                            }
                            if (!sawDoubleTap && maxPointers <= 1 && !movedPastSlop &&
                                downPos != Offset.Unspecified
                            ) {
                                state.lastTapUptimeMs = android.os.SystemClock.uptimeMillis()
                                state.lastTapPosition = downPos
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.offset.x
                translationY = state.offset.y
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        content()
    }
}
