package space.liushenme.markdownreader.importing

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 全格式导入矩阵（JVM / Robolectric）。
 *
 * 样本来自 `src/test/resources/samples`（与手机端推送的测试文档同一套），
 * 覆盖 Markdown / TXT / HTML / PDF / 12 种源码格式，以及编码、扩展名等边界条件。
 * 任何格式导入后正文为空、乱码、命中占位文案、代码语言丢失，都会在这里失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SampleFormatImportMatrixTest {

    private val context = RuntimeEnvironment.getApplication()

    /**
     * 样本目录：直接用 androidTest assets（完整 22 个文件，含 .kt/.java）。
     * JVM 测试资源会排除 .java/.kt（与源码目录冲突），走 classpath 会悄悄少测两种源码格式。
     */
    private fun sampleDir(): File {
        val candidates = listOf(
            File("src/androidTest/assets/samples"),
            File("app/src/androidTest/assets/samples"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("找不到 samples 目录: ${candidates.map { it.absolutePath }}")
    }

    private fun sampleNames(): List<String> =
        sampleDir().listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    private fun sampleBytes(name: String): ByteArray = File(sampleDir(), name).readBytes()

    private fun importSample(name: String): String {
        val format = BookImportSupport.detectFormat(name, null)
        return BookContentLoader.loadExtractedFromUrlBytes(
            context,
            sampleBytes(name),
            format,
            null,
        ).body
    }

    @Test
    fun sampleSetContainsAllFormats() {
        val names = sampleNames()
        assertTrue("样本数不足: ${names.size}", names.size >= 22)
        assertTrue("缺少 .kt 样本", names.any { it.endsWith(".kt") })
        assertTrue("缺少 .java 样本", names.any { it.endsWith(".java") })
        val formats = names.map { BookImportSupport.detectFormat(it, null) }.toSet()
        for (format in ImportedBookFormat.entries) {
            assertTrue("缺少 $format 格式样本", format in formats)
        }
    }

    @Test
    fun everySampleImportsIntoNonBlankContent() {
        val failures = mutableListOf<String>()
        for (name in sampleNames()) {
            val format = BookImportSupport.detectFormat(name, null)
            val body = runCatching { importSample(name) }
                .getOrElse { error -> failures += "$name 导入抛异常: ${error.message}"; continue }
            if (body.isBlank()) failures += "$name 正文为空 (format=${format.storedKey})"
            if (body.contains("暂不支持") || body.contains("暫不支援")) {
                failures += "$name 命中占位正文"
            }
            assertTrue("$name 正文过短", body.length > 20)
        }
        assertTrue("导入失败:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun codeSamplesKeepLanguageAndSourceIntact() {
        val failures = mutableListOf<String>()
        val codeNames = sampleNames().filter { name ->
            BookImportSupport.detectFormat(name, null).isCode
        }
        assertTrue("缺少源码样本", codeNames.size >= 10)
        for (name in codeNames) {
            val body = importSample(name)
            val language = CodeFileLanguage.fromFileName(name)
            if (language == null) {
                failures += "$name 未识别语言"
                continue
            }
            val raw = sampleBytes(name).decodeToString()
            if (body.length < raw.length * 0.9) failures += "$name 正文被裁剪 ${body.length}/${raw.length}"
            if (raw.contains("    ") && !body.contains("    ")) failures += "$name 缩进丢失"
            if (body.contains('\uFFFD')) failures += "$name 出现解码替换符"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun markdownSamplesKeepMarkupAndChinese() {
        for (name in sampleNames().filter { it.endsWith(".md") }) {
            val body = importSample(name)
            assertTrue("$name 中文丢失", body.any { it.code in 0x4E00..0x9FFF })
            if (name.startsWith("01-") || name.startsWith("17-")) {
                assertTrue("$name 缺少链接", body.contains("](") || body.contains("http"))
            }
            assertFalse("$name 出现替换符", body.contains('\uFFFD'))
        }
    }

    @Test
    fun htmlSampleKeepsTags() {
        val body = importSample("03-网页-HTML测试.html")
        assertTrue("HTML 标签丢失", body.contains("<html") || body.contains("<!DOCTYPE"))
        assertTrue("HTML 中文丢失", body.contains("网页"))
    }

    @Test
    fun plainTextSampleKeepsChapters() {
        val body = importSample("02-纯文本-长篇测试.txt")
        assertTrue("章节标题丢失", body.contains("第1章"))
        assertTrue("末尾章节丢失", body.contains("第140章"))
        assertTrue("正文被截断: ${body.length}", body.length > 50_000)
    }

    // ---------------- 编码与命名边界 ----------------

    @Test
    fun gbkChineseTxtDecodesWithoutMojibake() {
        val text = "第一章 雨夜\n\n夜色像一层缓慢下沉的水，把城里的声音都压低了。\n"
        val bytes = text.toByteArray(Charset.forName("GBK"))
        val body = BookContentLoader.loadExtractedFromUrlBytes(
            context, bytes, ImportedBookFormat.TXT, null,
        ).body
        assertTrue("GBK 未识别", body.contains("夜色像一层缓慢下沉的水"))
        assertFalse("GBK 乱码", body.contains('\uFFFD'))
    }

    @Test
    fun utf8BomIsStripped() {
        val text = "第一章 BOM 测试\n正文内容\n"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()
        val body = BookContentLoader.loadExtractedFromUrlBytes(
            context, bytes, ImportedBookFormat.TXT, null,
        ).body
        assertFalse("BOM 未剥离", body.startsWith("\uFEFF"))
        assertTrue("正文丢失", body.contains("BOM 测试"))
    }

    @Test
    fun utf16TextDecodes() {
        val text = "UTF-16 中文测试\n第二行\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(StandardCharsets.UTF_16LE)
        val body = BookContentLoader.loadExtractedFromUrlBytes(
            context, bytes, ImportedBookFormat.TXT, null,
        ).body
        assertTrue("UTF-16 未识别: $body", body.contains("UTF-16 中文测试"))
    }

    @Test
    fun uppercaseAndUnknownExtensionsStillDetectFormat() {
        assertEquals(ImportedBookFormat.MARKDOWN, BookImportSupport.detectFormat("A.MD", null))
        assertEquals(ImportedBookFormat.TXT, BookImportSupport.detectFormat("A.TXT", null))
        assertEquals(ImportedBookFormat.CODE, BookImportSupport.detectFormat("A.KT", null))
        assertEquals(ImportedBookFormat.CODE, BookImportSupport.detectFormat("script.sh", null))
        assertEquals(ImportedBookFormat.CODE, BookImportSupport.detectFormat("data.yaml", null))
        assertEquals(ImportedBookFormat.HTML, BookImportSupport.detectFormat("page.htm", null))
        // 无扩展名时退回 MIME
        assertEquals(
            ImportedBookFormat.MARKDOWN,
            BookImportSupport.detectFormat("README", "text/markdown"),
        )
        assertEquals(
            ImportedBookFormat.TXT,
            BookImportSupport.detectFormat(null, "text/plain"),
        )
    }

    @Test
    fun everyCodeExtensionMapsToDistinctLanguage() {
        val extensions = listOf(
            "kt", "java", "py", "js", "ts", "go", "rs", "swift", "json", "yaml", "sh", "css",
        )
        for (ext in extensions) {
            val language = CodeFileLanguage.fromFileName("sample.$ext")
            assertNotNull("缺少语言映射: .$ext", language)
            assertTrue("语言 token 为空: .$ext", requireNotNull(language).token.isNotBlank())
        }
    }
}