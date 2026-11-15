package space.liushenme.markdownreader

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import space.liushenme.markdownreader.importing.BookContentLoader
import space.liushenme.markdownreader.importing.BookImportSupport
import space.liushenme.markdownreader.importing.CodeFileLanguage
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.PdfReaderContent

/**
 * 全格式导入矩阵：用真实设备 API（content resolver 解码、PdfRenderer 栅格化、CodeFileLanguage）
 * 逐一导入 `assets/samples` 下的每种格式，断言导入结果可用。
 *
 * 这套测试对应「导入测试文档集」的回归：新增格式或改动导入管线时，
 * 任何格式出现空正文 / 占位符 / 乱码都应在这里失败。
 */
@RunWith(AndroidJUnit4::class)
class FormatImportMatrixInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private fun samples(): List<String> {
        val names = requireNotNull(context.assets.list("samples")) { "samples missing" }
        return names.filter { !it.startsWith(".") }.sorted()
    }

    private fun readSample(name: String): ByteArray =
        context.assets.open("samples/$name").use { it.readBytes() }

    /** 与系统文件选择器一致的 MIME 推断（部分扩展名无法识别时为 null）。 */
    private fun systemMimeOf(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    @Test
    fun everySampleImportsIntoUsableContent() {
        val names = samples()
        assertTrue("no samples found", names.size >= 19)

        val report = StringBuilder()
        val failures = mutableListOf<String>()

        for (name in names) {
            val mime = systemMimeOf(name)
            val format = BookImportSupport.detectFormat(name, mime)
            val removed = BookImportSupport.isRemovedFormat(name, mime)
            val bytes = readSample(name)
            val outcome = runCatching {
                BookContentLoader.loadUrlBytesForImport(context, bytes, format, null)
            }

            val line = StringBuilder()
            line.append("$name mime=${mime ?: "null"} format=${format.storedKey} bytes=${bytes.size}")

            if (removed) {
                failures += "$name: 被误判为已移除格式"
                line.append(" REMOVED")
                report.appendLine(line)
                continue
            }

            outcome.onFailure { error ->
                failures += "$name: 导入抛异常 ${error.javaClass.simpleName}: ${error.message}"
                line.append(" THROW ${error.message}")
            }

            outcome.onSuccess { result ->
                val body = result.extracted.body
                line.append(" body=${body.length}")
                if (body.isBlank()) {
                    failures += "$name: 导入正文为空"
                    line.append(" EMPTY")
                }
                if (body.contains("无法解析") || body.contains("暫不支援") || body.contains("暫不支援該格式")) {
                    failures += "$name: 命中占位正文"
                    line.append(" PLACEHOLDER")
                }
                when {
                    format.isPdf -> {
                        assertNotNull("$name: PDF 未生成资产目录", result.stagedAssetsDir)
                        assertTrue(
                            "$name: PDF 正文不是页图结构",
                            PdfReaderContent.looksLikePdfBody(body),
                        )
                        val pages = result.stagedAssetsDir?.listFiles()?.count { it.extension == "jpg" } ?: 0
                        line.append(" pages=$pages")
                        if (pages <= 0) failures += "$name: PDF 未渲染出页图"
                    }

                    format.isCode -> {
                        val language = CodeFileLanguage.fromFileName(name)
                        line.append(" lang=${language?.token}")
                        if (language == null) {
                            failures += "$name: 代码语言未识别"
                        } else {
                            // 代码正文必须原样保留（换行、缩进、长行）
                            assertTrue("$name: 代码正文被裁剪", body.length > bytes.size / 4)
                            assertTrue("$name: 代码缩进丢失", body.contains("    ") || body.contains("\t"))
                            val firstLine = bytes.decodeToString().lineSequence().first { it.isNotBlank() }
                            if (firstLine.isNotBlank() && !body.contains(firstLine.take(20))) {
                                failures += "$name: 代码首行内容不匹配"
                            }
                        }
                    }

                    format.isHtml -> {
                        line.append(" html=${body.contains("<html") || body.contains("<!DOCTYPE")}")
                        if (!body.contains("<h1") && !body.contains("<body")) {
                            failures += "$name: HTML 正文未保留标签"
                        }
                    }

                    else -> {
                        // Markdown / TXT：中英文内容必须完整解码
                        val hasCjk = bytes.decodeToString().any { it.code in 0x4E00..0x9FFF }
                        if (hasCjk && !body.any { it.code in 0x4E00..0x9FFF }) {
                            failures += "$name: 中文解码丢失"
                        }
                        if (format == ImportedBookFormat.MARKDOWN) {
                            assertTrue("$name: Markdown 长度异常", body.length > 100)
                        }
                    }
                }
            }
            report.appendLine(line)
        }

        java.io.File(context.getExternalFilesDir(null), "format-import-matrix.txt")
            .apply { parentFile?.mkdirs() }
            .writeText(report.toString())
        android.util.Log.e("FORMAT_MATRIX", report.toString())
        assertTrue("导入矩阵失败:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyTextSampleRoundTripsThroughFileUri() {
        // 覆盖「用其他应用打开」路径：file:// URI → ContentResolver → 解码
        val failures = mutableListOf<String>()
        for (name in samples().filter { !it.endsWith(".pdf") }) {
            val format = BookImportSupport.detectFormat(name, systemMimeOf(name))
            val file = File(context.cacheDir, "uri-$name").apply { writeBytes(readSample(name)) }
            try {
                val result = BookContentLoader.loadForImport(context, Uri.fromFile(file), format)
                if (result.extracted.body.isBlank()) failures += "$name: file:// 导入为空"
            } catch (e: Exception) {
                failures += "$name: file:// 导入异常 ${e.message}"
            } finally {
                file.delete()
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun sampleSetCoversEverySupportedFormat() {
        val formats = samples()
            .map { BookImportSupport.detectFormat(it, systemMimeOf(it)) }
            .toSet()
        for (format in ImportedBookFormat.entries) {
            assertTrue("缺少格式样本: $format", format in formats)
        }
        assertFalse("Markdown 样本缺失", samples().none { it.endsWith(".md") })
    }
}