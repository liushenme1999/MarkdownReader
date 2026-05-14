package com.example.markdownreader.importing

/**
 * MOBI 中 PalmDOC（compression type 2）单 record 解压。
 *
 * 实现要点：
 * - 直接用 [ByteArray] + 手动 size 维护输出缓冲，避免 [mutableListOf]<Byte> 上百万次自动装箱
 *   和 ArrayList 频繁扩容（旧实现解压几 MB MOBI 时占用 100+ MB 内存，易 OOM/ANR）。
 * - LZ77 回溯允许自重叠：必须逐字节 `buf[size + i] = buf[srcStart + i]`，
 *   不能用 [System.arraycopy] 拷贝整段（自重叠时复制结果不等价）。
 *
 * 算法参考：MobileRead PalmDOC 词条。
 */
internal object PalmDocDecompressor {

    fun decompress(block: ByteArray): ByteArray {
        if (block.isEmpty()) return ByteArray(0)
        // PalmDOC record 单块解压后通常 ≤ 4096 字节；给 2x headroom 后扩容几乎不会触发。
        var buf = ByteArray(maxOf(4096, block.size * 2))
        var size = 0
        var pos = 0

        fun grow(extra: Int) {
            val need = size + extra
            if (need <= buf.size) return
            var cap = buf.size
            while (cap < need) cap = cap shl 1
            buf = buf.copyOf(cap)
        }

        while (pos < block.size) {
            val c = block[pos++].toInt() and 0xff
            when {
                c == 0 -> {
                    grow(1)
                    buf[size++] = 0
                }
                c in 0x09..0x7f -> {
                    grow(1)
                    buf[size++] = c.toByte()
                }
                c in 0x01..0x08 -> {
                    val end = (pos + c).coerceAtMost(block.size)
                    val len = end - pos
                    if (len > 0) {
                        grow(len)
                        System.arraycopy(block, pos, buf, size, len)
                        size += len
                    }
                    pos = end
                }
                c in 0x80..0xbf -> {
                    if (pos >= block.size) break
                    val c2 = block[pos++].toInt() and 0xff
                    val combined = ((c and 0x3f) shl 8) or c2
                    val length = (combined shr 11) + 3
                    val distance = combined and 0x7ff
                    if (distance > 0) {
                        val srcStart = size - distance
                        if (srcStart >= 0) {
                            grow(length)
                            // 自重叠：必须逐字节，不能用 arraycopy
                            var i = 0
                            while (i < length) {
                                buf[size + i] = buf[srcStart + i]
                                i++
                            }
                            size += length
                        }
                    }
                }
                c in 0xc0..0xff -> {
                    grow(2)
                    buf[size++] = 0x20
                    buf[size++] = (c xor 0x80).toByte()
                }
            }
        }
        return if (size == buf.size) buf else buf.copyOf(size)
    }
}
