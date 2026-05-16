package com.example.markdownreader

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.markdownreader.ui.screens.bookshelf.BookshelfScreen
import com.example.markdownreader.ui.screens.notes.NotesScreen
import com.example.markdownreader.ui.screens.profile.ProfileScreen
import com.example.markdownreader.ui.screens.reader.ReaderScreen
import com.example.markdownreader.ui.screens.statistics.StatisticsScreen
import com.example.markdownreader.ui.theme.MainNavTabSelectedTint
import com.example.markdownreader.ui.theme.MainNavigationBarBackground
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * MIUI 等机型在 Compose + AndroidView（阅读器 TextView）场景下会派发 HOVER 事件；
     * 主线程略卡时易触发 [AndroidComposeView] 内部
     * `IllegalStateException: The ACTION_HOVER_EXIT event was not cleared` 闪退。
     * 在 Activity 层消费悬停类事件，不影响普通触摸滚动与点击。
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MarkdownReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    var bookshelfHideBottomNav by remember { mutableStateOf(false) }
                    val showBottomBar =
                        (currentRoute == "bookshelf" || currentRoute == "profile") &&
                            !bookshelfHideBottomNav

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar(
                                    containerColor = MainNavigationBarBackground
                                ) {
                                    MainBottomNavItem(
                                        selected = currentRoute == "bookshelf",
                                        onClick = {
                                            navController.navigate("bookshelf") {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = Icons.AutoMirrored.Filled.MenuBook,
                                        label = "书架"
                                    )
                                    MainBottomNavItem(
                                        selected = currentRoute == "profile",
                                        onClick = {
                                            navController.navigate("profile") {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = Icons.Default.Person,
                                        label = "我的"
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        // 阅读页全屏自管 inset；书架/我的/笔记/统计与 Tab 页共用外层 Scaffold 顶栏留白
                        val useMainScaffoldInsets = when (currentRoute) {
                            "bookshelf", "profile", "notes", "statistics" -> true
                            else -> false
                        }
                        NavHost(
                            navController = navController,
                            startDestination = "bookshelf",
                            modifier = Modifier.padding(
                                if (useMainScaffoldInsets) paddingValues else PaddingValues()
                            )
                        ) {
                            composable("bookshelf") {
                                BookshelfScreen(
                                    navController = navController,
                                    onSelectionModeChange = { bookshelfHideBottomNav = it }
                                )
                            }
                            composable("profile") {
                                ProfileScreen(navController = navController)
                            }
                            composable("reader/{bookId}") { backStackEntry ->
                                val bookId =
                                    backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
                                ReaderScreen(
                                    navController = navController,
                                    bookId = bookId
                                )
                            }
                            composable("notes") {
                                NotesScreen(navController = navController)
                            }
                            composable("statistics") {
                                StatisticsScreen(navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MainBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    NavigationBarItem(
        icon = {
            Icon(icon, contentDescription = label)
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MainNavTabSelectedTint,
            selectedTextColor = MainNavTabSelectedTint,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "主导航底栏")
@Composable
private fun MainBottomNavigationPreview() {
    MarkdownReaderTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MainNavigationBarBackground) {
                    MainBottomNavItem(
                        selected = true,
                        onClick = { },
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        label = "书架"
                    )
                    MainBottomNavItem(
                        selected = false,
                        onClick = { },
                        icon = Icons.Default.Person,
                        label = "我的"
                    )
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                Text(
                    text = "（NavHost 仅在运行时显示）",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
