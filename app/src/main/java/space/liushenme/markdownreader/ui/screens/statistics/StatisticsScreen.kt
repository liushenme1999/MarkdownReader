package space.liushenme.markdownreader.ui.screens.statistics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.repository.ReadingSessionStats
import space.liushenme.markdownreader.data.repository.ReadingTrendAggregator
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.BookCoverColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    navController: NavController,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val stats by viewModel.statistics.collectAsState()
    val readingTrend by viewModel.readingTrend.collectAsState()
    val books by viewModel.books.collectAsState()

    val pageBg = shelfStylePageBackground()

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = "阅读统计",
                    onNavigateBack = { navController.navigateUp() }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(pageBg)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StatisticsOverview(stats) }
            item { WeeklyReadingGoalCard(readingTrend) }
            item { ReadingTrendCard(readingTrend) }
            item { RecentlyReadBooks(books, navController) }
        }
    }
}

@Composable
private fun StatisticsOverview(stats: ReadingStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "阅读总览",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    value = "${stats.totalBooks}",
                    label = "书籍总数"
                )
                StatItem(
                    icon = Icons.Default.CheckCircle,
                    value = "${stats.finishedBooks}",
                    label = "已读完"
                )
                StatItem(
                    icon = Icons.Default.Schedule,
                    value = ReadingSessionStats.formatReadDuration(stats.totalReadMinutes),
                    label = "阅读时长"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.TextFields,
                    value = formatNumber(stats.totalReadChars),
                    label = "阅读字数"
                )
                StatItem(
                    icon = Icons.Default.Bookmark,
                    value = "${stats.totalBookmarks}",
                    label = "书签数"
                )
                StatItem(
                    icon = Icons.Default.Highlight,
                    value = "${stats.totalHighlights}",
                    label = "划线数"
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun WeeklyReadingGoalCard(trend: List<DailyReading>) {
    val goalMinutesPerDay = ReadingTrendAggregator.WEEKLY_GOAL_MINUTES
    val goalDays = trend.size.coerceAtLeast(1)
    val dayCompletions = trend.map { it.minutes >= goalMinutesPerDay }
    val completedDays = dayCompletions.count { it }
    val progress = completedDays.toFloat() / goalDays

    // 进度条动画
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label = "goalProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "本周阅读目标",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$completedDays / $goalDays 天",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 本周每天的完成状态（周一至周日，与趋势图列顺序一致）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                trend.forEachIndexed { index, day ->
                    val isCompleted = dayCompletions.getOrElse(index) { false }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = if (isCompleted)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = if (isCompleted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingTrendCard(trend: List<DailyReading>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "本周阅读趋势",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            val hasReadingData = trend.any { it.minutes > 0 }

            if (trend.isEmpty()) {
                // 优化后的空状态插图
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 用 Canvas 绘制一个简单的书本插图
                        val bookPaper = MaterialTheme.colorScheme.surfaceVariant
                        val bookSpine = MaterialTheme.colorScheme.outlineVariant
                        val bookLine = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        Canvas(
                            modifier = Modifier.size(80.dp)
                        ) {
                            // 书本主体
                            drawRoundRect(
                                color = bookPaper,
                                topLeft = Offset(size.width * 0.15f, size.height * 0.2f),
                                size = Size(size.width * 0.7f, size.height * 0.65f),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            // 书脊
                            drawRoundRect(
                                color = bookSpine,
                                topLeft = Offset(size.width * 0.15f, size.height * 0.2f),
                                size = Size(size.width * 0.1f, size.height * 0.65f),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            // 书页线条
                            for (i in 1..3) {
                                val lineY = size.height * (0.35f + i * 0.12f)
                                drawLine(
                                    color = bookLine,
                                    start = Offset(size.width * 0.32f, lineY),
                                    end = Offset(size.width * 0.75f, lineY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "暂无阅读数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            "开始阅读后将显示趋势图表",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                val maxMinutes = trend.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                val avgMinutes = trend.map { it.minutes }.average().toFloat()
                val chartHeight = 132.dp
                val minuteLabelHeight = 14.dp
                val barAreaHeight = chartHeight - minuteLabelHeight

                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            trend.forEach { day ->
                                val heightFraction = if (day.minutes <= 0) {
                                    0.06f
                                } else {
                                    (day.minutes.toFloat() / maxMinutes).coerceIn(0.12f, 1f)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (day.minutes > 0) {
                                        Text(
                                            text = "${day.minutes}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            modifier = Modifier.height(minuteLabelHeight),
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(minuteLabelHeight))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(barAreaHeight * heightFraction)
                                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                            .background(
                                                if (day.minutes > 0) {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                            MaterialTheme.colorScheme.primary,
                                                        ),
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                            MaterialTheme.colorScheme.surfaceVariant,
                                                        ),
                                                    )
                                                },
                                            ),
                                    )
                                }
                            }
                        }

                        if (hasReadingData && avgMinutes > 0) {
                            val avgFraction = (avgMinutes / maxMinutes).coerceIn(0f, 1f)
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val lineHeightPx = with(density) {
                                chartHeight.toPx() - minuteLabelHeight.toPx() -
                                    barAreaHeight.toPx() * avgFraction
                            }
                            val refLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawLine(
                                    color = refLineColor,
                                    start = Offset(0f, lineHeightPx),
                                    end = Offset(size.width, lineHeightPx),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                    ),
                                )
                            }

                            Text(
                                text = "平均 ${avgMinutes.toInt()}分",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                                ),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 2.dp, end = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        trend.forEach { day ->
                            Text(
                                text = day.dayOfWeek,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (day.minutes > 0) 0.85f else 0.45f,
                                ),
                            )
                        }
                    }

                    if (!hasReadingData) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "开始阅读后，将按每日阅读时长显示趋势",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyReadBooks(books: List<BookEntity>, navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "最近阅读",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "还没有阅读记录",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                val displayed = books.take(5)
                displayed.forEachIndexed { index, book ->
                    BookProgressItem(book) {
                        navController.navigate(space.liushenme.markdownreader.navigation.AppRoutes.reader(book.id))
                    }
                    if (index < displayed.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookProgressItem(book: BookEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面颜色块
        val coverColor = BookCoverColors[book.coverColor % BookCoverColors.size]

        Box(
            modifier = Modifier
                .size(40.dp, 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(coverColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            if (!book.author.isNullOrEmpty()) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { book.readingProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = "${(book.readingProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // 点击提示箭头
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "打开阅读",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 10000 -> "${number / 10000}万"
        number >= 1000 -> "${number / 1000}k"
        else -> number.toString()
    }
}

// 数据类
data class ReadingStatistics(
    val totalBooks: Int = 0,
    val finishedBooks: Int = 0,
    /** 累计阅读分钟数（优先来自 reading_progress，否则按进度估算）。 */
    val totalReadMinutes: Int = 0,
    val totalReadChars: Int = 0,
    val totalBookmarks: Int = 0,
    val totalHighlights: Int = 0
)

data class DailyReading(
    val dayOfWeek: String,
    val minutes: Int
)

private fun statisticsPreviewStats(): ReadingStatistics = ReadingStatistics(
    totalBooks = 12,
    finishedBooks = 4,
    totalReadMinutes = 36 * 60,
    totalReadChars = 1_250_000,
    totalBookmarks = 28,
    totalHighlights = 56
)

private fun statisticsPreviewTrend(): List<DailyReading> = listOf(
    DailyReading("一", 25),
    DailyReading("二", 10),
    DailyReading("三", 45),
    DailyReading("四", 0),
    DailyReading("五", 30),
    DailyReading("六", 60),
    DailyReading("日", 15),
)

private fun statisticsPreviewRecentBooks(): List<BookEntity> {
    val now = java.util.Date()
    return listOf(
        BookEntity(
            id = 1L,
            title = "活着",
            author = "余华",
            filePath = "/preview/1.txt",
            lastReadTime = now,
            readingProgress = 0.12f,
            coverColor = 2,
        ),
        BookEntity(
            id = 2L,
            title = "Markdown 测试",
            author = null,
            filePath = "/preview/2.md",
            lastReadTime = now,
            readingProgress = 0.59f,
            coverColor = 4,
        ),
    )
}

/** 仅用于 @Preview：不依赖 Hilt / Activity。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsScreenPreviewImpl(navController: NavController) {
    val stats = remember { statisticsPreviewStats() }
    val trend = remember { statisticsPreviewTrend() }
    val books = remember { statisticsPreviewRecentBooks() }

    val pageBg = shelfStylePageBackground()

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = "阅读统计",
                    onNavigateBack = { navController.navigateUp() }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(pageBg)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StatisticsOverview(stats) }
            item { WeeklyReadingGoalCard(trend) }
            item { ReadingTrendCard(trend) }
            item { RecentlyReadBooks(books, navController) }
        }
    }
}

@Preview(showBackground = true, name = "阅读统计")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsScreenPreview() {
    MarkdownReaderTheme {
        StatisticsScreenPreviewImpl(navController = rememberNavController())
    }
}
