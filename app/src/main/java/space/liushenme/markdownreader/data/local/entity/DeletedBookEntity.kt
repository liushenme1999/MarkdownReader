package space.liushenme.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书籍删除墓碑：跨设备同步时用 [contentHash] 对齐，避免对端把已删书再推回云端。
 */
@Entity(tableName = "deleted_books")
data class DeletedBookEntity(
    @PrimaryKey
    val contentHash: String,
    val deletedAt: Long,
)
