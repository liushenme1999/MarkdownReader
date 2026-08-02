package space.liushenme.markdownreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.BookmarkDao
import space.liushenme.markdownreader.data.local.dao.GitProjectDao
import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao
import space.liushenme.markdownreader.data.local.dao.ShelfGroupDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ReadingProgressEntity::class,
        ShelfGroupEntity::class,
        GitProjectEntity::class,
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun shelfGroupDao(): ShelfGroupDao
    abstract fun gitProjectDao(): GitProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN shelfGroup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN pinOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN importFormat TEXT NOT NULL DEFAULT 'markdown'"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverImagePath TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN parsedBundlePath TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN progressPreviewText TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** 合并同书同日重复行，并加上 UNIQUE(bookId, date) 以支撑原子累加。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_progress_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `date` INTEGER NOT NULL,
                        `readChars` INTEGER NOT NULL,
                        `readTimeMinutes` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `reading_progress_new` (`bookId`, `date`, `readChars`, `readTimeMinutes`)
                    SELECT `bookId`, `date`, SUM(`readChars`), SUM(`readTimeMinutes`)
                    FROM `reading_progress`
                    GROUP BY `bookId`, `date`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `reading_progress`")
                db.execSQL("ALTER TABLE `reading_progress_new` RENAME TO `reading_progress`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reading_progress_bookId` ON `reading_progress` (`bookId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_progress_bookId_date` ON `reading_progress` (`bookId`, `date`)",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE highlights ADD COLUMN style TEXT NOT NULL DEFAULT 'background'",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shelf_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_shelf_groups_name` " +
                        "ON `shelf_groups` (`name`)",
                )
                db.execSQL(
                    """
                    INSERT INTO `shelf_groups` (`name`, `sortOrder`)
                    SELECT DISTINCT TRIM(`shelfGroup`), 0
                    FROM `books`
                    WHERE TRIM(`shelfGroup`) != ''
                    """.trimIndent(),
                )
                db.execSQL("UPDATE `shelf_groups` SET `sortOrder` = `id`")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `shelf_groups` ADD COLUMN `isVisible` INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL("UPDATE `shelf_groups` SET `sortOrder` = `sortOrder` + 1")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `shelf_groups` (`name`, `sortOrder`, `isVisible`)
                    VALUES ('__all__', 0, 1)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `shelf_groups` (`name`, `sortOrder`, `isVisible`)
                    SELECT '__favorites__', COALESCE(MAX(`sortOrder`), -1) + 1, 1
                    FROM `shelf_groups`
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "UPDATE books SET contentHash = 'legacy_' || id",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_contentHash` " +
                        "ON `books` (`contentHash`)",
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN gitProjectId INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN gitRelativePath TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_books_gitProjectId` " +
                        "ON `books` (`gitProjectId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `git_projects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `remoteUrl` TEXT NOT NULL,
                        `defaultBranch` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `lastCommitSha` TEXT NOT NULL,
                        `lastPulledAt` INTEGER,
                        `addTime` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `pinOrder` INTEGER NOT NULL,
                        `shelfGroup` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_git_projects_remoteUrl` " +
                        "ON `git_projects` (`remoteUrl`)",
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE git_projects ADD COLUMN lastOpenedRelativePath TEXT")
                db.execSQL("ALTER TABLE git_projects ADD COLUMN lastOpenedAt INTEGER")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE git_projects ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE git_projects ADD COLUMN recentOpenedPathsJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "markdown_reader_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
