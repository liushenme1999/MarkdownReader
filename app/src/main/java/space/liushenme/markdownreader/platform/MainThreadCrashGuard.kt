package space.liushenme.markdownreader.platform

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 主线程已知 framework bug 异常守护。
 *
 * **不要**当成万能 crash 兜底——这里只 swallow Android framework 自身在
 * `ViewTreeObserver.dispatchOnPreDraw` / `Editor` / `Selection` 链路里抛出的、
 * **应用层无法重写到的** 已知异常。其他异常一律 rethrow，让 ANR/Crash 报上来。
 *
 * 当前覆盖的 framework 问题：
 *
 * 1. 拖动文本选择句柄时，`Editor$SelectionStartHandleView.updateSelection` 把
 *    cursor offset 算成 -1，紧接着 `Selection.setSelection(spannable, -1, -1)`
 *    → `SpannableStringInternal.setSpan(-1, -1)` → `checkRange` 抛
 *    `IndexOutOfBoundsException: setSpan (-1 ... -1) starts before 0`。
 *
 *    异常发生在 `ViewTreeObserver.dispatchOnPreDraw` 里，不经过 TextView 的
 *    `onTouchEvent` / `performLongClick`，所以 SafeReaderTextView 那一层 catch
 *    拦不住。Google issuetracker 123307221 等多次报告，至今未修。
 *
 * 实现：在主线程 post 一个 Runnable，Runnable 内嵌一层 `Looper.loop()` 接管
 * message dispatch。识别为已知 framework 异常就 log + 继续 loop；否则 rethrow。
 * Swallow 的代价仅是「这一次选区状态可能丢失」，不会让 Compose / 业务状态错乱。
 */
object MainThreadCrashGuard {

    private const val TAG = "MainThreadCrashGuard"

    /**
     * 单段时间窗口内允许 swallow 的最大次数；超过就让异常正常抛上去。
     *
     * 目的：
     * 1) 防止 [Looper.loop] 嵌套深度无止境累积——每次 swallow 都让主线程 stack 深一层，
     *    极端情况下连续狂拖选区句柄会把栈耗光。
     * 2) 避免某个真实业务异常恰好命中 [shouldSwallow] 模式时被无限吞掉、表现成「点击没反应」。
     */
    private const val MAX_SWALLOWS_PER_WINDOW = 16
    private const val WINDOW_MS = 10_000L

    /** 已 swallow 一次完整堆栈，后续同类型同 message 只在 throttle 间隔后打 W 单行。 */
    private const val LOG_THROTTLE_MS = 1_000L

    private var windowStartMs = 0L
    private var swallowsInWindow = 0
    private var firstStackLogged = false
    private var lastShortLogMs = 0L
    private var lastShortLogKey: String? = null

    /** 在 Application.onCreate 里调用一次即可。 */
    fun install() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                    return@post
                } catch (t: Throwable) {
                    if (!shouldSwallow(t) || !registerSwallow()) throw t
                    logSwallowed(t)
                }
            }
        }
    }

    private fun registerSwallow(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (windowStartMs == 0L || now - windowStartMs > WINDOW_MS) {
            windowStartMs = now
            swallowsInWindow = 0
        }
        swallowsInWindow++
        return swallowsInWindow <= MAX_SWALLOWS_PER_WINDOW
    }

    private fun logSwallowed(t: Throwable) {
        val key = "${t.javaClass.name}:${t.message}"
        if (!firstStackLogged) {
            Log.w(TAG, "Swallowed known framework crash (first stack):", t)
            firstStackLogged = true
            lastShortLogKey = key
            lastShortLogMs = SystemClock.uptimeMillis()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (key != lastShortLogKey || now - lastShortLogMs >= LOG_THROTTLE_MS) {
            Log.w(TAG, "Swallowed again: ${t.javaClass.simpleName}: ${t.message}")
            lastShortLogKey = key
            lastShortLogMs = now
        }
    }

    private fun shouldSwallow(t: Throwable): Boolean {
        // 仅覆盖文本选区 setSpan 越界一类
        if (t !is IndexOutOfBoundsException) return false
        val msg = t.message.orEmpty()
        val looksLikeSetSpan = "setSpan" in msg ||
            "starts before" in msg ||
            "ends beyond" in msg ||
            "ends before start" in msg
        if (!looksLikeSetSpan) return false
        // 必须由 framework 文本选区相关栈触发；业务层抛 IOOBE 仍然报上来
        return t.stackTrace.any { f ->
            val c = f.className
            c.startsWith("android.widget.Editor") ||
                c.startsWith("android.text.Selection") ||
                c.startsWith("android.text.SpannableStringInternal")
        }
    }
}
