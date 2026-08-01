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
import space.liushenme.markdownreader.ui.screens.bookshelf.BookshelfScreen
import space.liushenme.markdownreader.ui.screens.notes.NotesScreen
import space.liushenme.markdownreader.ui.screens.profile.AboutScreen
import space.liushenme.markdownreader.ui.screens.profile.LegalDocumentScreen
import space.liushenme.markdownreader.ui.screens.profile.LegalDocumentType
import space.liushenme.markdownreader.ui.screens.profile.ProfileScreen
import space.liushenme.markdownreader.ui.screens.profile.ReadingSettingsScreen
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

        composable(AppRoutes.PROFILE) {
            ProfileScreen(navController = navController)
        }

        composable(AppRoutes.READING_SETTINGS) {
            ReadingSettingsScreen(navController = navController)
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

        composable(AppRoutes.READER) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
            ReaderScreen(
                navController = navController,
                bookId = bookId,
            )
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
    fun setupNavigation(navController: NavHostController) {
        MarkdownLinkDispatcher.openUrl = { url ->
            navController.navigate(AppRoutes.webLink(url))
        }
    }

    fun cleanup() {
        MarkdownLinkDispatcher.openUrl = null
    }
}
