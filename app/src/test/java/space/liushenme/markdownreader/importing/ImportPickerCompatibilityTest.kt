package space.liushenme.markdownreader.importing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件选择器 / 「用其他应用打开」的兼容性回归。
 *
 * 系统把很多源码文件报成 application/octet-stream，若白名单里没有这一项，
 * 文件在选择器里会被置灰或隐藏，用户看到的就是「文件明明在，却导入不了」。
 */
class ImportPickerCompatibilityTest {

    private val codeExtensions = listOf(
        "kt", "java", "py", "js", "ts", "go", "rs", "swift", "json", "yaml", "sh", "css",
    )

    @Test
    fun pickerAcceptsOctetStreamForUnknownTypedSourceFiles() {
        assertTrue(
            "缺少 application/octet-stream 兜底，未知 MIME 的源码文件无法选中",
            "application/octet-stream" in BookImportSupport.importMimeTypes,
        )
        assertTrue("text/* 未覆盖", "text/plain" in BookImportSupport.importMimeTypes)
    }

    @Test
    fun everySupportedExtensionHasAMimeRoute() {
        // 扩展名白名单内的格式，要么有专门 MIME，要么由 octet-stream 兜底选中
        val hasOctet = "application/octet-stream" in BookImportSupport.importMimeTypes
        for (ext in codeExtensions) {
            val name = "sample.$ext"
            assertFalse("$ext 被误判为已移除格式", BookImportSupport.isRemovedFormat(name, null))
            assertFalse(
                "$ext 被误判为不可导入（picker 已放开 octet-stream=$hasOctet）",
                BookImportSupport.isUnsupportedPick(name, "application/octet-stream"),
            )
            assertTrue("$ext 未识别为源码", BookImportSupport.detectFormat(name, null).isCode)
        }
    }

    @Test
    fun unknownBinaryPickIsRejectedInsteadOfImportedAsText() {
        assertTrue(
            "APK 不应被当成文本导入",
            BookImportSupport.isUnsupportedPick("app.apk", "application/octet-stream"),
        )
        assertTrue(
            "无 MIME 的未知扩展名应拒绝",
            BookImportSupport.isUnsupportedPick("data.bin", null),
        )
        assertFalse(
            "无扩展名但声明 text/plain 仍允许",
            BookImportSupport.isUnsupportedPick("README", "text/plain"),
        )
    }

    @Test
    fun markdownAndTextStayImportableUnderEveryCommonMime() {
        for (mime in listOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream", null)) {
            assertFalse(
                "md 在 $mime 下不可导入",
                BookImportSupport.isUnsupportedPick("a.md", mime),
            )
            assertFalse(
                "txt 在 $mime 下不可导入",
                BookImportSupport.isUnsupportedPick("a.txt", mime),
            )
        }
    }
}
