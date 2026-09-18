package space.liushenme.markdownreader.ui.system

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 统一设置系统栏外观。HyperOS / MIUI 在 edge-to-edge 下常忽略半透明 [statusBarColor]，
 * 需配合 [androidx.activity.SystemBarStyle] 与 Compose 顶栏色块（见 [ShelfStyleStatusBarBackdrop]）。
 */
object SystemBarAppearance {

    fun applyStatusBar(activity: Activity, view: View, colorArgb: Int, lightStatusBarIcons: Boolean) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        @Suppress("DEPRECATION")
        window.statusBarColor = colorArgb
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = lightStatusBarIcons
        }
    }

    fun applyNavigationBar(activity: Activity, view: View, colorArgb: Int, lightNavigationBarIcons: Boolean) {
        val window = activity.window
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.navigationBarColor = colorArgb
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightNavigationBars = lightNavigationBarIcons
        }
    }

    /**
     * 隐藏/显示状态栏与导航栏（含底部小白条）。离开阅读页时必须 [visible]=true，
     * 否则书架等页面会继续处在沉浸隐藏状态。
     */
    fun setSystemBarsVisible(activity: Activity, view: View, visible: Boolean) {
        val controller = WindowInsetsControllerCompat(activity.window, view)
        val types = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        if (visible) {
            controller.show(types)
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.hide(types)
        }
    }
}
