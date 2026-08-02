package space.liushenme.markdownreader.data.backup

import space.liushenme.markdownreader.data.local.entity.DeletedBookEntity

data class DeletedBookBackup(
    val contentHash: String,
    val deletedAt: Long,
) {
    fun toEntity(): DeletedBookEntity = DeletedBookEntity(
        contentHash = contentHash,
        deletedAt = deletedAt,
    )

    companion object {
        fun fromEntity(entity: DeletedBookEntity): DeletedBookBackup = DeletedBookBackup(
            contentHash = entity.contentHash,
            deletedAt = entity.deletedAt,
        )
    }
}
