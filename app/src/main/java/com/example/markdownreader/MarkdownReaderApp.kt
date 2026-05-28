package com.example.markdownreader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.markdownreader.markdown.DiagramWebViewRenderer
import com.example.markdownreader.platform.MainThreadCrashGuard
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference

@HiltAndroidApp
class MarkdownReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 兜住 Android framework 已知 Editor / Selection setSpan 越界 crash，
        // 详见 [MainThreadCrashGuard] 注释；其他异常仍然正常 rethrow。
        MainThreadCrashGuard.install()
        registerActivityLifecycleCallbacks(ForegroundActivityTracker)
    }

    companion object {
        fun foregroundActivity(): Activity? = ForegroundActivityTracker.current?.get()
    }

    private object ForegroundActivityTracker : ActivityLifecycleCallbacks {
        var current: WeakReference<Activity>? = null

        override fun onActivityResumed(activity: Activity) {
            current = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (current?.get() === activity) {
                current = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            if (current?.get() === activity) {
                current = null
            }
            DiagramWebViewRenderer.releaseForActivity(activity)
        }
    }
}
