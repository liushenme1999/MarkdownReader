package space.liushenme.markdownreader.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.model.AppLanguage
import space.liushenme.markdownreader.model.AppThemeMode
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.components.AppThemeModeOption
import space.liushenme.markdownreader.ui.components.ReaderPageTurnModeOption
import space.liushenme.markdownreader.ui.components.ReaderSettingsChoiceOption
import space.liushenme.markdownreader.ui.components.ReaderSettingsGroupCard
import space.liushenme.markdownreader.ui.components.ReaderSettingsHintBanner
import space.liushenme.markdownreader.ui.components.ReaderSettingsSectionTitle
import space.liushenme.markdownreader.ui.components.ReaderSettingsSwitchRow
import space.liushenme.markdownreader.ui.components.ReaderWideSliderRow
import space.liushenme.markdownreader.ui.components.ReadingStyleSettingsBlock
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import kotlin.math.roundToInt

@Composable
fun ReadingSettingsScreen(
    navController: NavController,
    viewModel: ReadingSettingsViewModel = hiltViewModel(),
) {
    val pageBg = shelfStylePageBackground()
    val styleState by viewModel.readingStyleState.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val readerPaddingDp by viewModel.readerPaddingDp.collectAsState()
    val lineSpacing by viewModel.readerLineSpacingMultiplier.collectAsState()
    val codeBlockWrap by viewModel.codeBlockWrap.collectAsState()
    val hideSystemBars by viewModel.hideSystemBars.collectAsState()
    val pageTurnMode by viewModel.pageTurnMode.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val pageTurnModes = ReaderPageTurnMode.selectableModes
    val appThemeModes = AppThemeMode.entries
    val appLanguages = AppLanguage.entries

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = stringResource(R.string.reading_settings_title),
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
                text = stringResource(R.string.reading_settings_sync_hint),
            )

            Column {
                ReaderSettingsSectionTitle(stringResource(R.string.reading_settings_section_language))
                ReaderSettingsGroupCard {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        appLanguages.forEachIndexed { index, language ->
                            ReaderSettingsChoiceOption(
                                title = stringResource(language.labelRes),
                                icon = Icons.Default.Language,
                                selected = language == appLanguage,
                                onClick = { viewModel.setAppLanguage(language) },
                                showDividerBelow = index < appLanguages.lastIndex,
                            )
                        }
                    }
                }
            }

            Column {
                ReaderSettingsSectionTitle(stringResource(R.string.reading_settings_section_appearance))
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
                ReaderSettingsSectionTitle(stringResource(R.string.reading_settings_section_reading_theme))
                ReaderSettingsGroupCard {
                    ReadingStyleSettingsBlock(
                        styleState = styleState,
                        onSelect = viewModel::selectReadingStyle,
                        onAdd = viewModel::addReadingStyle,
                        onUpdate = viewModel::updateReadingStyle,
                        onDelete = viewModel::deleteReadingStyle,
                        onResetAll = viewModel::resetAllReadingStyles,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            Column {
                ReaderSettingsSectionTitle(stringResource(R.string.reading_settings_section_typography))
                ReaderSettingsGroupCard {
                    ReaderWideSliderRow(
                        label = stringResource(R.string.reader_font_size),
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
                        label = stringResource(R.string.reader_page_margin),
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
                        label = stringResource(R.string.reader_line_spacing),
                        valueText = stringResource(R.string.reader_line_spacing_value, lineSpacing),
                        value = lineSpacing,
                        onValueChange = viewModel::setReaderLineSpacingMultiplier,
                        valueRange = 1f..2.5f,
                        steps = 29,
                        showDividerBelow = true,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.reader_code_block_wrap),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.reader_code_block_wrap_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                        Switch(
                            checked = codeBlockWrap,
                            onCheckedChange = viewModel::setCodeBlockWrap,
                        )
                    }
                }
            }

            Column {
                ReaderSettingsSectionTitle(stringResource(R.string.reading_settings_section_display))
                ReaderSettingsGroupCard {
                    ReaderSettingsSwitchRow(
                        title = stringResource(R.string.reader_hide_system_bars),
                        summary = stringResource(R.string.reader_hide_system_bars_summary),
                        checked = hideSystemBars,
                        onCheckedChange = viewModel::setHideSystemBars,
                    )
                }
            }

            Column {
                ReaderSettingsSectionTitle(stringResource(R.string.reading_settings_section_page_turn))
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
