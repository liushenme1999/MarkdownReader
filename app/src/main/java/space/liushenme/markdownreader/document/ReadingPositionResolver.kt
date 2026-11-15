package space.liushenme.markdownreader.document

import kotlin.math.abs

/** 位置与进度允许的正常偏差比例（翻页、存盘时序造成的抖动远小于它）。 */
private const val POSITION_PROGRESS_TOLERANCE_RATIO = 0.05f

/** 位置与进度允许的正常偏差下限（字符）。 */
private const val POSITION_PROGRESS_TOLERANCE_MIN_CHARS = 50_000

/**
 * 解析「上次读到哪」（全书字符偏移）。
 *
 * 正常情况下 position 与 progress 指向同一处。但历史版本在窗口模式（TXT 分段加载）
 * 落盘时曾把全书偏移按窗口长度裁剪一次，结果 position 落在窗口起点附近、progress 仍在原处：
 * 书架显示 32%，打开却从 0.6% 开始（阅读历史看起来失效）。
 *
 * 两者严重不一致时以进度为准，把位置捡回原处；一致时仍以精确的 position 为准。
 */
internal fun resolveReadingOffset(position: Int, progress: Float, totalChars: Int): Int {
    val total = totalChars.coerceAtLeast(1)
    val safeProgress = progress.coerceIn(0f, 1f)
    val byProgress = (safeProgress * total).toInt().coerceIn(0, total)
    if (position <= 0) return if (safeProgress > 0f) byProgress else 0
    val byPosition = position.coerceIn(0, total)
    val tolerance = maxOf(
        POSITION_PROGRESS_TOLERANCE_MIN_CHARS,
        (total * POSITION_PROGRESS_TOLERANCE_RATIO).toInt(),
    )
    return if (abs(byPosition - byProgress) > tolerance) byProgress else byPosition
}
