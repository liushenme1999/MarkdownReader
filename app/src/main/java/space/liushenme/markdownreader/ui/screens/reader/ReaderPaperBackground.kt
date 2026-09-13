package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.ui.theme.ReaderBackgroundImages
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import space.liushenme.markdownreader.ui.theme.ReadingThemeStorage
import kotlin.math.roundToInt

@Composable
internal fun rememberPaperBaseColor(theme: ReadingTheme): Color {
    val context = LocalContext.current
    val asset = theme.backgroundImageAsset
    if (asset.isNullOrBlank()) return theme.backgroundColor
    val mean = remember(asset) { ReaderBackgroundImages.meanColor(context, asset) }
    return ReadingThemeStorage.paperBaseColor(theme.backgroundColor, mean)
}

/** legado 式双层纸面：底层平均色/背景色，顶层背景图按 bgAlpha 透明。 */
@Composable
internal fun ReaderPaperBackground(
    theme: ReadingTheme,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val widthPx = (config.screenWidthDp * density.density).roundToInt().coerceAtLeast(1)
    val heightPx = (config.screenHeightDp * density.density).roundToInt().coerceAtLeast(1)
    val asset = theme.backgroundImageAsset
    val paper = remember(asset, widthPx, heightPx) {
        asset?.let { ReaderBackgroundImages.loadDisplay(context, it, widthPx, heightPx) }
    }
    val meanColor = remember(asset) {
        asset?.let { ReaderBackgroundImages.meanColor(context, it) }
    }
    val baseColor = ReadingThemeStorage.paperBaseColor(theme.backgroundColor, meanColor)
    Box(modifier = modifier.background(baseColor)) {
        if (paper != null && !paper.isRecycled) {
            Image(
                bitmap = paper.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = theme.backgroundAlpha / 100f,
            )
        }
    }
}

/**
 * 在顶栏等短条里裁出与全屏纸面同一套 Crop 的顶部，避免换一张「条带中心裁切」。
 */
@Composable
internal fun ReaderPaperBackgroundTopStrip(
    theme: ReadingTheme,
    modifier: Modifier = Modifier,
) {
    val config = LocalConfiguration.current
    Box(modifier = modifier.clipToBounds()) {
        ReaderPaperBackground(
            theme = theme,
            modifier = Modifier
                .fillMaxWidth()
                .height(config.screenHeightDp.dp)
                .align(Alignment.TopCenter),
        )
    }
}
