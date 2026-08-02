package space.liushenme.markdownreader.data.backup

import java.util.Date
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity

/**
 * 云端备份用的 Git 项目元数据（不含 localPath / 工作区）。
 * 跨设备按 [remoteUrl] 对齐。
 */
data class GitProjectBackup(
    val remoteUrl: String,
    val title: String,
    val defaultBranch: String = "main",
    val lastCommitSha: String = "",
    val lastPulledAt: Date? = null,
    val addTime: Date = Date(),
    val isPinned: Boolean = false,
    val pinOrder: Long = 0L,
    val isFavorite: Boolean = false,
    val shelfGroup: String = "",
    val lastOpenedRelativePath: String? = null,
    val lastOpenedAt: Date? = null,
    val recentOpenedPathsJson: String = "[]",
) {
    companion object {
        fun fromEntity(entity: GitProjectEntity): GitProjectBackup = GitProjectBackup(
            remoteUrl = entity.remoteUrl,
            title = entity.title,
            defaultBranch = entity.defaultBranch,
            lastCommitSha = entity.lastCommitSha,
            lastPulledAt = entity.lastPulledAt,
            addTime = entity.addTime,
            isPinned = entity.isPinned,
            pinOrder = entity.pinOrder,
            isFavorite = entity.isFavorite,
            shelfGroup = entity.shelfGroup,
            lastOpenedRelativePath = entity.lastOpenedRelativePath,
            lastOpenedAt = entity.lastOpenedAt,
            recentOpenedPathsJson = entity.recentOpenedPathsJson,
        )
    }
}
