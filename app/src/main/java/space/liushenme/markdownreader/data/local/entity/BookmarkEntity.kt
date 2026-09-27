package space.liushenme.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "bookmarks",
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
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val position: Int,
    val previewText: String,
    val note: String? = null,
    val createTime: Date = Date(),
    /** 源码里书签点前面的上下文，最多 48 字。null 表示旧数据。 */
    val quotePrefix: String? = null,
    /** 源码里书签点后面的上下文，最多 48 字。 */
    val quoteSuffix: String? = null,
    val blockIndex: Int? = null,
    val blockOffsetStart: Int? = null,
    val blockOffsetEnd: Int? = null,
    val endBlockIndex: Int? = null,
    val endBlockHash: String? = null,
    /** 书签点所在块的归一化 SHA-256。 */
    val blockHash: String? = null,
    /** 保存时的 [BookEntity.contentHash]。 */
    val docHash: String? = null,
    /** 标题路径，级与级之间用 \\u001f 分隔，最多 4 级。 */
    val headingPath: String? = null,
)
