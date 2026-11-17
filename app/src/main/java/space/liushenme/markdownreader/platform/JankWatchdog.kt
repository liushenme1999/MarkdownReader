package space.liushenme.markdownreader.platform

import android.view.Choreographer
import android.util.Log

/**
 * 卡顿 / 主线程阻塞看门狗。
 *
 * 用 [Choreographer] 相邻两帧的时间差衡量主线程是否被卡住：单帧超过
 * [THRESHOLD_MS] 时在 logcat 打一行 `ReaderJank`，并附当前主线程调用栈，
 * 便于用户反馈「阅读页偶发卡住」时直接从日志定位。
 *
 * 限流：[MIN_LOG_INTERVAL_MS] 内最多输出一次，避免长卡顿刷屏。
 * 开销仅为每帧一次 long 比较，release 包同样启用（只输出日志，不做任何 UI 操作）。
 */
object JankWatchdog {

    private const val TAG = "ReaderJank"
    private const val THRESHOLD_MS = 400L
    private const val MIN_LOG_INTERVAL_MS = 60_000L

    @Volatile
    private var installed = false

    private var lastFrameMs = 0L
    private var lastLogMs = 0L

    fun install() {
        if (installed) return
        installed = true
        val choreographer = Choreographer.getInstance()
        choreographer.postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val nowMs = frameTimeNanos / 1_000_000L
                if (lastFrameMs != 0L) {
                    val delta = nowMs - lastFrameMs
                    if (delta > THRESHOLD_MS && nowMs - lastLogMs > MIN_LOG_INTERVAL_MS) {
                        lastLogMs = nowMs
                        runCatching {
                            Log.w(TAG, "frame gap ${delta}ms\n${mainThreadStack()}")
                        }
                    }
                }
                lastFrameMs = nowMs
                choreographer.postFrameCallback(this)
            }
        })
    }

    private fun mainThreadStack(): String = StringBuilder().apply {
        appendLine("main thread state: ${android.os.Looper.getMainLooper().thread.state}")
        android.os.Looper.getMainLooper().thread.stackTrace.take(30).forEach {
            appendLine("  at $it")
        }
    }.toString()
}
