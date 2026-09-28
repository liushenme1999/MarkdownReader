package space.liushenme.markdownreader.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensurePeriodicBackupScheduled() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_BACKUP_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueAutoBackup() {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueueUniqueWork(AUTO_BACKUP_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueBookContentUpload(bookId: Long) {
        val request = OneTimeWorkRequestBuilder<BookContentWorker>()
            .setConstraints(networkConstraints)
            .setInputData(
                workDataOf(
                    BookContentWorker.KEY_ACTION to BookContentWorker.ACTION_UPLOAD,
                    BookContentWorker.KEY_BOOK_ID to bookId,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(
            "book-content-upload-$bookId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueRemoteBookContentDelete(contentHash: String, fallbackNumericId: Long) {
        val request = OneTimeWorkRequestBuilder<BookContentWorker>()
            .setConstraints(networkConstraints)
            .setInputData(
                workDataOf(
                    BookContentWorker.KEY_ACTION to BookContentWorker.ACTION_DELETE,
                    BookContentWorker.KEY_CONTENT_HASH to contentHash,
                    BookContentWorker.KEY_BOOK_ID to fallbackNumericId,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(
            "book-content-delete-${contentHash.ifBlank { fallbackNumericId.toString() }}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val AUTO_BACKUP_WORK = "automatic-webdav-backup"
        const val PERIODIC_BACKUP_WORK = "periodic-webdav-backup"
    }
}
