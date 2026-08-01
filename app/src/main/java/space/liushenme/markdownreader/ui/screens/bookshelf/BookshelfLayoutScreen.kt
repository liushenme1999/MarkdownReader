package space.liushenme.markdownreader.ui.screens.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.model.BookshelfGridColumns
import space.liushenme.markdownreader.model.BookshelfLayoutMode
import space.liushenme.markdownreader.ui.components.ReaderSettingsChoiceOption
import space.liushenme.markdownreader.ui.components.ReaderSettingsGroupCard
import space.liushenme.markdownreader.ui.components.ReaderSettingsSectionTitle
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfLayoutScreen(
    navController: NavController,
    viewModel: BookshelfLayoutViewModel = hiltViewModel(),
) {
    val pageBg = shelfStylePageBackground()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = stringResource(R.string.bookshelf_layout_title),
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                ReaderSettingsSectionTitle(title = stringResource(R.string.bookshelf_layout_mode_section))
                ReaderSettingsGroupCard {
                    ReaderSettingsChoiceOption(
                        title = stringResource(R.string.bookshelf_layout_mode_grid),
                        icon = Icons.Default.GridView,
                        selected = layoutMode == BookshelfLayoutMode.Grid,
                        onClick = { viewModel.setLayoutMode(BookshelfLayoutMode.Grid) },
                        showDividerBelow = true,
                    )
                    ReaderSettingsChoiceOption(
                        title = stringResource(R.string.bookshelf_layout_mode_list),
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        selected = layoutMode == BookshelfLayoutMode.List,
                        onClick = { viewModel.setLayoutMode(BookshelfLayoutMode.List) },
                    )
                }
            }

            if (layoutMode == BookshelfLayoutMode.Grid) {
                Column {
                    ReaderSettingsSectionTitle(
                        title = stringResource(R.string.bookshelf_layout_columns_section),
                    )
                    ReaderSettingsGroupCard {
                        BookshelfGridColumns.OPTIONS.forEachIndexed { index, columns ->
                            ReaderSettingsChoiceOption(
                                title = stringResource(
                                    R.string.bookshelf_layout_columns_option,
                                    columns,
                                ),
                                icon = Icons.Default.ViewColumn,
                                selected = gridColumns == columns,
                                onClick = { viewModel.setGridColumns(columns) },
                                showDividerBelow = index < BookshelfGridColumns.OPTIONS.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}
