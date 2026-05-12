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
    val coverColor: Int = 0,
    val totalChars: Int = 0,
    val currentPosition: Int = 0,
    val readingProgress: Float = 0f,
    val lastReadTime: Date? = null,
    val addTime: Date = Date(),
    val isFavorite: Boolean = false
)
