package space.liushenme.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "books",
    indices = [Index(value = ["contentHash"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String? = null,
    val filePath: String,
    /** 导入格式，与 ImportedBookFormat.storedKey 一致，如 markdown、txt、pdf */
    val importFormat: String = "markdown",
    /** 本地封面文件绝对路径（可为解析包内 cover.jpg / 旧版 book_covers 路径） */
    val coverImagePath: String? = null,
    /**
     * 解析结果目录（[ParsedBookStorage]）：含 body.txt、toc.json、可选封面。
     * 非空且目录有效时，阅读器只从此加载，不再访问 [filePath] 原始文件。
     */
    val parsedBundlePath: String? = null,
    val coverColor: Int = 0,
    val totalChars: Int = 0,
    val currentPosition: Int = 0,
    /**
     * 上次阅读视口顶部附近纯文本预览（与书签 [BookmarkEntity.previewText] 同格式），
     * 用于重新进入时按书签同样的方式精确定位。
     */
    val progressPreviewText: String = "",
    val readingProgress: Float = 0f,
    val lastReadTime: Date? = null,
    val addTime: Date = Date(),
    val isFavorite: Boolean = false,
    /** 书架分组名称，空字符串表示未分组 */
    val shelfGroup: String = "",
    val isPinned: Boolean = false,
    /** 置顶排序，越大越靠前 */
    val pinOrder: Long = 0L,
    /**
     * 跨设备稳定标识：正文 SHA-256 hex。
     * WebDAV 正文与备份合并均按此字段对齐；本机主键仍为 [id]。
     */
    val contentHash: String = "",
)
