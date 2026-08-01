package space.liushenme.markdownreader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.markdown.DiagramWebViewRenderer
import space.liushenme.markdownreader.platform.AppLocaleController
import space.liushenme.markdownreader.platform.MainThreadCrashGuard
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class MarkdownReaderApp : Application() {

    @Inject
    lateinit var readerSettingsRepository: ReaderSettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var localeSyncStarted = false

    override fun onCreate() {
        super.onCreate()
        // 兜住 Android framework 已知 Editor / Selection setSpan 越界 crash，
        // 详见 [MainThreadCrashGuard] 注释；其他异常仍然正常 rethrow。
        MainThreadCrashGuard.install()
        registerActivityLifecycleCallbacks(ForegroundActivityTracker)
        registerActivityLifecycleCallbacks(LocaleSyncCallbacks())
    }

    private fun startLocaleSyncIfNeeded() {
        if (localeSyncStarted) return
        if (!::readerSettingsRepository.isInitialized) return
        localeSyncStarted = true
        appScope.launch {
            readerSettingsRepository.appLanguage
                .distinctUntilChanged()
                .collect { AppLocaleController.apply(it) }
        }
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

    private inner class LocaleSyncCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            startLocaleSyncIfNeeded()
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
