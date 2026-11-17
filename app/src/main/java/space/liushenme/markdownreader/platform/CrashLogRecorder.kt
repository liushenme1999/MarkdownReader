package space.liushenme.markdownreader.platform

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import space.liushenme.markdownreader.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志落地。
 *
 * 只记录「堆栈 + 版本 + 设备」这类诊断信息，不写书籍正文或用户数据。
 * 使用方式：`Application.onCreate` 里调用 [install]，接管默认
 * `UncaughtExceptionHandler`（写入后原样交还给系统处理器，行为与原来一致）。
 *
 * 日志目录：`filesDir/crash-logs/`，最多保留 [MAX_FILES] 份、单份截断到 [MAX_CHARS]，
 * 用户可在「我的 → 关于应用」里查看/分享最近一份。
 */
object CrashLogRecorder {

    private const val TAG = "CrashLogRecorder"
    private const val DIR_NAME = "crash-logs"
    private const val MAX_FILES = 20
    private const val MAX_CHARS = 256 * 1024
    private const val PREFIX = "crash-"

    @Volatile
    private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        val context = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeLog(context, thread, throwable) }
                .onFailure { Log.w(TAG, "write crash log failed: ${it.javaClass.simpleName}") }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Log.e(TAG, "uncaught exception on ${thread.name}", throwable)
                Process.killProcess(Process.myPid())
                Runtime.getRuntime().exit(10)
            }
        }
    }

    /** 最近一份崩溃日志内容；没有则返回 null。 */
    fun readLatest(context: android.content.Context): String? {
        val file = logFiles(context).firstOrNull() ?: return null
        return runCatching { file.readText().take(MAX_CHARS) }.getOrNull()
    }

    fun logFiles(context: android.content.Context): List<File> {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            .orEmpty()
            .sortedByDescending { it.name }
    }

    private fun writeLog(
        context: android.content.Context,
        thread: Thread,
        throwable: Throwable,
    ) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.isDirectory && !dir.mkdirs()) return
        trimOldFiles(dir)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "$PREFIX$stamp.txt")
        val body = buildString {
            appendLine("time: ${Date()}")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("thread: ${thread.name} (id=${thread.id})")
            appendLine("stack:")
            appendLine(throwable.stackTraceToString())
            appendLine()
            appendLine("app stack snapshot:")
            appendLine(currentMainThreadStack())
        }.take(MAX_CHARS)
        file.writeText(body)
        trimOldFiles(dir)
    }

    /** 主线程栈快照：崩溃常发生在后台线程，主线程当时的调用栈有助于定位。 */
    private fun currentMainThreadStack(): String = runCatching {
        val main = android.os.Looper.getMainLooper().thread
        val buffer = StringWriter()
        PrintWriter(buffer).use { writer ->
            writer.println("main thread state: ${main.state}")
            main.stackTrace.take(40).forEach { writer.println("  at $it") }
        }
        buffer.toString()
    }.getOrDefault("")

    private fun trimOldFiles(dir: File) {
        val files = dir.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.name }
            .orEmpty()
        files.drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }
}
