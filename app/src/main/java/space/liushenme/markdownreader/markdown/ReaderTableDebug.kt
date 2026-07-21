package space.liushenme.markdownreader.markdown

import android.text.Layout
import android.text.Spanned
import android.util.Log
import android.widget.TextView

/**
 * 表格行间空白排查日志。Logcat 过滤：`adb logcat -s ReaderTableDbg`
 *
 * 关注字段：
 * - gapPx / emptyLines：同一张表内相邻行之间的空隙
 * - readyCount：二次 layout 后 metrics 是否就绪
 * - suspect=ZEBRA_TRANSPARENT：偶数行透明底造成的「假空行」观感
 */
internal object ReaderTableDebug {
    const val TAG = "ReaderTableDbg"

    private const val MAX_ROWS_LOGGED = 12
    private const val GET_SIZE_LOG_EVERY = 40
    private const val CHOOSE_HEIGHT_LOG_EVERY = 20

    @Volatile private var getSizeCount = 0
    @Volatile private var chooseHeightCount = 0
    @Volatile private var dumpGeneration = 0L

    fun logGetSize(
        start: Int,
        end: Int,
        rawSize: Int,
        returnedSize: Int,
        metricsReady: Boolean,
        fmAscent: Int?,
        fmDescent: Int?,
    ) {
        val n = ++getSizeCount
        if (n % GET_SIZE_LOG_EVERY != 1) return
        Log.d(
            TAG,
            "getSize#$n [$start,$end) rawW=$rawSize retW=$returnedSize ready=$metricsReady " +
                "fm(a=$fmAscent,d=$fmDescent) mult=${ReaderTableSpacing.lineSpacingMultiplier}",
        )
    }

    fun logChooseHeight(
        start: Int,
        end: Int,
        skipped: String?,
        beforeAscent: Int,
        beforeDescent: Int,
        afterAscent: Int?,
        afterDescent: Int?,
        metricsReady: Boolean,
    ) {
        val n = ++chooseHeightCount
        if (n % CHOOSE_HEIGHT_LOG_EVERY != 1) return
        if (skipped != null) {
            Log.d(
                TAG,
                "chooseHeight#$n [$start,$end) SKIP $skipped ready=$metricsReady " +
                    "fm(a=$beforeAscent,d=$beforeDescent) mult=${ReaderTableSpacing.lineSpacingMultiplier}",
            )
            return
        }
        Log.d(
            TAG,
            "chooseHeight#$n [$start,$end) ready=$metricsReady " +
                "fmBefore(a=$beforeAscent,d=$beforeDescent) " +
                "fmAfter(a=$afterAscent,d=$afterDescent) mult=${ReaderTableSpacing.lineSpacingMultiplier}",
        )
    }

    /** 在 setText / invalidate 后 dump 真实 Layout 行距。 */
    fun scheduleDump(textView: TextView, reason: String) {
        val gen = ++dumpGeneration
        textView.post {
            if (gen != dumpGeneration) return@post
            dumpTableLayout(textView, "$reason#0")
        }
        // TableRowsScheduler 会 post setText；第一次 delayed 常仍未 ready，再采两次。
        textView.postDelayed({
            if (gen != dumpGeneration) return@postDelayed
            dumpTableLayout(textView, "$reason#120ms")
        }, 120L)
        textView.postDelayed({
            if (gen != dumpGeneration) return@postDelayed
            dumpTableLayout(textView, "$reason#400ms")
        }, 400L)
    }

