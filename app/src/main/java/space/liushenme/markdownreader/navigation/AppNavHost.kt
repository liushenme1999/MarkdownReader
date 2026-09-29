package space.liushenme.markdownreader.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import space.liushenme.markdownreader.markdown.MarkdownLinkDispatcher
import space.liushenme.markdownreader.ui.screens.bookshelf.BookshelfLayoutScreen
import space.liushenme.markdownreader.ui.screens.bookshelf.BookshelfScreen
import space.liushenme.markdownreader.ui.screens.bookshelf.BookshelfSearchScreen
import space.liushenme.markdownreader.ui.screens.bookshelf.GroupManagementScreen
import space.liushenme.markdownreader.ui.screens.notes.NotesScreen
import space.liushenme.markdownreader.ui.screens.profile.AboutScreen
import space.liushenme.markdownreader.ui.screens.profile.BackupHelpScreen
import space.liushenme.markdownreader.ui.screens.profile.BackupRestoreScreen
import space.liushenme.markdownreader.ui.screens.profile.LegalDocumentScreen
import space.liushenme.markdownreader.ui.screens.profile.LegalDocumentType
import space.liushenme.markdownreader.ui.screens.profile.ProfileScreen
import space.liushenme.markdownreader.ui.screens.profile.ReadingSettingsScreen
import space.liushenme.markdownreader.ui.screens.project.ProjectBrowserScreen
import space.liushenme.markdownreader.ui.screens.reader.ReaderScreen
import space.liushenme.markdownreader.ui.screens.statistics.StatisticsScreen
import space.liushenme.markdownreader.ui.screens.weblink.WebLinkScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoutes.BOOKSHELF,
    pendingOpenUri: Uri? = null,
    onPendingExternalUriConsumed: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.BOOKSHELF) {
            BookshelfScreen(
                navController = navController,
                onSelectionModeChange = onSelectionModeChange,
                pendingExternalUri = pendingOpenUri,
                onPendingExternalUriConsumed = onPendingExternalUriConsumed,
            )
        }

        composable(AppRoutes.BOOKSHELF_SEARCH) {
            BookshelfSearchScreen(navController = navController)
        }

        composable(AppRoutes.BOOKSHELF_GROUPS) {
            GroupManagementScreen(navController = navController)
        }

        composable(AppRoutes.BOOKSHELF_LAYOUT) {
            BookshelfLayoutScreen(navController = navController)
        }

        composable(AppRoutes.PROFILE) {
            ProfileScreen(navController = navController)
        }

        composable(AppRoutes.READING_SETTINGS) {
            ReadingSettingsScreen(navController = navController)
        }

        composable(AppRoutes.BACKUP_RESTORE) {
            BackupRestoreScreen(navController = navController)
        }

        composable(AppRoutes.BACKUP_HELP) {
            BackupHelpScreen(navController = navController)
        }

        composable(AppRoutes.USER_AGREEMENT) {
            LegalDocumentScreen(
                documentType = LegalDocumentType.USER_AGREEMENT,
                navController = navController,
            )
        }

        composable(AppRoutes.PRIVACY_POLICY) {
            LegalDocumentScreen(
                documentType = LegalDocumentType.PRIVACY_POLICY,
                navController = navController,
            )
        }

        composable(AppRoutes.ABOUT) {
            AboutScreen(navController = navController)
        }

        composable(
            route = AppRoutes.READER,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("jumpKind") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("jumpPosition") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("jumpHighlightId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("jumpBookmarkId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("jumpPreview") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
            ReaderScreen(
                navController = navController,
                bookId = bookId,
                jumpKind = backStackEntry.arguments?.getString("jumpKind"),
                jumpPosition = backStackEntry.arguments?.getString("jumpPosition")?.toIntOrNull(),
                jumpHighlightId = backStackEntry.arguments?.getString("jumpHighlightId")?.toLongOrNull(),
                jumpBookmarkId = backStackEntry.arguments?.getString("jumpBookmarkId")?.toLongOrNull(),
                jumpPreview = backStackEntry.arguments?.getString("jumpPreview"),
            )
        }

        composable(
            route = AppRoutes.PROJECT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
            ),
        ) {
            ProjectBrowserScreen(navController = navController)
        }

        composable(AppRoutes.NOTES) {
            NotesScreen(navController = navController)
        }

        composable(AppRoutes.STATISTICS) {
            StatisticsScreen(navController = navController)
        }

        composable(
            route = AppRoutes.WEB_LINK,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url").orEmpty()
            val url = Uri.decode(encodedUrl)
            if (url.isNotBlank()) {
                WebLinkScreen(
                    url = url,
                    navController = navController,
                )
            } else {
                navController.popBackStack()
            }
        }
    }
}

object MarkdownLinkNavigation {
    fun setupNavigation(
        navController: NavHostController,
        onRequestOpenWebUrl: ((String, () -> Unit) -> Unit)? = null,
    ) {
        MarkdownLinkDispatcher.openUrl = { url ->
            navController.navigate(AppRoutes.webLink(url))
        }
        MarkdownLinkDispatcher.onRequestOpenWebUrl = onRequestOpenWebUrl
    }

    fun cleanup() {
        MarkdownLinkDispatcher.openUrl = null
        MarkdownLinkDispatcher.onRequestOpenWebUrl = null
    }
}
