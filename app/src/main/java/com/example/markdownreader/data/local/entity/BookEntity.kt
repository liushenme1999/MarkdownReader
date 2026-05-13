package com.example.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String? = null,
    val filePath: String,
    /** 导入格式，与 ImportedBookFormat.storedKey 一致，如 markdown、txt、epub */
    val importFormat: String = "markdown",
    val coverColor: Int = 0,
    val totalChars: Int = 0,
    val currentPosition: Int = 0,
    val readingProgress: Float = 0f,
    val lastReadTime: Date? = null,
    val addTime: Date = Date(),
    val isFavorite: Boolean = false,
    /** 书架分组名称，空字符串表示未分组 */
    val shelfGroup: String = "",
    val isPinned: Boolean = false,
    /** 置顶排序，越大越靠前 */
    val pinOrder: Long = 0L
)
