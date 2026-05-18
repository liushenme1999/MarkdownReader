package com.example.markdownreader.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.markdownreader.model.AppThemeMode
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.components.AppThemeModeOption
import com.example.markdownreader.ui.components.ReaderPageTurnModeOption
import com.example.markdownreader.ui.components.ReaderSettingsGroupCard
import com.example.markdownreader.ui.components.ReaderSettingsHintBanner
import com.example.markdownreader.ui.components.ReaderSettingsSectionTitle
import com.example.markdownreader.ui.components.ReaderWideSliderRow
import com.example.markdownreader.ui.components.ReadingThemeGrid
import com.example.markdownreader.ui.components.ShelfStyleTopAppBar
import com.example.markdownreader.ui.components.ShelfStyleTopBarBackground
import com.example.markdownreader.ui.components.shelfStylePageBackground
import com.example.markdownreader.ui.theme.ReadingTheme
import kotlin.math.roundToInt

@Composable
fun ReadingSettingsScreen(
    navController: NavController,
    viewModel: ReadingSettingsViewModel = hiltViewModel(),
) {
    val pageBg = shelfStylePageBackground()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val readerPaddingDp by viewModel.readerPaddingDp.collectAsState()
    val lineSpacing by viewModel.readerLineSpacingMultiplier.collectAsState()
    val pageTurnMode by viewModel.pageTurnMode.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    val pageTurnModes = ReaderPageTurnMode.entries
    val appThemeModes = AppThemeMode.entries

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = "阅读设置",
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
            ReaderSettingsHintBanner(
                text = "以下设置将应用于全部书籍，与阅读页内快捷面板实时同步。",
            )

            Column {
                ReaderSettingsSectionTitle("外观主题")
                ReaderSettingsGroupCard {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        appThemeModes.forEachIndexed { index, mode ->
                            AppThemeModeOption(
                                mode = mode,
                                selected = mode == appThemeMode,
                                onClick = { viewModel.setAppThemeMode(mode) },
                                showDividerBelow = index < appThemeModes.lastIndex,
                            )
                        }
                    }
                }
            }

            Column {
                ReaderSettingsSectionTitle("阅读主题")
                ReaderSettingsGroupCard {
                    ReadingThemeGrid(
                        themes = ReadingTheme.allThemes(),
                        selected = currentTheme,
                        onSelect = viewModel::setTheme,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            Column {
                ReaderSettingsSectionTitle("字体与排版")
                ReaderSettingsGroupCard {
                    ReaderWideSliderRow(
                        label = "字体大小",
                        valueText = "${fontSize} sp",
                        value = fontSize.toFloat(),
                        onValueChange = { v ->
                            viewModel.setFontSize(v.roundToInt().coerceIn(10, 40))
                        },
                        valueRange = 10f..40f,
                        steps = 29,
                        showDividerBelow = true,
                    )
                    ReaderWideSliderRow(
                        label = "页边距",
                        valueText = "${readerPaddingDp} dp",
                        value = readerPaddingDp.toFloat(),
                        onValueChange = { v ->
                            viewModel.setReaderPaddingDp(v.roundToInt().coerceIn(8, 56))
                        },
                        valueRange = 8f..56f,
                        steps = 47,
                        showDividerBelow = true,
                    )
                    ReaderWideSliderRow(
                        label = "行距",
                        valueText = "%.2f 倍".format(lineSpacing),
                        value = lineSpacing,
                        onValueChange = viewModel::setReaderLineSpacingMultiplier,
                        valueRange = 1f..2.5f,
                        steps = 29,
                        showDividerBelow = false,
                    )
                }
            }

            Column {
                ReaderSettingsSectionTitle("翻页方式")
                ReaderSettingsGroupCard {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pageTurnModes.forEachIndexed { index, mode ->
                            ReaderPageTurnModeOption(
                                mode = mode,
                                selected = mode == pageTurnMode,
                                onClick = { viewModel.setPageTurnMode(mode) },
                                showDividerBelow = index < pageTurnModes.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}
