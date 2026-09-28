package space.liushenme.markdownreader.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import space.liushenme.markdownreader.data.backup.BackupManager
import space.liushenme.markdownreader.data.backup.BookContentSync

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BackgroundWorkerEntryPoint {
    fun backupManager(): BackupManager
    fun bookContentSync(): BookContentSync
}

private fun Context.workerEntryPoint(): BackgroundWorkerEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, BackgroundWorkerEntryPoint::class.java)

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        applicationContext.workerEntryPoint().backupManager().autoBackup().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
}

class BookContentWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sync = applicationContext.workerEntryPoint().bookContentSync()
        val operation = when (inputData.getString(KEY_ACTION)) {
            ACTION_UPLOAD -> sync.uploadBookContent(inputData.getLong(KEY_BOOK_ID, -1L))
            ACTION_DELETE -> sync.deleteRemoteBookContent(
                contentHash = inputData.getString(KEY_CONTENT_HASH).orEmpty(),
                fallbackNumericId = inputData.getLong(KEY_BOOK_ID, -1L).takeIf { it >= 0L },
            )
            else -> return Result.failure()
        }
        return operation.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_CONTENT_HASH = "content_hash"
        const val ACTION_UPLOAD = "upload"
        const val ACTION_DELETE = "delete"
    }
}
