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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
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
import space.liushenme.markdownreader.ui.components.ReaderPageTurnModeOption
import space.liushenme.markdownreader.ui.components.ReaderSettingsGroupCard
import space.liushenme.markdownreader.ui.components.ReaderSettingsSectionTitle
import space.liushenme.markdownreader.ui.components.ReaderSettingsSwitchRow
import space.liushenme.markdownreader.ui.components.ReadingStyleSettingsBlock
import space.liushenme.markdownreader.ui.components.ReaderWideSliderRow
import space.liushenme.markdownreader.ui.components.MarkdownInlineHtmlText
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.ReadingStyleState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderFontSheet(
    fontSize: Int,
    readerPaddingDp: Int,
    readerLineSpacingMultiplier: Float,
    onFontSizeChange: (Int) -> Unit,
    onPaddingDpChange: (Int) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.reader_sheet_font_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))
            ReaderWideSliderRow(
                label = stringResource(R.string.reader_font_size),
                valueText = "${fontSize} sp",
                value = fontSize.toFloat(),
                onValueChange = { v ->
                    onFontSizeChange(v.roundToInt().coerceIn(10, 40))
                },
                valueRange = 10f..40f,
                steps = 29
            )
            Spacer(modifier = Modifier.height(28.dp))
            ReaderWideSliderRow(
                label = stringResource(R.string.reader_page_margin),
                valueText = "${readerPaddingDp} dp",
                value = readerPaddingDp.toFloat(),
                onValueChange = { v ->
                    onPaddingDpChange(v.roundToInt().coerceIn(8, 56))
                },
                valueRange = 8f..56f,
                steps = 47
            )
            Spacer(modifier = Modifier.height(28.dp))
            ReaderWideSliderRow(
                label = stringResource(R.string.reader_line_spacing),
                valueText = stringResource(
                    R.string.reader_line_spacing_value,
                    readerLineSpacingMultiplier,
                ),
                value = readerLineSpacingMultiplier,
                onValueChange = onLineSpacingChange,
                valueRange = 1f..2.5f,
                steps = 29
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    styleState: ReadingStyleState,
    fontSize: Int,
    readerPaddingDp: Int,
    readerLineSpacingMultiplier: Float,
    codeBlockWrap: Boolean,
    hideSystemBars: Boolean,
    pageTurnMode: ReaderPageTurnMode,
    onSelectStyle: (Int) -> Unit,
    onAddStyle: ((Int) -> Unit) -> Unit,
    onUpdateStyle: (Int, ReadingTheme) -> Unit,
    onDeleteStyle: (Int) -> Unit,
    onResetAllStyles: (keepCustom: Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onPaddingDpChange: (Int) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onCodeBlockWrapChange: (Boolean) -> Unit,
    onHideSystemBarsChange: (Boolean) -> Unit,
    onPageTurnModeChange: (ReaderPageTurnMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.reading_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Column {
                ReaderSettingsSectionTitle(
                    stringResource(R.string.reading_settings_section_reading_theme),
                )
                ReaderSettingsGroupCard {
                    ReadingStyleSettingsBlock(
                        styleState = styleState,
                        onSelect = onSelectStyle,
                        onAdd = onAddStyle,
                        onUpdate = onUpdateStyle,
                        onDelete = onDeleteStyle,
                        onResetAll = onResetAllStyles,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            Column {
                ReaderSettingsSectionTitle(
                    stringResource(R.string.reading_settings_section_typography),
                )
                ReaderSettingsGroupCard {
                    ReaderWideSliderRow(
                        label = stringResource(R.string.reader_font_size),
                        valueText = "$fontSize sp",
                        value = fontSize.toFloat(),
                        onValueChange = {
                            onFontSizeChange(it.roundToInt().coerceIn(10, 40))
                        },
                        valueRange = 10f..40f,
                        steps = 29,
                        showDividerBelow = true,
                    )
                    ReaderWideSliderRow(
                        label = stringResource(R.string.reader_page_margin),
                        valueText = "$readerPaddingDp dp",
                        value = readerPaddingDp.toFloat(),
                        onValueChange = {
                            onPaddingDpChange(it.roundToInt().coerceIn(8, 56))
                        },
                        valueRange = 8f..56f,
                        steps = 47,
                        showDividerBelow = true,
                    )
                    ReaderWideSliderRow(
                        label = stringResource(R.string.reader_line_spacing),
                        valueText = stringResource(
                            R.string.reader_line_spacing_value,
                            readerLineSpacingMultiplier,
                        ),
                        value = readerLineSpacingMultiplier,
                        onValueChange = onLineSpacingChange,
                        valueRange = 1f..2.5f,
                        steps = 29,
                        showDividerBelow = true,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.reader_code_block_wrap),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.reader_code_block_wrap_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                        Switch(
                            checked = codeBlockWrap,
                            onCheckedChange = onCodeBlockWrapChange,
                        )
                    }
                }
            }

            Column {
                ReaderSettingsSectionTitle(
                    stringResource(R.string.reading_settings_section_display),
                )
                ReaderSettingsGroupCard {
                    ReaderSettingsSwitchRow(
                        title = stringResource(R.string.reader_hide_system_bars),
                        summary = stringResource(R.string.reader_hide_system_bars_summary),
                        checked = hideSystemBars,
                        onCheckedChange = onHideSystemBarsChange,
                    )
                }
            }

            Column {
                ReaderSettingsSectionTitle(
                    stringResource(R.string.reading_settings_section_page_turn),
                )
                ReaderSettingsGroupCard {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderPageTurnMode.selectableModes.forEachIndexed { index, mode ->
                            ReaderPageTurnModeOption(
                                mode = mode,
                                selected = mode == pageTurnMode,
                                onClick = { onPageTurnModeChange(mode) },
                                showDividerBelow =
                                    index < ReaderPageTurnMode.selectableModes.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ReaderMarksSheetTab {
    Bookmarks,
    Highlights,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    bookmarks: List<space.liushenme.markdownreader.data.local.entity.BookmarkEntity>,
    highlights: List<HighlightEntity>,
    totalChars: Int,
    onBookmarkClick: (space.liushenme.markdownreader.data.local.entity.BookmarkEntity) -> Unit,
    onDeleteBookmark: (space.liushenme.markdownreader.data.local.entity.BookmarkEntity) -> Unit,
    onHighlightClick: (HighlightEntity) -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ReaderMarksSheetTab.Bookmarks) }
    var revealedBookmarkId by remember { mutableStateOf<Long?>(null) }
    var revealedHighlightId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(bookmarks) {
        revealedBookmarkId = null
    }
    LaunchedEffect(highlights) {
        revealedHighlightId = null
    }
    LaunchedEffect(selectedTab) {
        revealedBookmarkId = null
        revealedHighlightId = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.reader_marks_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == ReaderMarksSheetTab.Bookmarks,
                    onClick = { selectedTab = ReaderMarksSheetTab.Bookmarks },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.reader_marks_tab_bookmarks))
                }
                SegmentedButton(
                    selected = selectedTab == ReaderMarksSheetTab.Highlights,
                    onClick = { selectedTab = ReaderMarksSheetTab.Highlights },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.reader_marks_tab_highlights))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val listScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(listScroll)
            ) {
                when (selectedTab) {
                    ReaderMarksSheetTab.Bookmarks -> {
                        if (bookmarks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.reader_no_bookmarks),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            bookmarks.forEachIndexed { index, bookmark ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    )
                                }
                                BookmarkItem(
                                    bookmark = bookmark,
                                    totalChars = totalChars,
                                    revealedBookmarkId = revealedBookmarkId,
                                    onRevealChange = { id -> revealedBookmarkId = id },
                                    onClick = { onBookmarkClick(bookmark) },
                                    onDelete = {
                                        onDeleteBookmark(bookmark)
                                        if (revealedBookmarkId == bookmark.id) {
                                            revealedBookmarkId = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                    ReaderMarksSheetTab.Highlights -> {
                        if (highlights.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.reader_no_highlights),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            highlights.forEachIndexed { index, highlight ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    )
                                }
                                HighlightListItem(
                                    highlight = highlight,
                                    totalChars = totalChars,
                                    revealedHighlightId = revealedHighlightId,
                                    onRevealChange = { id -> revealedHighlightId = id },
                                    onClick = { onHighlightClick(highlight) },
                                    onDelete = {
                                        onDeleteHighlight(highlight)
                                        if (revealedHighlightId == highlight.id) {
                                            revealedHighlightId = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TocSheet(
    entries: List<MarkdownTocEntry>,
    emptyTocMessage: String,
    currentEntry: MarkdownTocEntry? = null,
    onEntryClick: (MarkdownTocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val currentIndex = remember(entries, currentEntry) {
        resolveTocCurrentIndex(entries, currentEntry)
    }

    LaunchedEffect(entries, currentIndex) {
        val index = currentIndex ?: return@LaunchedEffect
        if (index in entries.indices) {
            // 非末尾：置顶；最后几章内容不够填满时自然停在 maxScroll，智能显示。
            listState.scrollToItem(index)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.reader_toc_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text(
                    emptyTocMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                val highlightFg = MaterialTheme.colorScheme.primary
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { index, entry -> "${entry.sourceOffset}_${entry.level}_$index" },
                    ) { index, entry ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) highlightBg else Color.Transparent)
                                .clickable { onEntryClick(entry) }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                                .padding(
                                    start = ((entry.level - 1).coerceAtLeast(0) * 14).dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MarkdownInlineHtmlText(
                                text = entry.rawTitle,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (isCurrent) {
                                    highlightFg
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/** 在目录列表中定位当前阅读章节下标；优先 [MarkdownTocEntry.sourceOffset]，其次 rawTitle。 */
internal fun resolveTocCurrentIndex(
    entries: List<MarkdownTocEntry>,
    currentEntry: MarkdownTocEntry?,
): Int? {
    if (currentEntry == null || entries.isEmpty()) return null
    entries.indexOfFirst { it.sourceOffset == currentEntry.sourceOffset }
        .takeIf { it >= 0 }
        ?.let { return it }
    return entries.indexOfFirst { it.rawTitle == currentEntry.rawTitle }.takeIf { it >= 0 }
}

@Composable
internal fun BookmarkItem(
    bookmark: space.liushenme.markdownreader.data.local.entity.BookmarkEntity,
    totalChars: Int,
    revealedBookmarkId: Long?,
    onRevealChange: (Long?) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 72.dp.toPx() }
    var offsetPx by remember(bookmark.id) { mutableFloatStateOf(0f) }
    val revealedIdSnapshot by rememberUpdatedState(revealedBookmarkId)

    LaunchedEffect(revealedBookmarkId, bookmark.id) {
        if (revealedBookmarkId != bookmark.id) {
            offsetPx = 0f
        }
    }

    val progressPercent = remember(bookmark.position, totalChars) {
        if (totalChars <= 0) null
        else ((bookmark.position * 100f) / totalChars).roundToInt().coerceIn(0, 100)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                val err = MaterialTheme.colorScheme.error
                val deleteIconTint = iconTintForDeleteStrip(err)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            onDelete()
                            offsetPx = 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        modifier = Modifier.size(28.dp),
                        tint = deleteIconTint
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 背景必须在「与位移同一层」或位移在内层，否则会整块铺宽不随 offset 移动，盖住下层红色删除条（点击能删但看不见）
                .graphicsLayer { translationX = offsetPx }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(bookmark.id, deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-deleteWidthPx, 0f)
                        },
                        onDragEnd = {
                            val threshold = -deleteWidthPx * 0.35f
                            if (offsetPx < threshold) {
                                offsetPx = -deleteWidthPx
                                onRevealChange(bookmark.id)
                            } else {
                                offsetPx = 0f
                                if (revealedIdSnapshot == bookmark.id) {
                                    onRevealChange(null)
                                }
                            }
                        }
                    )
                }
                .clickable {
                    if (offsetPx < -4f) {
                        offsetPx = 0f
                        onRevealChange(null)
                    } else {
                        onClick()
                    }
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.widthIn(min = 44.dp, max = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressPercent?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarkdownInlineHtmlText(
                    text = bookmark.previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!bookmark.note.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bookmark.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun HighlightListItem(
    highlight: HighlightEntity,
    totalChars: Int,
    revealedHighlightId: Long?,
    onRevealChange: (Long?) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 72.dp.toPx() }
    var offsetPx by remember(highlight.id) { mutableFloatStateOf(0f) }
    val revealedIdSnapshot by rememberUpdatedState(revealedHighlightId)
    val accent = Color(highlight.color).let { c ->
        if (c.alpha < 0.06f) Color(0xFFFFFF00) else c
    }

    LaunchedEffect(revealedHighlightId, highlight.id) {
        if (revealedHighlightId != highlight.id) {
            offsetPx = 0f
        }
    }

    val progressPercent = remember(highlight.startPosition, totalChars) {
        if (totalChars <= 0) null
        else ((highlight.startPosition * 100f) / totalChars).roundToInt().coerceIn(0, 100)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                val err = MaterialTheme.colorScheme.error
                val deleteIconTint = iconTintForDeleteStrip(err)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            onDelete()
                            offsetPx = 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        modifier = Modifier.size(28.dp),
                        tint = deleteIconTint
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetPx }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(highlight.id, deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-deleteWidthPx, 0f)
                        },
                        onDragEnd = {
                            val threshold = -deleteWidthPx * 0.35f
                            if (offsetPx < threshold) {
                                offsetPx = -deleteWidthPx
                                onRevealChange(highlight.id)
                            } else {
                                offsetPx = 0f
                                if (revealedIdSnapshot == highlight.id) {
                                    onRevealChange(null)
                                }
                            }
                        }
                    )
                }
                .clickable {
                    if (offsetPx < -4f) {
                        offsetPx = 0f
                        onRevealChange(null)
                    } else {
                        onClick()
                    }
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.widthIn(min = 44.dp, max = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressPercent?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlight.highlightedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!highlight.note.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = highlight.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

