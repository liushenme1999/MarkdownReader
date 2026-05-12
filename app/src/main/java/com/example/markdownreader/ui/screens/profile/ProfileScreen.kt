package com.example.markdownreader.ui.screens.profile

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.example.markdownreader.ui.theme.BookshelfPageBackground
import com.example.markdownreader.ui.theme.BookshelfPageBackgroundDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val view = LocalView.current
    val systemInDarkTheme = isSystemInDarkTheme()
    val shelfBg =
        if (systemInDarkTheme) BookshelfPageBackgroundDark
        else BookshelfPageBackground

    DisposableEffect(shelfBg, systemInDarkTheme) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val prevColor = window.statusBarColor
        val prevLightStatusBars = controller.isAppearanceLightStatusBars
        window.statusBarColor = shelfBg.toArgb()
        controller.isAppearanceLightStatusBars = systemInDarkTheme
        onDispose {
            window.statusBarColor = prevColor
            controller.isAppearanceLightStatusBars = prevLightStatusBars
        }
    }

    // 阅读页用 SideEffect 写状态栏；返回「我的」时须在本帧最后再写一次，避免仍显示阅读主题色
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        window.statusBarColor = shelfBg.toArgb()
        controller.isAppearanceLightStatusBars = systemInDarkTheme
    }

    Scaffold(
        containerColor = shelfBg,
        topBar = {
            TopAppBar(
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
                    containerColor = shelfBg,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(shelfBg)
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("statistics") }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("阅读统计", style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("notes") }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("笔记管理", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Markdown Reader",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
