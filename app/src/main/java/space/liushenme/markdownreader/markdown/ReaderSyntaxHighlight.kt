package space.liushenme.markdownreader.markdown

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import io.noties.markwon.syntax.SyntaxHighlight

/**
 * Lightweight regex highlighter for fenced/indented blocks.
 *
 * Comments and strings are claimed before every other token. This keeps keywords,
 * numbers and operators from painting over the contents of literals or comments.
 * The highlighter only changes foreground colors; typography remains owned by Markwon.
 */
internal class ReaderSyntaxHighlight(
    val highlightTheme: CodeHighlightTheme = CodeHighlightTheme.DEFAULT,
) : SyntaxHighlight {

    override fun highlight(info: String?, code: String): CharSequence {
        val spec = specFor(normalizeLanguage(info)) ?: return code
        val spannable = SpannableString(code)
        if (code.isEmpty()) return spannable

        val occupied = BooleanArray(code.length)
        applyProtectedTokens(code, spannable, occupied, spec)
        applyRules(code, spannable, occupied, spec.rules)
        return spannable
    }

    private fun applyProtectedTokens(
        code: String,
        spannable: SpannableString,
        occupied: BooleanArray,
        spec: LanguageSpec,
    ) {
        val candidates = buildList {
            spec.protectedRules.forEachIndexed { ruleIndex, rule ->
                rule.pattern.findAll(code).forEach { match ->
                    if (match.value.isEmpty()) return@forEach
                    add(
                        ProtectedCandidate(
                            start = match.range.first,
                            end = match.range.last + 1,
                            kind = rule.kind,
                            ruleIndex = ruleIndex,
                        ),
                    )
                }
            }
        }.sortedWith(compareBy<ProtectedCandidate> { it.start }.thenBy { it.ruleIndex })

        for (candidate in candidates) {
            if (!occupied.isFree(candidate.start, candidate.end)) continue
            color(spannable, occupied, candidate.start, candidate.end, candidate.kind)
        }
    }

    private fun applyRules(
        code: String,
        spannable: SpannableString,
        occupied: BooleanArray,
        rules: List<TokenRule>,
    ) {
        rules.forEach { rule ->
            rule.pattern.findAll(code).forEach { match ->
                val range = if (rule.group == 0) match.range else match.groups[rule.group]?.range
                    ?: return@forEach
                if (range.isEmpty()) return@forEach
                val start = range.first
                val end = range.last + 1
                if (!occupied.isFree(start, end)) return@forEach
                color(spannable, occupied, start, end, rule.kind)
            }
        }
    }

    private fun color(
        spannable: SpannableString,
        occupied: BooleanArray,
        start: Int,
        end: Int,
        kind: TokenKind,
    ) {
        if (!occupied.isFree(start, end)) return
        spannable.setSpan(
            ForegroundColorSpan(colorFor(kind)),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        occupied.fill(true, start, end)
    }

    private fun colorFor(kind: TokenKind): Int {
        val palette = highlightTheme.palette
        return when (kind) {
            TokenKind.COMMENT -> palette.comment
            TokenKind.STRING -> palette.string
            TokenKind.KEYWORD -> palette.keyword
            TokenKind.NUMBER -> palette.number
            TokenKind.FUNCTION -> palette.function
            TokenKind.TYPE -> palette.type
            TokenKind.TAG -> palette.tag
            TokenKind.ATTRIBUTE -> palette.attribute
            TokenKind.VARIABLE -> palette.variable
            TokenKind.OPERATOR -> palette.operator
            TokenKind.FOREGROUND -> palette.foreground
        }
    }

    private fun BooleanArray.isFree(start: Int, end: Int): Boolean {
        if (start < 0 || end <= start || end > size) return false
        for (index in start until end) {
            if (this[index]) return false
        }
        return true
    }

    private fun normalizeLanguage(info: String?): String {
        return info
            ?.trim()
            ?.substringBefore(' ')
            ?.substringBefore('{')
            ?.trim()
            ?.lowercase()
            .orEmpty()
    }

    private fun specFor(language: String): LanguageSpec? = when (language) {
        "py", "pyw", "python" -> pythonSpec()
        "rb", "ruby" -> rubySpec()
        "java", "kt", "kts", "kotlin" -> jvmSpec()
        "c", "h", "cpp", "c++", "cc", "cxx", "hpp" -> cFamilySpec()
        "cs", "csharp" -> cFamilySpec()
        "go", "golang" -> goSpec()
        "rs", "rust" -> rustSpec()
        "swift" -> swiftSpec()
        "php" -> phpSpec()
        "js", "javascript", "jsx", "mjs", "cjs" -> javaScriptSpec()
        "ts", "typescript", "tsx" -> javaScriptSpec()
        "vue" -> vueSpec()
        "html", "htm", "xml" -> markupSpec()
        "css", "scss", "less" -> cssSpec()
        "json" -> jsonSpec()
        "yaml", "yml" -> yamlSpec()
        "toml" -> tomlSpec()
        "ini", "properties" -> iniSpec()
        "sh", "shell", "bash", "zsh" -> shellSpec()
        "ps1", "powershell" -> powerShellSpec()
        "sql" -> sqlSpec()
        else -> null
    }

    private fun pythonSpec(): LanguageSpec {
        val keywords = keywordRule(
            "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
            "def", "del", "elif", "else", "except", "False", "finally", "for", "from",
            "global", "if", "import", "in", "is", "lambda", "match", "None", "nonlocal",
            "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield",
        )
        return LanguageSpec(
            protectedRules = listOf(
                protected(
                    "(?s)'''(?:\\\\.|(?!''').)*'''|\"\"\"(?:\\\\.|(?!\"\"\").)*\"\"\"" +
                        "|'(?:\\\\.|[^'\\\\\\n])*'|\"(?:\\\\.|[^\"\\\\\\n])*\"",
                    TokenKind.STRING,
                ),
                protected("""#[^\n]*""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywords,
                typeRule(
                    "Any", "bool", "bytes", "complex", "dict", "float", "frozenset", "int",
                    "list", "object", "set", "str", "tuple", "type", "Exception", "ValueError",
                    "TypeError", "KeyError", "RuntimeError", "BaseException",
                ),
                functionRule(
                    """(?m)(?<=def\s)[A-Za-z_][A-Za-z0-9_]*(?=\s*\()|(?<![\w.])[A-Za-z_][A-Za-z0-9_]*(?=\s*\()""",
                ),
                numberRule(),
                variableRule("""(?<![\w])(?:self|cls)\b"""),
                operatorRule(),
            ),
        )
    }

    private fun rubySpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(""""(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""", TokenKind.STRING),
                protected("""#[^\n]*""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "alias", "and", "begin", "break", "case", "class", "def", "defined",
                    "do", "else", "elsif", "end", "ensure", "false", "for", "if", "in",
                    "module", "next", "nil", "not", "or", "redo", "rescue", "retry",
                    "return", "self", "super", "then", "true", "undef", "unless", "until",
                    "when", "while", "yield",
                ),
                typeRule("Array", "Hash", "Integer", "Float", "String", "Symbol", "Regexp", "Proc"),
                functionRule("""(?m)(?<=def\s)[A-Za-z_][A-Za-z0-9_!?=]*(?=\s*[(\n])|\b[A-Za-z_][A-Za-z0-9_!?]*(?=\s*\()"""),
                numberRule(),
                variableRule("""(?<![\w])(?:@@?[A-Za-z_][A-Za-z0-9_]*|\$[A-Za-z_][A-Za-z0-9_]*)\b"""),
                operatorRule(),
            ),
        )
    }

    private fun jvmSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(
                    "(?s)\"\"\"(?:\\\\.|(?!\"\"\").)*\"\"\"" +
                        "|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'",
                    TokenKind.STRING,
                ),
                protected("""//[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
                    "companion", "const", "constructor", "continue", "crossinline", "data",
                    "default", "delegate", "do", "dynamic", "else", "enum", "expect", "extends",
                    "external", "false", "field", "final", "finally", "for", "fun", "get",
                    "if", "implements", "import", "in", "infix", "init", "inline", "inner",
                    "instanceof", "interface", "internal", "is", "lateinit", "native", "new",
                    "noinline", "null", "object", "open", "operator", "out", "override",
                    "package", "private", "protected", "public", "reified", "return", "sealed",
                    "set", "static", "strictfp", "super", "suspend", "switch", "synchronized",
                    "this", "throw", "throws", "transient", "true", "try", "typealias", "val",
                    "var", "vararg", "volatile", "when", "while",
                ),
                typeRule(
                    "Any", "Array", "Boolean", "Byte", "Char", "Collection", "Double",
                    "Exception", "Float", "Int", "Integer", "Iterable", "List", "Long", "Map",
                    "Nothing", "Number", "Object", "Pair", "Sequence", "Set", "Short", "String",
                    "Throwable", "Triple", "Unit", "Void", "boolean", "byte", "char", "double",
                    "float", "int", "long", "short",
                ),
                functionRule("""\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"""),
                numberRule(),
                operatorRule(),
            ),
        )
    }

    private fun cFamilySpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(
                    "(?s)\"\"\"(?:\\\\.|(?!\"\"\").)*\"\"\"" +
                        "|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'",
                    TokenKind.STRING,
                ),
                protected("""//[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "alignas", "alignof", "asm", "auto", "break", "case", "catch", "class",
                    "const", "constexpr", "continue", "default", "delete", "do", "else",
                    "enum", "explicit", "extern", "false", "final", "for", "friend", "goto",
                    "if", "inline", "mutable", "namespace", "new", "noexcept", "nullptr",
                    "operator", "override", "private", "protected", "public", "register",
                    "return", "sizeof", "static", "static_assert", "struct", "switch",
                    "template", "this", "throw", "true", "try", "typedef", "typename",
                    "union", "using", "virtual", "volatile", "while",
                ),
                typeRule(
                    "bool", "char", "char16_t", "char32_t", "double", "float", "int", "long",
                    "short", "signed", "size_t", "string", "unsigned", "void", "wchar_t",
                    "std", "vector", "map", "set", "unique_ptr", "shared_ptr", "optional",
                ),
                functionRule("""\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"""),
                numberRule(),
                operatorRule(),
            ),
        )
    }

    private fun goSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected("""`[^`]*`|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""", TokenKind.STRING),
                protected("""//[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "break", "case", "chan", "const", "continue", "default", "defer", "else",
                    "fallthrough", "false", "for", "func", "go", "goto", "if", "import",
                    "interface", "map", "nil", "package", "range", "return", "select",
                    "struct", "switch", "true", "type", "var",
                ),
                typeRule(
                    "bool", "byte", "complex64", "complex128", "error", "float32", "float64",
                    "int", "int8", "int16", "int32", "int64", "rune", "string", "uint",
                    "uint8", "uint16", "uint32", "uint64", "uintptr", "any",
                ),
                functionRule("""(?m)(?<=func\s)[A-Za-z_][A-Za-z0-9_]*(?=\s*\()|\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"""),
                numberRule(),
                operatorRule(),
            ),
        )
    }

    private fun rustSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(
                    """r#*"(?s:.*?)"#*|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""",
                    TokenKind.STRING,
                ),
                protected("""//[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "as", "async", "await", "break", "const", "continue", "crate", "dyn",
                    "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
                    "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
                    "self", "Self", "static", "struct", "super", "trait", "true", "type",
                    "unsafe", "use", "where", "while", "yield",
                ),
                typeRule(
                    "bool", "char", "f32", "f64", "i8", "i16", "i32", "i64", "i128",
                    "isize", "str", "String", "u8", "u16", "u32", "u64", "u128", "usize",
                    "Option", "Result", "Vec", "Box", "HashMap", "HashSet", "BTreeMap",
                ),
                functionRule("""(?m)(?<=fn\s)[A-Za-z_][A-Za-z0-9_]*(?=\s*\()|\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"""),
                numberRule(),
                variableRule("""(?<![\w])[A-Za-z_][A-Za-z0-9_]*(?=\s*!)"""),
                operatorRule(),
            ),
        )
    }

    private fun swiftSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(
                    "(?s)\"\"\"(?:\\\\.|(?!\"\"\").)*\"\"\"" +
                        "|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'",
                    TokenKind.STRING,
                ),
                protected("""//[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "associatedtype", "break", "case", "catch", "class", "continue",
                    "default", "defer", "deinit", "do", "else", "enum", "extension", "fallthrough",
                    "false", "fileprivate", "for", "func", "guard", "if", "import", "in",
                    "indirect", "infix", "init", "inout", "internal", "is", "let", "nil",
                    "open", "operator", "override", "param", "postfix", "precedencegroup",
                    "prefix", "private", "protocol", "public", "repeat", "required", "rethrows",
                    "return", "self", "Self", "static", "struct", "subscript", "super", "switch",
                    "throw", "throws", "true", "try", "typealias", "var", "where", "while",
                ),
                typeRule(
                    "Any", "AnyObject", "Array", "Bool", "Character", "Dictionary", "Double",
                    "Error", "Float", "Int", "Optional", "Result", "Set", "String", "UInt",
                    "Void",
                ),
                functionRule("""(?m)(?<=func\s)[A-Za-z_][A-Za-z0-9_]*(?=\s*\()|\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"""),
                numberRule(),
                operatorRule(),
            ),
        )
    }

    private fun phpSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(""""(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""", TokenKind.STRING),
                protected("""//[^\n]*|#[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "abstract", "and", "array", "as", "break", "callable", "case", "catch",
                    "class", "clone", "const", "continue", "declare", "default", "do", "echo",
                    "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
                    "endswitch", "endwhile", "extends", "final", "finally", "fn", "for",
                    "foreach", "function", "global", "goto", "if", "implements", "include",
                    "include_once", "instanceof", "insteadof", "interface", "isset", "list",
                    "match", "namespace", "new", "null", "or", "print", "private", "protected",
                    "public", "readonly", "require", "require_once", "return", "static",
                    "switch", "throw", "trait", "true", "false", "try", "unset", "use", "var",
                    "while", "xor", "yield",
                ),
                typeRule("bool", "boolean", "float", "int", "integer", "iterable", "mixed", "object", "string", "void"),
                functionRule("""(?m)(?<=function\s)[A-Za-z_][A-Za-z0-9_]*(?=\s*\()|\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"""),
                numberRule(),
                variableRule("""\$[A-Za-z_][A-Za-z0-9_]*"""),
                operatorRule(),
            ),
        )
    }

    private fun javaScriptSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(
                    """`(?:\\.|[^`\\])*`|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""",
                    TokenKind.STRING,
                ),
                protected("""//[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "as", "async", "await", "break", "case", "catch", "class", "const",
                    "continue", "debugger", "default", "delete", "do", "else", "export",
                    "extends", "false", "finally", "for", "from", "function", "get", "if",
                    "implements", "import", "in", "instanceof", "interface", "let", "new",
                    "null", "of", "package", "private", "protected", "public", "return",
                    "set", "static", "super", "switch", "this", "throw", "true", "try",
                    "typeof", "undefined", "var", "void", "while", "with", "yield",
                ),
                typeRule(
                    "Array", "BigInt", "Boolean", "Date", "Error", "Function", "Map", "Number",
                    "Object", "Promise", "Record", "RegExp", "Set", "String", "Symbol", "WeakMap",
                    "WeakSet", "any", "boolean", "never", "number", "string", "unknown",
                ),
                functionRule("""(?m)(?<=function\s)[A-Za-z_$][A-Za-z0-9_$]*(?=\s*\()|\b[A-Za-z_$][A-Za-z0-9_$]*(?=\s*\()"""),
                numberRule(),
                operatorRule(),
            ),
        )
    }

    private fun vueSpec(): LanguageSpec {
        val markup = markupSpec()
        val script = javaScriptSpec()
        return LanguageSpec(
            protectedRules = markup.protectedRules + script.protectedRules,
            rules = markup.rules + script.rules,
        )
    }

    private fun markupSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(""""(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""", TokenKind.STRING),
                protected("""<!--(?s).*?-->|<!\[CDATA\[(?s).*?\]\]>""", TokenKind.COMMENT),
            ),
            rules = listOf(
                attributeRule("""(?<=\s)[A-Za-z_:][-A-Za-z0-9_:.]*(?=\s*=)"""),
                tagRule("""</?[A-Za-z][A-Za-z0-9:_-]*|/?>|<!DOCTYPE\b"""),
                keywordRule("doctype", "html", "head", "body", "script", "style", "template"),
                typeRule("template", "slot", "component", "transition"),
                numberRule(),
                operatorRule(),
            ),
        )
    }

    private fun cssSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(""""(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""", TokenKind.STRING),
                protected("""(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                tagRule("""(?:#(?![0-9A-Fa-f]{3,8}\b)|\.)[A-Za-z_-][A-Za-z0-9_-]*|&[A-Za-z0-9_-]*"""),
                attributeRule("""(?m)(?:^|(?<=[;{]))\s*[-A-Za-z][A-Za-z0-9_-]*(?=\s*:)"""),
                attributeRule("""@[A-Za-z-]+"""),
                keywordRule("important", "media", "supports", "import", "keyframes", "font-face"),
                functionRule("""\b[A-Za-z_-][A-Za-z0-9_-]*(?=\s*\()"""),
                numberRule("""#(?:[0-9A-Fa-f]{3,8})\b|\b\d+(?:\.\d+)?(?:%|px|em|rem|vh|vw|vmin|vmax|s|ms|deg|fr)?\b"""),
                variableRule("""--[A-Za-z_-][A-Za-z0-9_-]*"""),
                operatorRule(""":|::|[>+~*=|^$]"""),
            ),
        )
    }

    private fun jsonSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(""""(?:\\.|[^"\\])*"(?=\s*:)""", TokenKind.ATTRIBUTE),
                protected(""""(?:\\.|[^"\\])*"""", TokenKind.STRING),
            ),
            rules = listOf(
                keywordRule("true", "false", "null"),
                numberRule("""-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b"""),
                operatorRule(""":|,|\[|\]|\{|\}"""),
            ),
        )
    }

    private fun yamlSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected("""(?m)^[ \t-]*[A-Za-z_][A-Za-z0-9_.-]*(?=\s*:)""", TokenKind.ATTRIBUTE),
                protected(""""(?:\\.|[^"\\\n])*"|'(?:''|[^'\n])*'""", TokenKind.STRING),
                protected("""#[^\n]*""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule("true", "false", "null", "yes", "no", "on", "off"),
                numberRule("""-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b"""),
                variableRule("""&[A-Za-z_][A-Za-z0-9_-]*|\*[A-Za-z_][A-Za-z0-9_-]*"""),
                operatorRule(""":|-|\[|\]|\{|\}|,|\||>"""),
            ),
        )
    }

    private fun tomlSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected("""(?m)^\s*[A-Za-z0-9_.-]+(?=\s*=)""", TokenKind.ATTRIBUTE),
                protected(
                    "(?s)'''(?:\\\\.|(?!''').)*'''|\"\"\"(?:\\\\.|(?!\"\"\").)*\"\"\"" +
                        "|'(?:\\\\.|[^'\\\\\\n])*'|\"(?:\\\\.|[^\"\\\\\\n])*\"",
                    TokenKind.STRING,
                ),
                protected("""#[^\n]*""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule("true", "false"),
                numberRule("""\b\d{4}-\d{2}-\d{2}(?:[Tt ][0-9:.+-]+)?\b|\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b"""),
                operatorRule("""=|\[|\]|\{|\}|,|\."""),
            ),
        )
    }

    private fun iniSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected("""(?m)^\s*[A-Za-z0-9_.-]+(?=\s*[=:])""", TokenKind.ATTRIBUTE),
                protected(""""(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""", TokenKind.STRING),
                protected("""(?m)[#;][^\n]*""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule("true", "false", "yes", "no", "on", "off", "null"),
                numberRule("""-?\b\d+(?:\.\d+)?\b"""),
                variableRule("""\$\{[A-Za-z_][A-Za-z0-9_.-]*\}"""),
                operatorRule("""[=:]"""),
            ),
        )
    }

    private fun shellSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(""""(?:\\.|[^"\\])*"|'[^']*'""", TokenKind.STRING),
                protected("""(?:^|\s)#[^\n]*""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "case", "do", "done", "elif", "else", "esac", "fi", "for", "function",
                    "if", "in", "select", "then", "time", "until", "while",
                ),
                functionRule("""\b[A-Za-z_][A-Za-z0-9_-]*(?=\s*\(\))"""),
                variableRule("""\$\{?[A-Za-z_][A-Za-z0-9_]*\}?|\$[0-9@#?*!$-]"""),
                numberRule(),
                operatorRule("""&&|\|\||;;|==|!=|[|&;<>]"""),
            ),
        )
    }

    private fun powerShellSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected(""""(?:\\.|[^"\\])*"|'[^']*'""", TokenKind.STRING),
                protected("""<#(?s).*?#>|#[^\n]*""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRule(
                    "begin", "break", "catch", "class", "continue", "data", "define", "do",
                    "dynamicparam", "else", "elseif", "end", "enum", "exit", "filter",
                    "finally", "for", "foreach", "from", "function", "hidden", "if", "in",
                    "param", "process", "return", "static", "switch", "throw", "trap", "try",
                    "until", "using", "var", "while", "workflow",
                ),
                typeRule(
                    "bool", "byte", "char", "datetime", "decimal", "double", "float", "int",
                    "long", "object", "string", "void",
                ),
                functionRule("""(?m)(?<=function\s)[A-Za-z_][A-Za-z0-9_-]*|\b[A-Z][A-Za-z0-9]*-[A-Z][A-Za-z0-9]*\b|\b[A-Za-z_][A-Za-z0-9_-]*(?=\s*\()"""),
                variableRule("""\$[A-Za-z_][A-Za-z0-9_:]*"""),
                numberRule(),
                operatorRule("""-eq|-ne|-gt|-ge|-lt|-le|-and|-or|-not|-like|-match|&&|\|\||[+\-*/%!=<>]"""),
            ),
        )
    }

    private fun sqlSpec(): LanguageSpec {
        return LanguageSpec(
            protectedRules = listOf(
                protected("""'(?:''|[^'])*'""", TokenKind.STRING),
                protected("""--[^\n]*|(?s)/\*.*?\*/""", TokenKind.COMMENT),
            ),
            rules = listOf(
                keywordRuleIgnoreCase(
                    "all", "alter", "and", "as", "asc", "begin", "between", "by", "case",
                    "commit", "constraint", "create", "cross", "database", "default", "delete",
                    "desc", "distinct", "drop", "else", "end", "exists", "false", "foreign",
                    "from", "full", "group", "having", "if", "in", "index", "inner", "insert",
                    "into", "is", "join", "key", "left", "like", "limit", "not", "null",
                    "offset", "on", "or", "order", "outer", "primary", "references", "right",
                    "rollback", "select", "set", "table", "then", "true", "union", "unique",
                    "update", "values", "view", "when", "where", "with",
                ),
                typeRuleIgnoreCase(
                    "bigint", "binary", "bit", "blob", "boolean", "char", "date", "datetime",
                    "decimal", "double", "float", "int", "integer", "json", "numeric", "real",
                    "smallint", "text", "time", "timestamp", "tinyint", "varchar",
                ),
                functionRule(
                    """\b(?:count|sum|avg|min|max|coalesce|cast|convert|length|lower|upper|substr|substring|round|now|date|year|month|day)\b(?=\s*\()""",
                    ignoreCase = true,
                ),
                numberRule(),
                operatorRule("""<>|!=|<=|>=|=|<|>|\+|-|\*|/|%|\|\||::"""),
            ),
        )
    }

    private fun keywordRule(vararg words: String): TokenRule =
        wordRule(TokenKind.KEYWORD, words = words)

    private fun keywordRuleIgnoreCase(vararg words: String): TokenRule =
        wordRule(TokenKind.KEYWORD, ignoreCase = true, words = words)

    private fun typeRule(vararg words: String): TokenRule =
        wordRule(TokenKind.TYPE, words = words)

    private fun typeRuleIgnoreCase(vararg words: String): TokenRule =
        wordRule(TokenKind.TYPE, ignoreCase = true, words = words)

    private fun wordRule(
        kind: TokenKind,
        ignoreCase: Boolean = false,
        vararg words: String,
    ): TokenRule {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return TokenRule(
            Regex("""\b(?:${words.joinToString("|") { Regex.escape(it) }})\b""", options),
            kind,
        )
    }

    private fun functionRule(pattern: String, ignoreCase: Boolean = false): TokenRule {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return TokenRule(Regex(pattern, options), TokenKind.FUNCTION)
    }

    private fun variableRule(pattern: String): TokenRule =
        TokenRule(Regex(pattern), TokenKind.VARIABLE)

    private fun attributeRule(pattern: String, group: Int = 0): TokenRule =
        TokenRule(Regex(pattern), TokenKind.ATTRIBUTE, group)

    private fun tagRule(pattern: String): TokenRule =
        TokenRule(Regex(pattern), TokenKind.TAG)

    private fun numberRule(
        pattern: String =
            """\b(?:0[xX][0-9A-Fa-f_]+|0[bB][01_]+|0[oO][0-7_]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?)[fFdDlLuU]*\b""",
    ): TokenRule = TokenRule(Regex(pattern), TokenKind.NUMBER)

    private fun operatorRule(
        pattern: String =
            """==|!=|<=|>=|=>|->|::|\+\+|--|&&|\|\||\+=|-=|\*=|/=|%=|&=|\|=|\^=|\?\.|\?:|[+\-*/%=!<>&|^~?:]""",
    ): TokenRule = TokenRule(Regex(pattern), TokenKind.OPERATOR)

    private fun protected(pattern: String, kind: TokenKind): ProtectedRule =
        ProtectedRule(Regex(pattern), kind)

    private data class LanguageSpec(
        val protectedRules: List<ProtectedRule>,
        val rules: List<TokenRule>,
    )

    private data class ProtectedRule(
        val pattern: Regex,
        val kind: TokenKind,
    )

    private data class ProtectedCandidate(
        val start: Int,
        val end: Int,
        val kind: TokenKind,
        val ruleIndex: Int,
    )

    private data class TokenRule(
        val pattern: Regex,
        val kind: TokenKind,
        val group: Int = 0,
    )

    private enum class TokenKind {
        COMMENT,
        STRING,
        KEYWORD,
        NUMBER,
        FUNCTION,
        TYPE,
        TAG,
        ATTRIBUTE,
        VARIABLE,
        OPERATOR,
        FOREGROUND,
    }
}
