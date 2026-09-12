package space.liushenme.markdownreader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "git_projects",
    indices = [
        Index(value = ["remoteUrl"], unique = true),
    ],
)
data class GitProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    /** 规范化 HTTPS 浏览地址，如 https://github.com/owner/repo */
    val remoteUrl: String,
    val defaultBranch: String = "main",
    /** 本地工作区绝对路径 */
    val localPath: String = "",
    val lastCommitSha: String = "",
    val lastPulledAt: Date? = null,
    val addTime: Date = Date(),
    val isPinned: Boolean = false,
    val pinOrder: Long = 0L,
    val isFavorite: Boolean = false,
    /** 书架分组名称，空字符串表示未分组 */
    val shelfGroup: String = "",
    /** 最近打开的仓库内相对路径，如 docs/guide.md */
    val lastOpenedRelativePath: String? = null,
    val lastOpenedAt: Date? = null,
    /** 最近打开路径历史 JSON 数组（新→旧），如 ["a.md","b.md"] */
    val recentOpenedPathsJson: String = "[]",
    /** 远程分支 HEAD 与本地 lastCommitSha 不一致，或本地尚无工作区 */
    val hasRemoteUpdate: Boolean = false,
)
