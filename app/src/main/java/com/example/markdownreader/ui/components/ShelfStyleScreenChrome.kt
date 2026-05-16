package com.example.markdownreader.ui.components

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.example.markdownreader.ui.theme.BookshelfPageBackground
import com.example.markdownreader.ui.theme.BookshelfPageBackgroundDark

/** 与书架 / 我的页一致的页面背景色 */
@Composable
fun shelfStylePageBackground(): Color =
    if (isSystemInDarkTheme()) BookshelfPageBackgroundDark else BookshelfPageBackground

/** 与书架 / 我的页一致的状态栏颜色与图标深浅 */
@Composable
fun ShelfStyleStatusBarEffect(backgroundColor: Color) {
    val view = LocalView.current
    val systemInDarkTheme = isSystemInDarkTheme()
    DisposableEffect(backgroundColor, systemInDarkTheme) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val prevColor = window.statusBarColor
        val prevLightStatusBars = controller.isAppearanceLightStatusBars
        window.statusBarColor = backgroundColor.toArgb()
        controller.isAppearanceLightStatusBars = !systemInDarkTheme
        onDispose {
            window.statusBarColor = prevColor
            controller.isAppearanceLightStatusBars = prevLightStatusBars
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfStyleTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
