package space.liushenme.markdownreader.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.TextViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 略深于阅读区底色，用于顶栏/状态栏/底栏/系统导航条，便于与正文区分 */
internal fun readingChromeShade(readingBackground: Color): Color =
    lerp(readingBackground, Color.Black, 0.04f)

/** 进页空白/遮罩/渲染分阶段耗时，过滤 logcat: `adb logcat -s ReaderOpenDbg` */
internal const val READER_OPEN_DBG = "ReaderOpenDbg"

internal fun readerOpenDbg(msg: String) {
    if (!space.liushenme.markdownreader.BuildConfig.DEBUG) return
    android.util.Log.d(READER_OPEN_DBG, "${android.os.SystemClock.uptimeMillis()} $msg")
}

internal fun readerRestoreDbg(msg: String) {
    if (!space.liushenme.markdownreader.BuildConfig.DEBUG) return
    android.util.Log.d("ReaderRestoreDbg2", msg)
}

/** [TextView] 上用于判断是否需要重新执行 Markwon 渲染的 tag key（不含划线） */
internal val TAG_READER_RENDER_SIG = R.id.reader_render_sig

/** [TextView] 上用于判断是否仅需重绘划线 span 的 tag key */
internal val TAG_READER_HIGHLIGHT_SIG = R.id.reader_highlight_sig

/** 记录上次应用的行距倍数，行距变化时触发 TextView 重排以更新表格 span 补偿 */
internal val TAG_READER_LINE_SPACING = R.id.reader_line_spacing

/** 滚动恢复代数：防止已取消的 LaunchedEffect 在 tv.post 中仍执行旧的 snap 滚动 */
internal val TAG_READER_SCROLL_RESTORE_GEN = R.id.reader_scroll_restore_gen

/** 顶栏/底栏显示时，累计垂直滚动超过该像素后再收起（避免轻微抖动误触） */
internal val ReaderHideChromeScrollThreshold = 56.dp

internal val ReaderImmersiveBottomBarHeight = 56.dp

/** 沉浸章节条显示时，正文 TextView 顶部额外留白（dp），略小于常规页边距 */
internal const val ReaderChapterStripBodyTopPaddingDp = 4
