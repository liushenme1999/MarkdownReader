package com.example.markdownreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.markdownreader.data.local.dao.BookDao
import com.example.markdownreader.data.local.dao.BookmarkDao
import com.example.markdownreader.data.local.dao.HighlightDao
import com.example.markdownreader.data.local.dao.ReadingProgressDao
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.data.local.entity.BookmarkEntity
import com.example.markdownreader.data.local.entity.HighlightEntity
import com.example.markdownreader.data.local.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ReadingProgressEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "markdown_reader_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
