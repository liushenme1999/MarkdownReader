package space.liushenme.markdownreader.data.backup

import space.liushenme.markdownreader.data.local.entity.DeletedGitProjectEntity

data class DeletedGitProjectBackup(
    val remoteUrl: String,
    val deletedAt: Long,
) {
    fun toEntity(): DeletedGitProjectEntity = DeletedGitProjectEntity(
        remoteUrl = remoteUrl,
        deletedAt = deletedAt,
    )

    companion object {
        fun fromEntity(entity: DeletedGitProjectEntity): DeletedGitProjectBackup =
            DeletedGitProjectBackup(
                remoteUrl = entity.remoteUrl,
                deletedAt = entity.deletedAt,
            )
    }
}
