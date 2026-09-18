package space.liushenme.markdownreader.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.system.SystemBarAppearance
import space.liushenme.markdownreader.ui.theme.shelfStylePageBackground as themeShelfStylePageBackground

/** 与书架 / 我的页一致的页面背景色（随应用主题切换） */
@Composable
fun shelfStylePageBackground(): Color = themeShelfStylePageBackground()

/** 与书架系页面背景一致的状态栏颜色与图标深浅（edge-to-edge + HyperOS 需多层配合）。 */
@Composable
fun ShelfStyleStatusBarEffect(backgroundColor: Color) {
    ShelfStyleSystemBarsEffect(
        backgroundColor = backgroundColor,
        applyNavigationBar = false,
    )
}

/**
 * 状态栏 + 导航栏随 [backgroundColor] 同步（含 onResume 重刷，适配 HyperOS 等机型）。
 */
@Composable
fun ShelfStyleSystemBarsEffect(
    backgroundColor: Color,
    applyNavigationBar: Boolean = true,
    navigationBarColor: Color = backgroundColor,
    iconContrastColor: Color = backgroundColor,
    systemBarsVisible: Boolean = true,
) {
    val view = LocalView.current
    val activity = view.context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val barArgb = backgroundColor.toArgb()
    val navArgb = navigationBarColor.toArgb()
    val contrastArgb = iconContrastColor.toArgb()
    val useLightBarIcons = ColorUtils.calculateLuminance(contrastArgb) > 0.5

    fun apply() {
        SystemBarAppearance.applyStatusBar(
            activity = activity,
            view = view,
            colorArgb = barArgb,
            lightStatusBarIcons = useLightBarIcons,
        )
        if (applyNavigationBar) {
            SystemBarAppearance.applyNavigationBar(
                activity = activity,
                view = view,
                colorArgb = navArgb,
                lightNavigationBarIcons = useLightBarIcons,
            )
        }
        SystemBarAppearance.setSystemBarsVisible(activity, view, systemBarsVisible)
    }

    SideEffect { apply() }

    DisposableEffect(lifecycleOwner, barArgb, navArgb, useLightBarIcons, applyNavigationBar, systemBarsVisible) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!systemBarsVisible) {
                SystemBarAppearance.setSystemBarsVisible(activity, view, true)
            }
        }
    }
}

/**
 * 在状态栏区域绘制与页面一致的色块（置于最顶层 Compose 子节点，避免 OEM 透明栏透出系统灰底）。
 */
@Composable
fun ShelfStyleStatusBarBackdrop(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(100f)
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(backgroundColor),
    )
}

@Composable
fun ShelfStyleTopBarBackground(
    backgroundColor: Color,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
    ) {
        content()
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_cd),
                )
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
