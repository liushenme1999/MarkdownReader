package space.liushenme.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Git 项目删除墓碑：按 [remoteUrl] 跨设备对齐。
 */
@Entity(tableName = "deleted_git_projects")
data class DeletedGitProjectEntity(
    @PrimaryKey
    val remoteUrl: String,
    val deletedAt: Long,
)
