package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.distinctUntilChanged
import space.liushenme.markdownreader.importing.PdfPageRenderSize
import space.liushenme.markdownreader.ui.theme.ReadingTheme

/**
 * 横向翻页：每页一张图，缩放置于外层 [PdfReaderZoomBox]（一页适配进视口）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PdfPagedImageHost(
    pages: List<PdfPageRef>,
    pagerState: PagerState,
    theme: ReadingTheme,
    paperColor: Color,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    onPageChanged: (pageIndex: Int, atEnd: Boolean) -> Unit = { _, _ -> },
) {
    val cache = remember { PdfPageBitmapCache(maxEntries = 5) }
    DisposableEffect(Unit) {
        onDispose { cache.clear() }
    }
    val invert = shouldInvertPdfPages(theme.backgroundColor.luminance())
    val invertFilter = remember { pdfPageInvertComposeColorFilter() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
            onPageChanged(page, page >= pages.lastIndex)
            val keep = buildSet {
                for (i in (page - 1)..(page + 1)) {
                    val p = pages.getOrNull(i) ?: continue
                    add(PdfBitmapKey(p.path, decodeWidth(viewport.width)))
                }
            }
            cache.retain(keep)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(paperColor)
            .onSizeChanged { viewport = it },
        userScrollEnabled = userScrollEnabled,
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(paperColor),
            contentAlignment = Alignment.Center,
        ) {
            if (page != null) {
                PdfPageImage(
                    page = page,
                    targetWidth = decodeWidth(viewport.width),
                    invert = invert,
                    invertFilter = invertFilter,
                    cache = cache,
                    paperColor = paperColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun decodeWidth(viewportWidth: Int): Int =
    viewportWidth.coerceIn(1, PdfPageRenderSize.MAX_WIDTH)
