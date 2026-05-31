package space.liushenme.markdownreader

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import space.liushenme.markdownreader.intent.IncomingFileIntent
import space.liushenme.markdownreader.navigation.AppNavHost
import space.liushenme.markdownreader.navigation.AppRoutes
import space.liushenme.markdownreader.navigation.MarkdownLinkNavigation
import space.liushenme.markdownreader.ui.components.CompactMainBottomBar
import space.liushenme.markdownreader.ui.components.CompactMainBottomNavItem
import space.liushenme.markdownreader.ui.components.ShelfStyleStatusBarBackdrop
import space.liushenme.markdownreader.ui.components.ShelfStyleStatusBarEffect
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import space.liushenme.markdownreader.ui.screens.profile.AppSettingsViewModel
import space.liushenme.markdownreader.ui.theme.BookshelfPageBackground
import space.liushenme.markdownreader.ui.theme.BookshelfPageBackgroundDark
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appSettingsViewModel: AppSettingsViewModel by viewModels()

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private var pendingOpenUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenUri = IncomingFileIntent.extractOpenableUri(intent)
        setContent {
            val themeMode by appSettingsViewModel.appThemeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.resolveDarkTheme()

            ApplySystemBarStyles(darkTheme = darkTheme)

            MarkdownReaderTheme(darkTheme = darkTheme) {
                MainAppContent(
                    pendingOpenUri = pendingOpenUri,
                    onPendingOpenUriConsumed = { pendingOpenUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenUri = IncomingFileIntent.extractOpenableUri(intent)
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
private fun MainAppContent(
    pendingOpenUri: Uri? = null,
    onPendingOpenUriConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        MarkdownLinkNavigation.setupNavigation(navController)
        onDispose {
            MarkdownLinkNavigation.cleanup()
        }
    }
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
                AppNavHost(
                    navController = navController,
                    modifier = Modifier.padding(navHostPadding),
                    pendingOpenUri = pendingOpenUri,
                    onPendingExternalUriConsumed = onPendingOpenUriConsumed,
                    onSelectionModeChange = { bookshelfHideBottomNav = it },
                )
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
