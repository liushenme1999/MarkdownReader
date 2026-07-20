package io.noties.markwon.ext.latex

/** 行内公式占位文本：用对象替换符，避免依赖系统字体是否含 ⊗ 等数学符号。 */
internal fun prepareInlineLatexPlaceholder(latex: String): String =
    "\uFFFC"
