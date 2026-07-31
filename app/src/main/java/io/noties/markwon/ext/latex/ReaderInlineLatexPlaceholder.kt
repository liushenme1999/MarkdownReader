package io.noties.markwon.ext.latex

/**
 * 行内公式占位文本。
 *
 * 必须用单行源码（与 Markwon `prepareLatexTextPlaceholder` 相同：去掉换行），
 * 不能用多行 raw latex，否则 ReplacementSpan 会在每一行各画一遍公式。
 *
 * 也不要用单独的 `\uFFFC`：长按选中后复制到剪贴板是「空」，划线也会因无有效文本而无响应。
 * 底层保留 `$…$`，由 [ReplacementSpan] 负责绘制公式外观。
 */
internal fun prepareInlineLatexPlaceholder(latex: String): String {
    val body = latex.replace('\n', ' ').trim()
    if (body.isEmpty()) return "\uFFFC"
    if (body.length >= 2 && body.startsWith('$') && body.endsWith('$')) return body
    return "\$${body}\$"
}
