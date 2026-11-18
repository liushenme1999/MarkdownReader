package space.liushenme.markdownreader.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 升级脚本回归：用真实 SQLite 建「旧版本表结构」，跑对应 Migration，
 * 断言新列已加上且旧数据不丢（防升级丢书/丢划线）。
 *
 * 只验证最近两个版本（17→18、18→19）；更早的脚本由 addMigrations 链式覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDatabaseMigrationTest {

    private var dbFile: File? = null
    private var helper: SupportSQLiteOpenHelper? = null
    private var database: SupportSQLiteDatabase? = null

    private fun openDb(name: String, version: Int): SupportSQLiteDatabase {
        val context: Context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "migration-$name.db")
        file.delete()
        dbFile = file
        val h = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper = h
        val db = h.writableDatabase
        database = db
        db.execSQL("PRAGMA user_version = $version")
        return db
    }

    @After
    fun tearDown() {
        database?.close()
        helper?.close()
        dbFile?.delete()
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                result += cursor.getString(nameIndex)
            }
        }
        return result
    }

    @Test
    fun migration18To19_addsAnnotationAnchorColumnsAndKeepsRows() {
        val db = openDb("v18", 18)
        db.execSQL("CREATE TABLE highlights (id INTEGER PRIMARY KEY, bookId INTEGER NOT NULL, quote TEXT)")
        db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY, bookId INTEGER NOT NULL, position INTEGER)")
        db.execSQL("INSERT INTO highlights (id, bookId, quote) VALUES (1, 42, '重点')")
        db.execSQL("INSERT INTO bookmarks (id, bookId, position) VALUES (2, 42, 100)")

        val migration = AppDatabase.migrationFor(18, 19)
        assertNotNull("缺少 18→19 迁移脚本", migration)
        migration!!.migrate(db)

        val expected = setOf(
            "quotePrefix", "quoteSuffix", "blockIndex", "blockOffsetStart",
            "blockOffsetEnd", "endBlockIndex", "endBlockHash", "blockHash",
            "docHash", "headingPath",
        )
        assertTrue("highlights 缺少列: ${expected - columns(db, "highlights")}", columns(db, "highlights").containsAll(expected))
        assertTrue("bookmarks 缺少列: ${expected - columns(db, "bookmarks")}", columns(db, "bookmarks").containsAll(expected))

        db.query("SELECT quote FROM highlights WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("重点", it.getString(0))
        }
        db.query("SELECT position FROM bookmarks WHERE id = 2").use {
            assertTrue(it.moveToFirst())
            assertEquals(100, it.getInt(0))
        }
    }

    @Test
    fun migration17To18_addsHasRemoteUpdateWithDefault() {
        val db = openDb("v17", 17)
        db.execSQL("CREATE TABLE git_projects (id INTEGER PRIMARY KEY, owner TEXT, repo TEXT)")
        db.execSQL("INSERT INTO git_projects (id, owner, repo) VALUES (7, 'liushenme1999', 'MarkdownReader')")

        val migration = AppDatabase.migrationFor(17, 18)
        assertNotNull("缺少 17→18 迁移脚本", migration)
        migration!!.migrate(db)

        assertTrue("git_projects 缺少 hasRemoteUpdate", columns(db, "git_projects").contains("hasRemoteUpdate"))
        db.query("SELECT hasRemoteUpdate FROM git_projects WHERE id = 7").use {
            assertTrue(it.moveToFirst())
            assertEquals("历史项目默认不应显示远程更新", 0, it.getInt(0))
        }
    }
}
