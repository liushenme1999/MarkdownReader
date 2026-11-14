package space.liushenme.markdownreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.markdown.CodeHighlightTheme
import space.liushenme.markdownreader.model.AppThemeMode
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.theme.MainNavTabSelectedTint
import space.liushenme.markdownreader.ui.theme.ReadingTheme

private val SettingsCardShape = RoundedCornerShape(16.dp)
private val SettingsItemShape = RoundedCornerShape(12.dp)

@Composable
fun ReaderSettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            content()
        }
    }
}

@Composable
fun ReaderSettingsSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
    )
}

@Composable
fun ReaderSettingsHintBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SettingsItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
        )
    }
}

@Composable
fun ReaderSettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun ReadingThemeGrid(
    themes: List<ReadingTheme>,
    selected: ReadingTheme,
    onSelect: (ReadingTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        themes.chunked(3).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowThemes.forEach { theme ->
                    ReadingThemeCardOption(
                        theme = theme,
                        isSelected = selected.matchesPreset(theme),
                        onClick = { onSelect(theme.asQuickPreset()) },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                }
                repeat(3 - rowThemes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ReadingThemeCardOption(
    theme: ReadingTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val accent = MainNavTabSelectedTint
    val borderColor = if (isSelected) accent else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val borderWidth = if (isSelected) 2.dp else 0.5.dp
    val previewShape = RoundedCornerShape(if (compact) 10.dp else 12.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(previewShape)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (compact) 1.15f else 1f)
                .clip(previewShape)
                .background(theme.backgroundColor)
                .border(borderWidth, borderColor, previewShape),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Aa",
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = theme.textColor,
                    fontWeight = FontWeight.Bold,
                )
                if (!compact) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.reading_theme_preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.secondaryTextColor,
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = theme.nameRes?.let { stringResource(it) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
        )
    }
}

@Composable
fun CodeHighlightThemeGrid(
    themes: List<CodeHighlightTheme>,
    selected: CodeHighlightTheme,
    onSelect: (CodeHighlightTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单行横向滑动：卡片更小，一屏可瞥见多个主题，左右滑动选择。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        themes.forEach { theme ->
            CodeHighlightThemeCardOption(
                theme = theme,
                isSelected = theme == selected,
                onClick = { onSelect(theme) },
                modifier = Modifier.width(ThemeCardWidth),
            )
        }
    }
}

@Composable
fun CodeHighlightThemeCardOption(
    theme: CodeHighlightTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = theme.palette
    val accent = MainNavTabSelectedTint
    val borderColor = if (isSelected) {
        accent
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val previewShape = RoundedCornerShape(12.dp)
    val previewStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
    )

    Column(
        modifier = modifier
            .clip(previewShape)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(previewShape)
                .background(Color(palette.background))
                .border(if (isSelected) 2.dp else 0.5.dp, borderColor, previewShape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row {
                    Text("fun", style = previewStyle, color = Color(palette.keyword), maxLines = 1)
                    Text(" ", style = previewStyle, color = Color(palette.foreground), maxLines = 1)
                    Text("main", style = previewStyle, color = Color(palette.function), maxLines = 1)
                    Text("() {", style = previewStyle, color = Color(palette.foreground), maxLines = 1)
                }
                Text(
                    text = "// ${theme.displayName}",
                    style = previewStyle,
                    color = Color(palette.comment),
                    maxLines = 1,
                )
                Row {
                    Text("return", style = previewStyle, color = Color(palette.keyword), maxLines = 1)
                    Text(" ", style = previewStyle, color = Color(palette.foreground), maxLines = 1)
                    Text("\"ok\"", style = previewStyle, color = Color(palette.string), maxLines = 1)
                    Text(" + 1", style = previewStyle, color = Color(palette.number), maxLines = 1)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        palette.comment,
                        palette.string,
                        palette.keyword,
                        palette.function,
                        palette.type,
                        palette.number,
                    ).forEach { tokenColor ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(tokenColor)),
                        )
                    }
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderWideSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    showDividerBelow: Boolean = false,
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = MainNavTabSelectedTint,
        activeTrackColor = MainNavTabSelectedTint,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MainNavTabSelectedTint.copy(alpha = 0.12f),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MainNavTabSelectedTint,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = sliderColors,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(10.dp),
                    colors = sliderColors,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
        )
        if (showDividerBelow) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsChoiceOption(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    hint: String = "",
    showDividerBelow: Boolean = false,
) {
    val accent = MainNavTabSelectedTint

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsItemShape,
            color = if (selected) {
                accent.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = if (selected) {
                BorderStroke(1.dp, accent.copy(alpha = 0.45f))
            } else {
                null
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) {
                        accent.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) accent else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (hint.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (showDividerBelow) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPageTurnModeOption(
    mode: ReaderPageTurnMode,
    selected: Boolean,
    onClick: () -> Unit,
    showDividerBelow: Boolean = false,
) {
    ReaderSettingsChoiceOption(
        title = stringResource(mode.labelRes),
        hint = stringResource(mode.hintRes),
        icon = pageTurnModeIcon(mode),
        selected = selected,
        onClick = onClick,
        showDividerBelow = showDividerBelow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppThemeModeOption(
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    showDividerBelow: Boolean = false,
) {
    ReaderSettingsChoiceOption(
        title = stringResource(mode.labelRes),
        hint = stringResource(mode.hintRes),
        icon = appThemeModeIcon(mode),
        selected = selected,
        onClick = onClick,
        showDividerBelow = showDividerBelow,
    )
}

private fun appThemeModeIcon(mode: AppThemeMode): ImageVector = when (mode) {
    AppThemeMode.SYSTEM -> Icons.Default.PhoneAndroid
    AppThemeMode.LIGHT -> Icons.Default.LightMode
    AppThemeMode.DARK -> Icons.Default.DarkMode
}

private fun pageTurnModeIcon(mode: ReaderPageTurnMode): ImageVector = when (mode) {
    ReaderPageTurnMode.VerticalScroll -> Icons.Default.SwapVert
    ReaderPageTurnMode.HorizontalSwipe -> Icons.AutoMirrored.Filled.ViewList
    ReaderPageTurnMode.SimulationPageTurn -> Icons.AutoMirrored.Filled.MenuBook
    ReaderPageTurnMode.CoverPageTurn -> Icons.Default.ViewCarousel
}

/** 代码高亮主题卡片宽度（横向滑动列表）。 */
private val ThemeCardWidth = 148.dp