    fun dumpTableLayout(textView: TextView, reason: String) {
        val text = textView.text
        if (text !is Spanned) {
            Log.d(TAG, "dump($reason) text not Spanned")
            return
        }
        val layout = textView.layout
        if (layout == null) {
            Log.d(TAG, "dump($reason) layout=null tvW=${textView.width}")
            return
        }
        val rows = text.getSpans(0, text.length, ReaderTableRowSpan::class.java)
            .sortedBy { text.getSpanStart(it) }
        if (rows.isEmpty()) {
            Log.d(TAG, "dump($reason) no table rows")
            return
        }

        val readyCount = rows.count { it.metricsReady }
        val mult = ReaderTableSpacing.lineSpacingMultiplier
        val contentW = (textView.width - textView.paddingLeft - textView.paddingRight).coerceAtLeast(0)
        Log.d(
            TAG,
            "dump($reason) rows=${rows.size} ready=$readyCount/${rows.size} lineCount=${layout.lineCount} " +
                "layoutW=${layout.width} contentW=$contentW tvW=${textView.width} " +
                "mult=$mult textSize=${textView.textSize} spacingMult=${textView.lineSpacingMultiplier}",
        )

        var maxGap = 0
        var emptyLinePairs = 0
        var adjacentPairs = 0
        val limit = minOf(rows.size, MAX_ROWS_LOGGED)
        for (i in 0 until limit) {
            val row = rows[i]
            val s = text.getSpanStart(row)
            val e = text.getSpanEnd(row)
            val line = layout.getLineForOffset(s)
            val top = layout.getLineTop(line)
            val bot = layout.getLineBottom(line)
            val h = bot - top
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            val raw = text.subSequence(lineStart, lineEnd).toString()
                .replace("\n", "\\n")
                .replace("\u00a0", "NBSP")
            Log.d(
                TAG,
                "  row$i ready=${row.metricsReady} span=[$s,$e) line=$line " +
                    "h=$h top=$top bot=$bot lineChars='$raw'",
            )

            if (i >= limit - 1) continue
            val next = rows[i + 1]
            val s2 = text.getSpanStart(next)
            // 只统计同一张表内相邻行（中间只有换行）；跨表大段正文不算 EMPTY。
            if (s2 - e > 2) {
                Log.d(TAG, "  skip row$i->${i + 1}: different table/block (delta=${s2 - e})")
                continue
            }
            adjacentPairs++
            val between = text.subSequence(e, s2).toString()
                .replace("\n", "\\n")
                .replace("\u00a0", "NBSP")
            val line2 = layout.getLineForOffset(s2)
            val gap = layout.getLineTop(line2) - layout.getLineBottom(line)
            maxGap = maxOf(maxGap, gap)
            if (line2 > line + 1) {
                var tallEmpty = 0
                for (li in (line + 1) until line2) {
                    val a = layout.getLineStart(li)
                    val b = layout.getLineEnd(li)
                    val mid = text.subSequence(a, b).toString()
                        .replace("\n", "\\n")
                        .replace("\u00a0", "NBSP")
                    val mh = layout.getLineBottom(li) - layout.getLineTop(li)
                    if (mh > 1) tallEmpty++
                    Log.d(TAG, "  EMPTY L$li [$a,$b) '$mid' h=$mh")
                }
                // h≈0 的挤出空行算修复成功，不计 EMPTY_LINES
                if (tallEmpty > 0 || gap > 1) {
                    emptyLinePairs++
                    Log.d(
                        TAG,
                        "  BETWEEN row$i->${i + 1}: chars='$between' lines $line->$line2 gapPx=$gap",
                    )
                } else {
                    Log.d(
                        TAG,
                        "  OK row$i->${i + 1}: collapsed break lines $line->$line2 gapPx=$gap",
                    )
                }
            } else if (gap > 1) {
                Log.d(
                    TAG,
                    "  GAP row$i->${i + 1}: chars='$between' lines $line->$line2 gapPx=$gap",
                )
            } else {
                Log.d(TAG, "  OK row$i->${i + 1}: adjacent lines $line->$line2 gapPx=$gap")
            }
        }
        if (rows.size > MAX_ROWS_LOGGED) {
            Log.d(TAG, "  ... ${rows.size - MAX_ROWS_LOGGED} more rows omitted")
        }
        val suspect = when {
            emptyLinePairs > 0 -> "EMPTY_LINES"
            maxGap > 1 -> "LINE_BOX_GAP"
            readyCount < rows.size -> "METRICS_NOT_READY"
            else -> "OK_LAYOUT(check_zebra_bg_if_visual_gap)"
        }
        Log.d(
            TAG,
            "dump($reason) summary adjacentPairs=$adjacentPairs maxGapPx=$maxGap " +
                "emptyLinePairs=$emptyLinePairs ready=$readyCount/${rows.size} suspect=$suspect",
        )
    }

    fun describeLine(layout: Layout, line: Int): String {
        if (line < 0 || line >= layout.lineCount) return "L$line(out)"
        return "L$line h=${layout.getLineBottom(line) - layout.getLineTop(line)} " +
            "top=${layout.getLineTop(line)} bot=${layout.getLineBottom(line)}"
    }
}
