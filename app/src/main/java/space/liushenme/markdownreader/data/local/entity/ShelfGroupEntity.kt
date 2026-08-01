package space.liushenme.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shelf_groups",
    indices = [Index(value = ["name"], unique = true)],
)
data class ShelfGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** 越小越靠前 */
    val sortOrder: Long = 0L,
    /** 是否在书架顶部分组标签中显示 */
    val isVisible: Boolean = true,
)
