package space.liushenme.markdownreader.data.repository

import android.util.Log
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import space.liushenme.markdownreader.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao

/** Serializes reader writes so lifecycle callbacks never block the main thread. */
@Singleton
class ReaderPersistenceQueue @Inject constructor(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                runCatching {
                    when (command) {
                        is Command.Progress -> bookDao.updateReadingProgress(
                            bookId = command.bookId,
                            progress = command.progress,
                            position = command.position,
                            previewText = command.previewText,
                            time = command.updatedAt,
                        )
                        is Command.Session -> readingProgressDao.upsertAddProgress(
                            bookId = command.bookId,
                            date = command.date,
                            chars = command.charsRead,
                            minutes = command.minutesRead,
                        )
                    }
                }.onFailure { error ->
                    // A single failed Room write must not terminate the application-wide writer.
                    Log.e(TAG, "Reader persistence command failed: ${command::class.simpleName}", error)
                }
            }
        }
    }

    fun saveProgress(bookId: Long, progress: Float, position: Int, previewText: String) {
        commands.trySend(
            Command.Progress(
                bookId = bookId,
                progress = progress,
                position = position,
                previewText = previewText,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun recordSession(bookId: Long, charsRead: Int, minutesRead: Int) {
        if (charsRead <= 0 && minutesRead <= 0) return
        commands.trySend(
            Command.Session(
                bookId = bookId,
                date = ReadingSessionStats.startOfToday(),
                charsRead = charsRead.coerceAtLeast(0),
                minutesRead = minutesRead.coerceAtLeast(0),
            ),
        )
    }

    private sealed interface Command {
        data class Progress(
            val bookId: Long,
            val progress: Float,
            val position: Int,
            val previewText: String,
            val updatedAt: Long,
        ) : Command

        data class Session(
            val bookId: Long,
            val date: Date,
            val charsRead: Int,
            val minutesRead: Int,
        ) : Command
    }

    private companion object {
        const val TAG = "ReaderPersistence"
    }
}
