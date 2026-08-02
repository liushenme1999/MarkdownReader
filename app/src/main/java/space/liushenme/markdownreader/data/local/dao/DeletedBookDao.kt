package space.liushenme.markdownreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import space.liushenme.markdownreader.data.local.entity.DeletedBookEntity

@Dao
interface DeletedBookDao {
    @Query("SELECT * FROM deleted_books")
    suspend fun getAll(): List<DeletedBookEntity>

    @Query("SELECT contentHash FROM deleted_books")
    suspend fun getAllHashes(): List<String>

    @Query("SELECT * FROM deleted_books WHERE contentHash = :contentHash LIMIT 1")
    suspend fun getByHash(contentHash: String): DeletedBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeletedBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeletedBookEntity>)

    @Query("DELETE FROM deleted_books WHERE contentHash = :contentHash")
    suspend fun deleteByHash(contentHash: String)
}
