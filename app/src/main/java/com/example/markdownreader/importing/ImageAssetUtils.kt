package com.example.markdownreader.importing

import android.graphics.BitmapFactory

/**
 * 抽取器侧的图片占位生成工具。
 *
 * 之所以走 HTML `<img>` 而非 Markdown `![]()`：Markwon 的 `HtmlPlugin` 解析到 `<img>` 的
 * width/height 属性后，会把这两个值写入 `AsyncDrawable` 的初始 bounds——TextView 第一次
 * measure 时就预留了图片占位的高度，后续异步加载完成只是 invalidate()，**不再触发
 * relayout**。这是消除「滚动时被图片陆续加载触发的连环 measure 卡顿」的根因优化。
 *
 * Markdown `![](url)` 则只在加载完成后才知道实际尺寸，会引发一次 `requestLayout()` 改变
 * TextView 高度，进而导致整段重新 measure（几百毫秒级，正是 ANR Warning 的来源）。
 */
internal object ImageAssetUtils {

    /** decodeBounds 只读 header，几乎不占内存。失败返回 null。 */
    fun decodeImageSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w > 0 && h > 0) w to h else null
        }.getOrNull()
    }

    /**
     * 构造一个带尺寸预声明的 `<img>` 标签字符串。
     *
     * - [src] 通常是 `book-asset://<id>` 占位，落盘 read 时再被 [ParsedBookStorage] 替换成
     *   `file://` 绝对路径。
     * - [width]/[height] 为 null 时退化为不带尺寸的 `<img>`；Markwon 仍能渲染，只是回到
     *   「加载完再调整 layout」的旧行为。
     */
    fun buildImgTag(
        src: String,
        alt: String? = null,
        width: Int? = null,
        height: Int? = null,
        style: String? = null,
    ): String {
        val sb = StringBuilder(96)
        sb.append("<img src=\"").append(escapeAttr(src)).append("\"")
        if (!alt.isNullOrBlank()) {
            sb.append(" alt=\"").append(escapeAttr(alt.take(120))).append("\"")
        }
        if (width != null && width > 0) sb.append(" width=\"").append(width).append("\"")
        if (height != null && height > 0) sb.append(" height=\"").append(height).append("\"")
        if (!style.isNullOrBlank()) {
            sb.append(" style=\"").append(escapeAttr(style)).append("\"")
        }
        sb.append("/>")
        return sb.toString()
    }

    /** 同上，但用 byte[] 自动 decode 尺寸；失败时回退为不带 width/height 的 `<img>`。 */
    fun buildImgTagFromBytes(src: String, alt: String?, bytes: ByteArray): String {
        val (w, h) = decodeImageSize(bytes) ?: (null to null)
        return buildImgTag(src, alt, w, h)
    }

    private fun escapeAttr(s: String): String =
        s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
