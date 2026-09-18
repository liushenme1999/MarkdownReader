package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import space.liushenme.markdownreader.importing.PdfPageRenderSize
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal data class PdfJumpRequest(
    val generation: Int,
    val pageIndex: Int,
    val fractionInPage: Float = 0f,
)

internal fun pdfPageInvertComposeColorFilter(): ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix(PDF_PAGE_INVERT_MATRIX_VALUES.copyOf()))

/** 手势闭包只持有这个对象，避免 pointerInput 捕获到过期的 scale。 */
private class PdfReaderZoomState {
    var scale by mutableFloatStateOf(PDF_ZOOM_MIN)
    var panX by mutableFloatStateOf(0f)
    var settleScale by mutableFloatStateOf(PDF_ZOOM_MIN)
    var viewportW by mutableFloatStateOf(0f)
    var viewportH by mutableFloatStateOf(0f)
    var pinching by mutableStateOf(false)
    var lastTapUptimeMs by mutableLongStateOf(0L)
    var lastTapPosition by mutableStateOf(Offset.Zero)

    fun commitScale(
        nextScale: Float,
        focalX: Float,
        focalY: Float,
        listState: LazyListState,
        intrins: List<Pair<Int, Int>>,
    ) {
        val old = scale
        val clamped = clampPdfZoom(nextScale)
        val nextPanX = pdfDocumentPanXAfterZoom(panX, focalX, old, clamped)
        if (viewportW <= 0f || intrins.isEmpty()) {
            scale = clamped
            panX = clampPdfDocumentPanX(nextPanX, viewportW, clamped)
            return
        }
        val oldHeights = pdfPageHeights(viewportW, old, intrins)
        val oldGap = pdfScaledPageGap(old)
        val oldY = pdfDocumentScrollY(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            oldHeights,
            oldGap,
        )
        val newY = pdfDocumentScrollAfterZoom(oldY, focalY, old, clamped)
        scale = clamped
        panX = clampPdfDocumentPanX(nextPanX, viewportW, clamped)
        val newHeights = pdfPageHeights(viewportW, clamped, intrins)
        val newGap = pdfScaledPageGap(clamped)
        val (index, offset) = pdfScrollToOffset(newY, newHeights, newGap)
        listState.requestScrollToItem(index, offset)
    }
}

