package space.liushenme.markdownreader.di

import android.content.Context
import space.liushenme.markdownreader.data.local.AppDatabase
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.BookmarkDao
import space.liushenme.markdownreader.data.local.dao.GitProjectDao
import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao
import space.liushenme.markdownreader.data.local.dao.ShelfGroupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideHighlightDao(database: AppDatabase): HighlightDao = database.highlightDao()

    @Provides
    fun provideReadingProgressDao(database: AppDatabase): ReadingProgressDao = database.readingProgressDao()

    @Provides
    fun provideShelfGroupDao(database: AppDatabase): ShelfGroupDao = database.shelfGroupDao()

    @Provides
    fun provideGitProjectDao(database: AppDatabase): GitProjectDao = database.gitProjectDao()
}
