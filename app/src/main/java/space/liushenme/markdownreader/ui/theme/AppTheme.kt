package space.liushenme.markdownreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import space.liushenme.markdownreader.model.AppThemeMode

val LocalAppInDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AppThemeMode.resolveDarkTheme(): Boolean = when (this) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
}

@Composable
fun appInDarkTheme(): Boolean = LocalAppInDarkTheme.current

/** 书架 / 我的等 Tab 页背景 */
@Composable
fun shelfStylePageBackground(): Color =
    if (appInDarkTheme()) BookshelfPageBackgroundDark else BookshelfPageBackground

/** 底部主导航栏背景，随应用浅色/深色切换 */
@Composable
fun mainNavigationBarContainerColor(): Color = MaterialTheme.colorScheme.surface
