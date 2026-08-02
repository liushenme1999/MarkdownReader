package space.liushenme.markdownreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity

@Dao
interface GitProjectDao {
    /**
     * 置顶优先；其余按「最近活动」倒序（打开文档时间，未打开则用导入时间）。
     * 不用 lastPulledAt，避免下拉同步把项目顶到最前。
     */
    @Query(
        "SELECT * FROM git_projects ORDER BY isPinned DESC, pinOrder DESC, " +
            "COALESCE(lastOpenedAt, addTime) DESC, id DESC",
    )
    fun getAllProjects(): Flow<List<GitProjectEntity>>

    @Query(
        "SELECT * FROM git_projects ORDER BY isPinned DESC, pinOrder DESC, " +
            "COALESCE(lastOpenedAt, addTime) DESC, id DESC",
    )
    suspend fun getAllProjectsList(): List<GitProjectEntity>

    @Query("SELECT * FROM git_projects WHERE id = :id")
    suspend fun getById(id: Long): GitProjectEntity?

    @Query("SELECT * FROM git_projects WHERE remoteUrl = :remoteUrl LIMIT 1")
    suspend fun getByRemoteUrl(remoteUrl: String): GitProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: GitProjectEntity): Long

    @Update
    suspend fun update(project: GitProjectEntity)

    @Delete
    suspend fun delete(project: GitProjectEntity)

    @Query("DELETE FROM git_projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE git_projects SET isPinned = 1, pinOrder = :pinOrder WHERE id IN (:ids)")
    suspend fun pinByIds(ids: List<Long>, pinOrder: Long)

    @Query("UPDATE git_projects SET isPinned = 0, pinOrder = 0 WHERE id IN (:ids)")
    suspend fun unpinByIds(ids: List<Long>)

    @Query("UPDATE git_projects SET shelfGroup = :groupName WHERE id IN (:ids)")
    suspend fun updateShelfGroupByIds(ids: List<Long>, groupName: String)

    @Query("UPDATE git_projects SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE git_projects SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun updateFavoriteByIds(ids: List<Long>, isFavorite: Boolean)
}
