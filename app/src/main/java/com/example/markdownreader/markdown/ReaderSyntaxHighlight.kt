package com.example.markdownreader.markdown

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import io.noties.markwon.syntax.SyntaxHighlight

/** 围栏代码块关键字着色（对接 Markwon [SyntaxHighlight] API）。 */
internal class ReaderSyntaxHighlight : SyntaxHighlight {

    override fun highlight(info: String?, code: String): CharSequence {
        val lang = info?.trim()?.substringBefore(' ')?.lowercase().orEmpty()
        val rules = rulesFor(lang) ?: return code
        val spannable = SpannableString(code)
        rules.forEach { rule ->
            rule.pattern.findAll(code).forEach { m ->
                val s = m.range.first
                val e = m.range.last + 1
                spannable.setSpan(ForegroundColorSpan(rule.color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (rule.bold) {
                    spannable.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return spannable
    }

    private data class Rule(val pattern: Regex, val color: Int, val bold: Boolean = false)

    private fun rulesFor(lang: String): List<Rule>? = when (lang) {
        "javascript", "js", "typescript", "ts" -> listOf(
            Rule(Regex("""\b(function|let|const|var|return|if|else|for|while|new)\b"""), Color.parseColor("#D32F2F"), true),
            Rule(Regex("""\b(true|false|null|undefined)\b"""), Color.parseColor("#7B1FA2")),
            Rule(Regex("""\b\d+\b"""), Color.parseColor("#388E3C")),
        )
        "python", "py" -> listOf(
            Rule(Regex("""\b(def|return|if|else|elif|for|while|import|print)\b"""), Color.parseColor("#D32F2F"), true),
            Rule(Regex("""\b(True|False|None)\b"""), Color.parseColor("#7B1FA2")),
            Rule(Regex("""\b\d+\b"""), Color.parseColor("#388E3C")),
        )
        "sql" -> listOf(
            Rule(Regex("""\b(?i)(select|from|where|order|by|insert|update|delete|join|on|group|having|limit)\b"""), Color.parseColor("#1565C0"), true),
            Rule(Regex("""\b\d+\b"""), Color.parseColor("#388E3C")),
        )
        "kotlin", "kt", "java" -> listOf(
            Rule(Regex("""\b(fun|val|var|return|if|else|class|object|import|package|public|private)\b"""), Color.parseColor("#D32F2F"), true),
            Rule(Regex("""\b(true|false|null)\b"""), Color.parseColor("#7B1FA2")),
        )
        "json" -> listOf(
            Rule(Regex(""""[^"]*""""), Color.parseColor("#388E3C")),
            Rule(Regex("""\b(true|false|null)\b"""), Color.parseColor("#7B1FA2")),
        )
        else -> null
    }
}
