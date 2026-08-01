package space.liushenme.markdownreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity

@Dao
interface ShelfGroupDao {
    @Query("SELECT * FROM shelf_groups ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<ShelfGroupEntity>>

    @Query("SELECT * FROM shelf_groups ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<ShelfGroupEntity>

    @Query("SELECT * FROM shelf_groups WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ShelfGroupEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM shelf_groups")
    suspend fun getMaxSortOrder(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(group: ShelfGroupEntity): Long

    @Update
    suspend fun update(group: ShelfGroupEntity)

    @Update
    suspend fun updateAll(groups: List<ShelfGroupEntity>)

    @Delete
    suspend fun delete(group: ShelfGroupEntity)

    @Query("DELETE FROM shelf_groups WHERE id = :id")
    suspend fun deleteById(id: Long)
}
