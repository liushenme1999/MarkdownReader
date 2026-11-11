package space.liushenme.markdownreader.markdown

/**
 * Colors used by the lightweight code highlighter and the code block chrome.
 *
 * All values are Android ARGB ints so the same palette can be consumed by Markwon,
 * Spannable spans and Compose previews without conversion ambiguity.
 */
data class CodeTokenPalette(
    val background: Int,
    val foreground: Int,
    val selection: Int,
    val border: Int,
    val headerBackground: Int,
    val chrome: Int,
    val divider: Int,
    val scrollTrack: Int,
    val scrollThumb: Int,
    val comment: Int,
    val string: Int,
    val keyword: Int,
    val number: Int,
    val function: Int,
    val type: Int,
    val tag: Int,
    val attribute: Int,
    val variable: Int,
    val operator: Int,
)

enum class CodeHighlightTheme(
    val stableId: String,
    val displayName: String,
    val isDark: Boolean,
    val palette: CodeTokenPalette,
) {
    VS_LIGHT_PLUS(
        stableId = "VS_LIGHT_PLUS",
        displayName = "VS Light+",
        isDark = false,
        palette = CodeTokenPalette(
            background = 0xFFFFFFFF.toInt(),
            foreground = 0xFF000000.toInt(),
            selection = 0x663B82F6,
            border = 0xFFD0D7DE.toInt(),
            headerBackground = 0xFFF3F3F3.toInt(),
            chrome = 0xFF57606A.toInt(),
            divider = 0xFFD0D7DE.toInt(),
            scrollTrack = 0x33D0D7DE,
            scrollThumb = 0xFF8C959F.toInt(),
            comment = 0xFF008000.toInt(),
            string = 0xFFA31515.toInt(),
            keyword = 0xFF0000FF.toInt(),
            number = 0xFF098658.toInt(),
            function = 0xFF795E26.toInt(),
            type = 0xFF267F99.toInt(),
            tag = 0xFF800000.toInt(),
            attribute = 0xFF001080.toInt(),
            variable = 0xFF001080.toInt(),
            operator = 0xFF000000.toInt(),
        ),
    ),
    VS_DARK_PLUS(
        stableId = "VS_DARK_PLUS",
        displayName = "VS Dark+",
        isDark = true,
        palette = CodeTokenPalette(
            background = 0xFF1E1E1E.toInt(),
            foreground = 0xFFD4D4D4.toInt(),
            selection = 0x665A9BD6,
            border = 0xFF3F3F46.toInt(),
            headerBackground = 0xFF252526.toInt(),
            chrome = 0xFFC5C5C5.toInt(),
            divider = 0xFF3F3F46.toInt(),
            scrollTrack = 0x336A6A6A,
            scrollThumb = 0xFF797979.toInt(),
            comment = 0xFF6A9955.toInt(),
            string = 0xFFCE9178.toInt(),
            keyword = 0xFFC586C0.toInt(),
            number = 0xFFB5CEA8.toInt(),
            function = 0xFFDCDCAA.toInt(),
            type = 0xFF4EC9B0.toInt(),
            tag = 0xFF569CD6.toInt(),
            attribute = 0xFF9CDCFE.toInt(),
            variable = 0xFF9CDCFE.toInt(),
            operator = 0xFFD4D4D4.toInt(),
        ),
    ),
    MONOKAI(
        stableId = "MONOKAI",
        displayName = "Monokai",
        isDark = true,
        palette = CodeTokenPalette(
            background = 0xFF272822.toInt(),
            foreground = 0xFFF8F8F2.toInt(),
            selection = 0x66F8F8F2,
            border = 0xFF49483E.toInt(),
            headerBackground = 0xFF1F201B.toInt(),
            chrome = 0xFFA6A69B.toInt(),
            divider = 0xFF49483E.toInt(),
            scrollTrack = 0x33494643,
            scrollThumb = 0xFF75715E.toInt(),
            comment = 0xFF75715E.toInt(),
            string = 0xFFE6DB74.toInt(),
            keyword = 0xFFF92672.toInt(),
            number = 0xFFAE81FF.toInt(),
            function = 0xFFA6E22E.toInt(),
            type = 0xFF66D9EF.toInt(),
            tag = 0xFFF92672.toInt(),
            attribute = 0xFFA6E22E.toInt(),
            variable = 0xFFA6E22E.toInt(),
            operator = 0xFFF92672.toInt(),
        ),
    ),
    GITHUB_LIGHT(
        stableId = "GITHUB_LIGHT",
        displayName = "GitHub Light",
        isDark = false,
        palette = CodeTokenPalette(
            background = 0xFFFFFFFF.toInt(),
            foreground = 0xFF24292F.toInt(),
            selection = 0x6654AEFF,
            border = 0xFFD0D7DE.toInt(),
            headerBackground = 0xFFF6F8FA.toInt(),
            chrome = 0xFF57606A.toInt(),
            divider = 0xFFD0D7DE.toInt(),
            scrollTrack = 0x33D0D7DE,
            scrollThumb = 0xFF8C959F.toInt(),
            comment = 0xFF6E7781.toInt(),
            string = 0xFF0A3069.toInt(),
            keyword = 0xFFCF222E.toInt(),
            number = 0xFF0550AE.toInt(),
            function = 0xFF8250DF.toInt(),
            type = 0xFF953800.toInt(),
            tag = 0xFF116329.toInt(),
            attribute = 0xFF0550AE.toInt(),
            variable = 0xFF0550AE.toInt(),
            operator = 0xFF24292F.toInt(),
        ),
    );

    companion object {
        val DEFAULT: CodeHighlightTheme = VS_LIGHT_PLUS
        val all: List<CodeHighlightTheme> = entries

        fun fromStored(raw: String?): CodeHighlightTheme =
            entries.firstOrNull { it.stableId == raw?.trim() } ?: DEFAULT
    }
}
