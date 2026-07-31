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
    val createTime: Date = Date()
)
