package space.liushenme.markdownreader

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import androidx.work.Configuration
import space.liushenme.markdownreader.data.local.BookContentHashBackfill
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.data.work.BackgroundWorkScheduler
import space.liushenme.markdownreader.markdown.DiagramWebViewRenderer
import space.liushenme.markdownreader.platform.AppLocaleController
import space.liushenme.markdownreader.platform.CrashLogRecorder
import space.liushenme.markdownreader.platform.JankWatchdog
import space.liushenme.markdownreader.platform.MainThreadCrashGuard
import space.liushenme.markdownreader.update.AppUpdateRepository
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class MarkdownReaderApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    @Inject
    lateinit var readerSettingsRepository: ReaderSettingsRepository

    @Inject
    lateinit var bookContentHashBackfill: BookContentHashBackfill

    @Inject
    lateinit var appUpdateRepository: AppUpdateRepository

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 仅 debug 包开启主线程 IO 检测：新增代码若在主线程读写磁盘/网络，
     * logcat 会直接打出 `StrictMode policy violation`（不崩溃、不影响 release）。
     */
    private fun installDebugStrictMode() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build(),
        )
    }

    @Volatile
    private var localeSyncStarted = false

    override fun onCreate() {
        super.onCreate()
        // 兜住 Android framework 已知 Editor / Selection setSpan 越界 crash，
        // 详见 [MainThreadCrashGuard] 注释；其他异常仍然正常 rethrow。
        installDebugStrictMode()
        // 先接管未捕获异常写崩溃日志，再装主线程框架异常守护（守护不吞的异常仍会走这里）。
        CrashLogRecorder.install(this)
        MainThreadCrashGuard.install()
        JankWatchdog.install()
        registerActivityLifecycleCallbacks(ForegroundActivityTracker)
        registerActivityLifecycleCallbacks(LocaleSyncCallbacks())
        appScope.launch(Dispatchers.IO) {
            bookContentHashBackfill.runIfNeeded()
        }
        appScope.launch {
            appUpdateRepository.refresh()
        }
        backgroundWorkScheduler.ensurePeriodicBackupScheduled()
        backgroundWorkScheduler.enqueueAutoBackup()
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
