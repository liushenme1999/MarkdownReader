package space.liushenme.markdownreader.ui.screens.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import space.liushenme.markdownreader.navigation.AppRoutes
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import androidx.navigation.compose.rememberNavController
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val shelfBg = shelfStylePageBackground()
    val profile by viewModel.profile.collectAsState()

    // 对话框状态
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    // 阅读数据（预留数据接口，暂时硬编码）
    val todayReadingMinutes = 0
    val totalReadingDays = 0
    val continuousReadingDays = 0

    // 关于应用对话框
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    "关于 MD阅读器",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("版本号：1.0.0")
                    HorizontalDivider()
                    Text("支持的文件格式：")
                    Text("  - Markdown (.md)", style = MaterialTheme.typography.bodySmall)
                    Text("  - 文本文件 (.txt)", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text(
                        "一款轻量级的 Markdown 阅读器，支持阅读统计、笔记管理等功能。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    // 清理缓存确认对话框
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    "清理缓存",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("将清理网络图片等缓存数据，不影响书架与阅读进度。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    viewModel.clearCache { bytesFreed ->
                        val message = if (bytesFreed > 0) {
                            "缓存已清理（${formatCacheBytes(bytesFreed)}）"
                        } else {
                            "缓存已清理"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        containerColor = shelfBg,
        topBar = {
            ShelfStyleTopBarBackground(shelfBg) {
            TopAppBar(
                // Scaffold 已对 topBar 施加状态栏区域；避免与 TopAppBar 默认 windowInsets 叠加成双倍顶距
                windowInsets = WindowInsets(),
                title = {
                    Text(
                        "我的",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(shelfBg)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ========== 用户头像区域 ==========
            Spacer(modifier = Modifier.height(12.dp))

            ProfileHeaderSection(
                profile = profile,
                onAvatarPicked = viewModel::updateAvatar,
                onNicknameChange = viewModel::setNickname,
                onSignatureChange = viewModel::setSignature,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ========== 阅读数据概览卡片 ==========
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                val overviewScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(overviewScroll)
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    // 今日阅读时长
                    OverviewDataItem(
                        value = "${todayReadingMinutes}",
                        unit = "分钟",
                        label = "今日阅读",
                        icon = Icons.Default.Schedule,
                        modifier = Modifier.widthIn(min = 92.dp)
                    )
                    // 分隔线
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                    )
                    // 累计阅读天数
                    OverviewDataItem(
                        value = "${totalReadingDays}",
                        unit = "天",
                        label = "累计阅读",
                        icon = Icons.Default.CalendarMonth,
                        modifier = Modifier.widthIn(min = 92.dp)
                    )
                    // 分隔线
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                    )
                    // 连续阅读天数
                    OverviewDataItem(
                        value = "${continuousReadingDays}",
                        unit = "天",
                        label = "连续阅读",
                        icon = Icons.Default.LocalFireDepartment,
                        modifier = Modifier.widthIn(min = 92.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========== 功能菜单区域 ==========
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.05f)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    // 阅读统计
                    ProfileMenuItem(
                        icon = Icons.Default.BarChart,
                        title = "阅读统计",
                        subtitle = "查看阅读数据与趋势",
                        onClick = { navController.navigate(AppRoutes.STATISTICS) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    // 笔记管理
                    ProfileMenuItem(
                        icon = Icons.Default.EditNote,
                        title = "笔记管理",
                        subtitle = "管理阅读笔记与标注",
                        onClick = { navController.navigate(AppRoutes.NOTES) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    // 阅读设置
                    ProfileMenuItem(
                        icon = Icons.Default.Settings,
                        title = "阅读设置",
                        subtitle = "外观、主题、字体、行距、翻页",
                        onClick = { navController.navigate(AppRoutes.READING_SETTINGS) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== 更多选项区域 ==========
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.05f)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    // 关于应用
                    ProfileMenuItem(
                        icon = Icons.Default.Info,
                        title = "关于应用",
                        subtitle = "版本信息与支持格式",
                        onClick = { showAboutDialog = true }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    // 清理缓存
                    ProfileMenuItem(
                        icon = Icons.Default.DeleteSweep,
                        title = "清理缓存",
                        subtitle = "释放存储空间",
                        onClick = { showClearCacheDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ========== 底部版本号 ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "MD阅读器 v1.0.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/**
 * 阅读数据概览单项
 */
@Composable
private fun OverviewDataItem(
    value: String,
    unit: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * 个人中心菜单项
 */
@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

private fun formatCacheBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Preview(showBackground = true, name = "我的")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreenPreview() {
    MarkdownReaderTheme {
        ProfileScreen(navController = rememberNavController())
    }
}