@Composable
internal fun PdfContinuousReader(
    pages: List<PdfPageRef>,
    theme: ReadingTheme,
    paperColor: Color,
    restoreAnchor: PdfViewportAnchor,
    jumpRequest: PdfJumpRequest?,
    modifier: Modifier = Modifier,
    onViewportChanged: (PdfViewportAnchor, atEnd: Boolean) -> Unit = { _, _ -> },
    onCenterTap: () -> Unit = {},
    onSwipeBookmark: () -> Unit = {},
    onVerticalScroll: (deltaPx: Int) -> Unit = {},
    onZoomedChange: (Boolean) -> Unit = {},
    onOpened: () -> Unit = {},
) {
    if (pages.isEmpty()) {
        Box(modifier.fillMaxSize().background(paperColor))
        LaunchedEffect(Unit) { onOpened() }
        return
    }

    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val listState = rememberLazyListState()
    val cache = remember { PdfPageBitmapCache() }
    val zoom = remember { PdfReaderZoomState() }
    DisposableEffect(Unit) {
        onDispose { cache.clear() }
    }

    var intrins by remember(pages) {
        mutableStateOf(
            pages.map { page ->
                if (page.intrinsicWidth > 0 && page.intrinsicHeight > 0) {
                    page.intrinsicWidth to page.intrinsicHeight
                } else {
                    1000 to 1414
                }
            },
        )
    }
    val needsBounds = remember(pages) {
        pages.any { it.intrinsicWidth <= 0 || it.intrinsicHeight <= 0 }
    }
    var boundsReady by remember(pages) { mutableStateOf(!needsBounds) }
    val invert = shouldInvertPdfPages(theme.backgroundColor.luminance())
    val invertFilter = remember { pdfPageInvertComposeColorFilter() }

    var opened by remember { mutableStateOf(false) }
    var liveAnchor by remember { mutableStateOf(restoreAnchor) }

    val latestOnOpened = rememberUpdatedState(onOpened)
    val latestOnViewport = rememberUpdatedState(onViewportChanged)
    val latestOnScroll = rememberUpdatedState(onVerticalScroll)
    val latestOnZoomed = rememberUpdatedState(onZoomedChange)
    val latestCenterTap = rememberUpdatedState(onCenterTap)
    val latestSwipeBookmark = rememberUpdatedState(onSwipeBookmark)
    val latestIntrins = rememberUpdatedState(intrins)

    val scale = zoom.scale
    val panX = zoom.panX
    val settleScale = zoom.settleScale
    val pinching = zoom.pinching
    val viewportW = zoom.viewportW
    val viewportH = zoom.viewportH

    val gap = pdfScaledPageGap(scale)
    val heights = remember(viewportW, scale, intrins) {
        pdfPageHeights(viewportW, scale, intrins)
    }

    fun currentScrollY(): Float = pdfDocumentScrollY(
        firstVisibleIndex = listState.firstVisibleItemIndex,
        firstVisibleOffset = listState.firstVisibleItemScrollOffset,
        pageHeights = heights,
        gap = gap,
    )

    fun scrollToDocumentY(y: Float, heightsNow: FloatArray, gapNow: Float) {
        val (index, offset) = pdfScrollToOffset(y, heightsNow, gapNow)
        listState.requestScrollToItem(index, offset)
    }

    LaunchedEffect(scale, pinching) {
        latestOnZoomed.value(pdfZoomIsActive(scale))
        if (!pdfZoomIsActive(scale) && !pinching) {
            zoom.panX = 0f
        }
    }

    LaunchedEffect(pages, restoreAnchor) {
        liveAnchor = restoreAnchor
        opened = false
    }

    LaunchedEffect(pages) {
        if (!needsBounds) {
            boundsReady = true
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            pages.map { page ->
                if (page.intrinsicWidth > 0 && page.intrinsicHeight > 0) {
                    page.intrinsicWidth to page.intrinsicHeight
                } else {
                    PdfPageBitmapCache.decodeBounds(page.path) ?: (1000 to 1414)
                }
            }
        }
        intrins = decoded
        boundsReady = true
    }

    LaunchedEffect(viewportW, pages, restoreAnchor, boundsReady) {
        if (!boundsReady || viewportW <= 0f || pages.isEmpty()) return@LaunchedEffect
        val target = if (opened) liveAnchor else restoreAnchor
        val restoreHeights = pdfPageHeights(viewportW, zoom.scale, intrins)
        val index = target.pageIndex.coerceIn(0, pages.lastIndex)
        val pageH = restoreHeights.getOrElse(index) { 0f }
        val offset = (pageH * target.fractionInPage.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(0)
        listState.requestScrollToItem(index, offset)
        if (!opened) {
            opened = true
            latestOnOpened.value()
        }
    }

    LaunchedEffect(jumpRequest, viewportW, boundsReady) {
        val jump = jumpRequest ?: return@LaunchedEffect
        if (!boundsReady || viewportW <= 0f || pages.isEmpty()) return@LaunchedEffect
        val jumpHeights = pdfPageHeights(viewportW, zoom.scale, intrins)
        val index = jump.pageIndex.coerceIn(0, pages.lastIndex)
        val pageH = jumpHeights.getOrElse(index) { 0f }
        val offset = (pageH * jump.fractionInPage.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(0)
        liveAnchor = PdfViewportAnchor(index, jump.fractionInPage)
        listState.requestScrollToItem(index, offset)
        latestOnOpened.value()
    }

    LaunchedEffect(listState, viewportH, scale, pages) {
        var lastY = currentScrollY()
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect {
            val y = currentScrollY()
            val dy = abs(y - lastY).roundToInt()
            if (dy > 0) latestOnScroll.value(dy)
            lastY = y
            val anchor = pdfAnchorAtScrollY(y, heights, gap)
            liveAnchor = anchor
            val atEnd = pdfDocumentReachedEnd(y, viewportH, heights, gap)
            latestOnViewport.value(anchor, atEnd)
        }
    }

    val decodeWidth = pdfDecodeWidthForScale(viewportW, settleScale, PdfPageRenderSize.MAX_WIDTH)
    LaunchedEffect(listState, settleScale, viewportH, pages) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect {
            if (viewportH <= 0f || heights.isEmpty()) return@collect
            val y = currentScrollY()
            val visible = pdfVisiblePageIndices(y, viewportH, heights, gap)
            cache.retainPaths(visible.map { pages[it].path }.toSet())
        }
    }

    val contentWidthDp = with(density) { pdfPageDisplayWidth(viewportW, scale).toDp() }
    val slop = viewConfig.touchSlop
    val doubleTapTimeout = viewConfig.doubleTapTimeoutMillis

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(paperColor)
            .onSizeChanged {
                zoom.viewportW = it.width.toFloat()
                zoom.viewportH = it.height.toFloat()
            }
            .pointerInput(pages.size) {
                awaitEachGesture {
                    var maxPointers = 0
                    var movedPastSlop = false
                    var downPos = Offset.Unspecified
                    var sawDoubleTap = false
                    var sawPinch = false
                    var pinchArmed = false
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
                                val dt = first.uptimeMillis - zoom.lastTapUptimeMs
                                val close = (first.position - zoom.lastTapPosition).getDistance() <= slop * 2f
                                if (dt in 1..doubleTapTimeout && close) {
                                    sawDoubleTap = true
                                    val next = pdfDoubleTapTargetScale(zoom.scale)
                                    zoom.commitScale(
                                        next,
                                        first.position.x,
                                        first.position.y,
                                        listState,
                                        latestIntrins.value,
                                    )
                                    zoom.settleScale = snapPdfZoom(next)
                                    zoom.lastTapUptimeMs = 0L
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
                                sawPinch = true
                                zoom.pinching = true
                                if (!pinchArmed) {
                                    pinchArmed = true
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                val centroidX = pressed.map { it.position.x }.average().toFloat()
                                val centroidY = pressed.map { it.position.y }.average().toFloat()
                                zoom.commitScale(
                                    zoom.scale * event.calculateZoom(),
                                    centroidX,
                                    centroidY,
                                    listState,
                                    latestIntrins.value,
                                )
                                val pan = event.calculatePan()
                                zoom.panX = clampPdfDocumentPanX(
                                    zoom.panX + pan.x,
                                    zoom.viewportW,
                                    zoom.scale,
                                )
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                                continue
                            }
                            if (pressed.size != 1) continue
                            val travel = (first.position - downPos).getDistance()
                            if (!movedPastSlop && travel < slop) continue
                            movedPastSlop = true
                            val pan = event.calculatePan()
                            if (pdfZoomIsActive(zoom.scale) && abs(pan.x) >= abs(pan.y)) {
                                zoom.panX = clampPdfDocumentPanX(
                                    zoom.panX + pan.x,
                                    zoom.viewportW,
                                    zoom.scale,
                                )
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    } finally {
                        if (sawPinch) {
                            val snapped = snapPdfZoom(zoom.scale)
                            if (snapped != zoom.scale) {
                                zoom.commitScale(
                                    snapped,
                                    zoom.viewportW / 2f,
                                    zoom.viewportH / 2f,
                                    listState,
                                    latestIntrins.value,
                                )
                            }
                            zoom.settleScale = snapped
                            zoom.pinching = false
                        } else if (!sawDoubleTap) {
                            val snapped = snapPdfZoom(zoom.scale)
                            if (snapped != zoom.scale) {
                                zoom.commitScale(
                                    snapped,
                                    zoom.viewportW / 2f,
                                    zoom.viewportH / 2f,
                                    listState,
                                    latestIntrins.value,
                                )
                            }
                            zoom.settleScale = snapped
                        }
                        if (!sawDoubleTap && !sawPinch && maxPointers <= 1 && !movedPastSlop &&
                            downPos != Offset.Unspecified
                        ) {
                            zoom.lastTapUptimeMs = SystemClock.uptimeMillis()
                            zoom.lastTapPosition = downPos
                            val w = zoom.viewportW
                            val h = zoom.viewportH
                            if (w > 0f && h > 0f &&
                                downPos.x in (w * 0.32f)..(w * 0.68f) &&
                                downPos.y in (h * 0.36f)..(h * 0.64f)
                            ) {
                                latestCenterTap.value()
                            }
                        }
                    }
                }
            }
            .pointerInput(pages.size) {
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Main)
                    val start = down.changes.firstOrNull() ?: return@awaitEachGesture
                    val downPos = start.position
                    var last = downPos
                    var maxPointers = 1
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        maxPointers = max(maxPointers, event.changes.count { it.pressed })
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            val end = event.changes.firstOrNull()?.position ?: last
                            val dx = end.x - downPos.x
                            val dy = end.y - downPos.y
                            if (maxPointers <= 1 &&
                                !pdfZoomIsActive(zoom.scale) &&
                                dx > 120f &&
                                dx > abs(dy) * 2f
                            ) {
                                latestSwipeBookmark.value()
                            }
                            break
                        }
                        last = pressed.first().position
                    }
                }
            },
    ) {
        if (viewportW <= 0f) return@Box
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !pinching,
            horizontalAlignment = Alignment.Start,
        ) {
            itemsIndexed(pages, key = { _, page -> page.assetName }) { index, page ->
                val (iw, ih) = intrins[index]
                val pageH = heights.getOrElse(index) {
                    pdfPageHeightPx(viewportW, scale, iw, ih)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { pageH.toDp() })
                        .clipToBounds(),
                ) {
                    PdfPageImage(
                        page = page,
                        targetWidth = decodeWidth,
                        invert = invert,
                        invertFilter = invertFilter,
                        cache = cache,
                        paperColor = paperColor,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .wrapContentWidth(unbounded = true, align = Alignment.Start)
                            .graphicsLayer { translationX = panX }
                            .requiredWidth(contentWidthDp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PdfPageImage(
    page: PdfPageRef,
    targetWidth: Int,
    invert: Boolean,
    invertFilter: ColorFilter,
    cache: PdfPageBitmapCache,
    paperColor: Color,
    modifier: Modifier = Modifier,
) {
    var shown by remember(page.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page.path, targetWidth) {
        val decoded = withContext(Dispatchers.IO) {
            cache.decode(page.path, targetWidth.coerceAtLeast(1))
        }
        if (decoded != null && !decoded.isRecycled) {
            shown = decoded
        }
    }
    val bmp = shown
    if (bmp == null || bmp.isRecycled) {
        Box(modifier.background(paperColor))
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.background(paperColor),
            contentScale = ContentScale.FillBounds,
            alignment = Alignment.TopStart,
            colorFilter = if (invert) invertFilter else null,
        )
    }
}
