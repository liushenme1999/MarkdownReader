package space.liushenme.markdownreader.data.backup

import space.liushenme.markdownreader.data.webdav.WebDavFile

/**
 * Git 项目跨设备同步的纯判断：墓碑是否仍压过一次导入，以及该选哪份备份。
 */
internal object GitProjectSyncPolicy {

    const val LATEST_BACKUP_NAME = "backup.zip"

    /** 上传前最多再合并几次，避免两台同时刷新互相盖掉。 */
    const val MAX_PRE_PUSH_REMERGES = 2

    /**
     * 删除时间晚于导入时间时，墓碑仍然有效，不能把项目写回书架。
     * 导入时间不早于删除时间（含同一毫秒）时，视为删完又导入，应撤销墓碑。
     */
    fun tombstoneBlocksImport(addTimeMillis: Long, deletedAt: Long): Boolean =
        deletedAt > addTimeMillis

    /**
     * 有可靠修改时间时取最新一份。时间全是 0（解析失败）时优先 [LATEST_BACKUP_NAME]，
     * 否则按文件名倒序，避免 PROPFIND 顺序里随便一份旧包。
     */
    fun selectLatestBackup(files: List<WebDavFile>): WebDavFile? {
        val backups = files.filter { !it.isDir && it.displayName.startsWith("backup") }
        if (backups.isEmpty()) return null
        val withTime = backups.filter { it.lastModify > 0L }
        if (withTime.isNotEmpty()) return withTime.maxBy { it.lastModify }
        return backups.firstOrNull { it.displayName == LATEST_BACKUP_NAME }
            ?: backups.maxBy { it.displayName }
    }

    /** [candidate] 是否比这次合并所依据的 [base] 更新，需要先再合并再上传。 */
    fun isNewerBackup(base: WebDavFile, candidate: WebDavFile): Boolean {
        if (candidate.displayName == base.displayName) {
            return candidate.lastModify > base.lastModify
        }
        return when {
            candidate.lastModify > 0L && base.lastModify > 0L ->
                candidate.lastModify > base.lastModify
            candidate.lastModify > 0L -> true
            base.lastModify > 0L -> false
            else -> true
        }
    }
}
