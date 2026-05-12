package com.example.markdownreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.markdownreader.ui.screens.bookshelf.BookshelfScreen
import com.example.markdownreader.ui.screens.reader.ReaderScreen
import com.example.markdownreader.ui.screens.notes.NotesScreen
import com.example.markdownreader.ui.screens.statistics.StatisticsScreen
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarkdownReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "bookshelf"
                    ) {
                        composable("bookshelf") {
                            BookshelfScreen(navController = navController)
                        }
                        composable("reader/{bookId}") { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
                            ReaderScreen(
                                navController = navController,
                                bookId = bookId
                            )
                        }
                        composable("notes") {
                            NotesScreen(navController = navController)
                        }
                        composable("statistics") {
                            StatisticsScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}
