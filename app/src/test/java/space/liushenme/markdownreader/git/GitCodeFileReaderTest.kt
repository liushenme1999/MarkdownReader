package space.liushenme.markdownreader.git

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitCodeFileReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun readsSourceAndRemovesUtf8BomOnly() {
        val source = "# 注释\n| 列 | 值 |\n<template>{{ value }}</template>\n"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            source.toByteArray(StandardCharsets.UTF_8)
        val file = tmp.newFile("App.vue").apply { writeBytes(bytes) }

        assertEquals(source, GitCodeFileReader.read(file))
    }

    @Test
    fun acceptsExactlyTwoMegabytes() {
        val bytes = ByteArray(GitCodeFileReader.MAX_BYTES) { 'a'.code.toByte() }
        val file = tmp.newFile("large.py").apply { writeBytes(bytes) }

        assertEquals(bytes.size, GitCodeFileReader.read(file).length)
    }

    @Test
    fun rejectsMoreThanTwoMegabytesBeforeDecoding() {
        val file = tmp.newFile("large.java").apply {
            writeBytes(ByteArray(GitCodeFileReader.MAX_BYTES + 1) { 'a'.code.toByte() })
        }

        val error = assertThrows(GitCodeFileReader.ReadException::class.java) {
            GitCodeFileReader.read(file)
        }
        assertEquals(GitCodeFileReader.Failure.TooLarge, error.failure)
    }

    @Test
    fun rejectsInvalidUtf8WithoutReplacementCharacters() {
        val error = assertThrows(GitCodeFileReader.ReadException::class.java) {
            GitCodeFileReader.decode(byteArrayOf(0xC3.toByte(), 0x28))
        }

        assertEquals(GitCodeFileReader.Failure.UnsupportedEncoding, error.failure)
    }
}
