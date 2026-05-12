package com.example.markdownreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.markdownreader.ui.screens.bookshelf.BookshelfScreen
import com.example.markdownreader.ui.screens.notes.NotesScreen
import com.example.markdownreader.ui.screens.profile.ProfileScreen
import com.example.markdownreader.ui.screens.reader.ReaderScreen
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
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val showBottomBar = currentRoute == "bookshelf" || currentRoute == "profile"

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar {
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.MenuBook,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text("书架") },
                                        selected = currentRoute == "bookshelf",
                                        onClick = {
                                            navController.navigate("bookshelf") {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text("我的") },
                                        selected = currentRoute == "profile",
                                        onClick = {
                                            navController.navigate("profile") {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = "bookshelf",
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            composable("bookshelf") {
                                BookshelfScreen(navController = navController)
                            }
                            composable("profile") {
                                ProfileScreen(navController = navController)
                            }
                            composable("reader/{bookId}") { backStackEntry ->
                                val bookId =
                                    backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
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
}
