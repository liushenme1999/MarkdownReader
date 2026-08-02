package space.liushenme.markdownreader.git

import android.content.Context
import java.io.File

object GitProjectStorage {
    const val ROOT_DIR_NAME = "git_projects"
    /** clone 后目录体积超过该阈值时 Toast 提示（不硬拦） */
    const val SIZE_WARN_BYTES: Long = 200L * 1024L * 1024L

    fun rootDir(context: Context): File =
        File(context.filesDir, ROOT_DIR_NAME).also { it.mkdirs() }

    fun projectDir(context: Context, projectId: Long): File =
        File(rootDir(context), projectId.toString())

    fun resolveFile(projectRoot: File, relativePath: String): File? {
        val normalized = relativePath.trim().trimStart('/').replace('\\', '/')
        if (normalized.isEmpty() || normalized.contains("..")) return null
        val file = File(projectRoot, normalized)
        val rootCanon = projectRoot.canonicalFile
        val fileCanon = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (!fileCanon.path.startsWith(rootCanon.path + File.separator) && fileCanon != rootCanon) {
            return null
        }
        return fileCanon
    }

    fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    fun deleteProjectDir(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).deleteRecursively() }
    }
}
