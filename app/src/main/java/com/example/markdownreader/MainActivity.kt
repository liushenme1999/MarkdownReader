package com.example.markdownreader

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.markdownreader.navigation.AppRoutes
import com.example.markdownreader.ui.screens.bookshelf.BookshelfScreen
import com.example.markdownreader.ui.screens.notes.NotesScreen
import com.example.markdownreader.ui.screens.profile.AppSettingsViewModel
import com.example.markdownreader.ui.screens.profile.ProfileScreen
import com.example.markdownreader.ui.screens.profile.ReadingSettingsScreen
import com.example.markdownreader.ui.screens.reader.ReaderScreen
import com.example.markdownreader.ui.screens.statistics.StatisticsScreen
import com.example.markdownreader.ui.components.CompactMainBottomBar
import com.example.markdownreader.ui.components.CompactMainBottomNavItem
import com.example.markdownreader.ui.components.ShelfStyleStatusBarBackdrop
import com.example.markdownreader.ui.components.ShelfStyleStatusBarEffect
import com.example.markdownreader.ui.components.shelfStylePageBackground
import com.example.markdownreader.ui.theme.BookshelfPageBackground
import com.example.markdownreader.ui.theme.BookshelfPageBackgroundDark
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import com.example.markdownreader.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appSettingsViewModel: AppSettingsViewModel by viewModels()

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
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by appSettingsViewModel.appThemeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.resolveDarkTheme()

            ApplySystemBarStyles(darkTheme = darkTheme)

            MarkdownReaderTheme(darkTheme = darkTheme) {
                MainAppContent()
            }
        }
    }
}

@Composable
private fun ApplySystemBarStyles(darkTheme: Boolean) {
    val activity = LocalContext.current as? ComponentActivity
    val statusLight = BookshelfPageBackground.toArgb()
    val statusDark = BookshelfPageBackgroundDark.toArgb()
    val navLight = Color.White.toArgb()
    val navDark = BookshelfPageBackgroundDark.toArgb()

    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (darkTheme) {
                SystemBarStyle.dark(statusDark)
            } else {
                SystemBarStyle.light(statusLight, statusLight)
            },
            navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(navDark)
            } else {
                SystemBarStyle.light(navLight, navLight)
            },
        )
    }
}

@Composable
private fun MainAppContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val shelfPageBg = shelfStylePageBackground()
    val usesShelfStyleChrome = currentRoute in AppRoutes.shelfStyleRoutes
    if (usesShelfStyleChrome) {
        ShelfStyleStatusBarEffect(shelfPageBg)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (usesShelfStyleChrome) {
            shelfPageBg
        } else {
            MaterialTheme.colorScheme.background
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var bookshelfHideBottomNav by remember { mutableStateOf(false) }
            val showBottomBar =
                (currentRoute == AppRoutes.BOOKSHELF || currentRoute == AppRoutes.PROFILE) &&
                    !bookshelfHideBottomNav

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                bottomBar = {
                    if (showBottomBar) {
                        CompactMainBottomBar {
                            CompactMainBottomNavItem(
                                selected = currentRoute == AppRoutes.BOOKSHELF,
                                onClick = {
                                    navController.navigate(AppRoutes.BOOKSHELF) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                label = "书架",
                            )
                            CompactMainBottomNavItem(
                                selected = currentRoute == AppRoutes.PROFILE,
                                onClick = {
                                    navController.navigate(AppRoutes.PROFILE) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = Icons.Default.Person,
                                label = "我的",
                            )
                        }
                    }
                },
            ) { paddingValues ->
                val useMainScaffoldInsets = currentRoute in AppRoutes.shelfStyleRoutes
                val layoutDirection = LocalLayoutDirection.current
                val navHostPadding = if (useMainScaffoldInsets) {
                    PaddingValues(
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        top = paddingValues.calculateTopPadding(),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                        bottom = if (
                            bookshelfHideBottomNav && currentRoute == AppRoutes.BOOKSHELF
                        ) {
                            0.dp
                        } else {
                            paddingValues.calculateBottomPadding()
                        },
                    )
                } else {
                    PaddingValues()
                }
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.BOOKSHELF,
                    modifier = Modifier.padding(navHostPadding),
                ) {
                    composable(AppRoutes.BOOKSHELF) {
                        BookshelfScreen(
                            navController = navController,
                            onSelectionModeChange = { bookshelfHideBottomNav = it },
                        )
                    }
                    composable(AppRoutes.PROFILE) {
                        ProfileScreen(navController = navController)
                    }
                    composable(AppRoutes.READING_SETTINGS) {
                        ReadingSettingsScreen(navController = navController)
                    }
                    composable(AppRoutes.READER) { backStackEntry ->
                        val bookId =
                            backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
                        ReaderScreen(
                            navController = navController,
                            bookId = bookId,
                        )
                    }
                    composable(AppRoutes.NOTES) {
                        NotesScreen(navController = navController)
                    }
                    composable(AppRoutes.STATISTICS) {
                        StatisticsScreen(navController = navController)
                    }
                }
            }
            if (usesShelfStyleChrome) {
                ShelfStyleStatusBarBackdrop(
                    backgroundColor = shelfPageBg,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "主导航底栏")
@Composable
private fun MainBottomNavigationPreview() {
    MarkdownReaderTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                CompactMainBottomBar {
                    CompactMainBottomNavItem(
                        selected = true,
                        onClick = { },
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        label = "书架",
                    )
                    CompactMainBottomNavItem(
                        selected = false,
                        onClick = { },
                        icon = Icons.Default.Person,
                        label = "我的",
                    )
                }
            },
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background,
            ) {
                Text(
                    text = "（NavHost 仅在运行时显示）",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
