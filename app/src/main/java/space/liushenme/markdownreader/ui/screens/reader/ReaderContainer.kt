package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import space.liushenme.markdownreader.ui.components.ShelfStyleStatusBarBackdrop
import space.liushenme.markdownreader.ui.theme.ReadingTheme

/**
 * Stable reader page shell. Document rendering is supplied as a slot so
 * background, insets, scaffold and floating controls cannot alter its state.
 */
@Composable
internal fun ReaderContainer(
    isPdf: Boolean,
    immersive: Boolean,
    textSelectionActive: Boolean,
    hideSystemBars: Boolean,
    theme: ReadingTheme,
    paperColor: Color,
    chromeColor: Color,
    snackbarHostState: SnackbarHostState,
    title: String,
    chapterTitle: String?,
    chromeVisible: Boolean,
    wideNavigation: Boolean = false,
    tocEntries: List<MarkdownTocEntry> = emptyList(),
    currentTocEntry: MarkdownTocEntry? = null,
    tocTitle: String = "目录",
    tocEmptyMessage: String = "暂无目录",
    onTocEntryClick: (MarkdownTocEntry) -> Unit = {},
    onNavigateBack: () -> Unit,
    onToc: () -> Unit,
    onBookmarks: () -> Unit,
    onReadingSettings: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (!isPdf) {
            ReaderPaperBackground(
                theme = theme,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Scaffold(
            containerColor = if (isPdf) paperColor else Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = if (immersive) {
                WindowInsets(0, 0, 0, 0)
            } else {
                ScaffoldDefaults.contentWindowInsets
            },
            topBar = {
                if (!immersive && !textSelectionActive) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBars),
                        )
                        ReaderTopAppBar(
                            title = title,
                            chapterTitle = chapterTitle,
                            onNavigateBack = onNavigateBack,
                            theme = theme,
                        )
                    }
                }
            },
        ) { paddingValues ->
            val readerContentPadding = if (immersive) PaddingValues(0.dp) else paddingValues
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isPdf) Modifier.background(paperColor) else Modifier),
            ) {
                if (immersive && !isPdf && !hideSystemBars) {
                    ShelfStyleStatusBarBackdrop(
                        backgroundColor = chromeColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(100f),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(readerContentPadding)
                        .then(
                            if (immersive && !hideSystemBars) {
                                if (isPdf) {
                                    Modifier.statusBarsPadding().navigationBarsPadding()
                                } else {
                                    Modifier.navigationBarsPadding()
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (wideNavigation) {
                        WideReaderTocRail(
                            entries = tocEntries,
                            currentEntry = currentTocEntry,
                            title = tocTitle,
                            emptyMessage = tocEmptyMessage,
                            onEntryClick = onTocEntryClick,
                            modifier = Modifier.width(280.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        content = content,
                    )
                }
                ReaderChrome(
                    visible = chromeVisible,
                    title = title,
                    chapterTitle = chapterTitle,
                    theme = theme,
                    backgroundColor = chromeColor,
                    onNavigateBack = onNavigateBack,
                    onToc = onToc,
                    onBookmarks = onBookmarks,
                    onReadingSettings = onReadingSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun WideReaderTocRail(
    entries: List<MarkdownTocEntry>,
    currentEntry: MarkdownTocEntry?,
    title: String,
    emptyMessage: String,
    onEntryClick: (MarkdownTocEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        HorizontalDivider()
        if (entries.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(entries, key = { "wide_toc_${it.sourceOffset}_${it.level}" }) { entry ->
                    val selected = currentEntry?.sourceOffset == entry.sourceOffset
                    Text(
                        text = entry.rawTitle.ifBlank { entry.title },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEntryClick(entry) }
                            .padding(
                                start = (16 + entry.level.coerceIn(0, 4) * 14).dp,
                                end = 16.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            ),
                    )
                }
            }
        }
    }
}
