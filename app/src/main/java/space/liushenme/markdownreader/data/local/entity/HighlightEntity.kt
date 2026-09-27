package space.liushenme.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val startPosition: Int,
    val endPosition: Int,
    val highlightedText: String,
    val color: Int = 0xFFFFFF00.toInt(), // 默认黄色
    /** [space.liushenme.markdownreader.model.HighlightStyle.storageKey] */
    val style: String = "background",
    val note: String? = null,
    val createTime: Date = Date(),
    /** 源码里划线前面的上下文，最多 48 字。null 表示旧数据。 */
    val quotePrefix: String? = null,
    /** 源码里划线后面的上下文，最多 48 字。 */
    val quoteSuffix: String? = null,
    val blockIndex: Int? = null,
    val blockOffsetStart: Int? = null,
    val blockOffsetEnd: Int? = null,
    /** 终点落在另一块时记下那一块；同一块则为 null。 */
    val endBlockIndex: Int? = null,
    val endBlockHash: String? = null,
    /** 起点所在块的归一化 SHA-256。 */
    val blockHash: String? = null,
    /** 保存时的 [BookEntity.contentHash]。 */
    val docHash: String? = null,
    /** 标题路径，级与级之间用 \\u001f 分隔，最多 4 级。 */
    val headingPath: String? = null,
)
