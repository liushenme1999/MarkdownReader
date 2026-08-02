package space.liushenme.markdownreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import space.liushenme.markdownreader.data.local.entity.DeletedGitProjectEntity

@Dao
interface DeletedGitProjectDao {
    @Query("SELECT * FROM deleted_git_projects")
    suspend fun getAll(): List<DeletedGitProjectEntity>

    @Query("SELECT remoteUrl FROM deleted_git_projects")
    suspend fun getAllRemoteUrls(): List<String>

    @Query("SELECT * FROM deleted_git_projects WHERE remoteUrl = :remoteUrl LIMIT 1")
    suspend fun getByRemoteUrl(remoteUrl: String): DeletedGitProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeletedGitProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeletedGitProjectEntity>)

    @Query("DELETE FROM deleted_git_projects WHERE remoteUrl = :remoteUrl")
    suspend fun deleteByRemoteUrl(remoteUrl: String)
}
