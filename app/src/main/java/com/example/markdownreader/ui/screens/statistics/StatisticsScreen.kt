package com.example.markdownreader.ui.screens.statistics

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.ui.theme.BookCoverColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    navController: NavController,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val stats by viewModel.statistics.collectAsState()
    val readingTrend by viewModel.readingTrend.collectAsState()
    val books by viewModel.books.collectAsState()

    // 状态栏适配
    val view = LocalView.current
    val systemInDarkTheme = isSystemInDarkTheme()
    DisposableEffect(systemInDarkTheme) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !systemInDarkTheme
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "阅读统计",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StatisticsOverview(stats) }
            item { WeeklyReadingGoalCard() }
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
                    icon = Icons.Default.MenuBook,
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
                    value = "${stats.totalReadTime}小时",
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
private fun WeeklyReadingGoalCard() {
    val goalDays = 7
    val completedDays = 3
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

            // 本周每天的完成状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                dayLabels.forEachIndexed { index, label ->
                    val isCompleted = index < completedDays
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
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
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
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
                "近7天阅读趋势",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                // 优化的柱状图
                val maxMinutes = trend.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                val avgMinutes = trend.map { it.minutes }.average().toFloat()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    // 柱状图
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp) // 为上方数字留空间
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        trend.forEach { day ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                // 柱子上方显示分钟数
                                Text(
                                    text = "${day.minutes}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (day.minutes > 0)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                val heightFraction = (day.minutes.toFloat() / maxMinutes).coerceIn(0.08f, 1f)
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (day.minutes > 0) {
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                        MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                )
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = day.dayOfWeek,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // 平均线（虚线效果）
                    if (avgMinutes > 0) {
                        val avgFraction = (avgMinutes / maxMinutes).coerceIn(0f, 1f)
                        val chartHeight = 180.dp - 20.dp // 减去顶部数字空间
                        val lineHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            (chartHeight * (1f - avgFraction)).toPx()
                        }
                        val refLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            drawLine(
                                color = refLineColor,
                                start = Offset(0f, lineHeightPx),
                                end = Offset(size.width, lineHeightPx),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                                )
                            )
                        }

                        // 平均值标签
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                        ) {
                            Text(
                                text = "平均 ${avgMinutes.toInt()}分",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                                )
                            )
                        }
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
                        navController.navigate("reader/${book.id}")
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
                progress = book.readingProgress,
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
    val totalReadTime: Int = 0, // 小时
    val totalReadChars: Int = 0,
    val totalBookmarks: Int = 0,
    val totalHighlights: Int = 0
)

data class DailyReading(
    val dayOfWeek: String,
    val minutes: Int
)
