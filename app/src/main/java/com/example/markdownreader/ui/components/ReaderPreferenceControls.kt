package com.example.markdownreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.markdownreader.model.AppThemeMode
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.theme.MainNavTabSelectedTint
import com.example.markdownreader.ui.theme.ReadingTheme

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
                        isSelected = theme == selected,
                        onClick = { onSelect(theme) },
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
                        text = "预览",
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
            text = theme.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
        )
    }
}

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
            colors = SliderDefaults.colors(
                thumbColor = MainNavTabSelectedTint,
                activeTrackColor = MainNavTabSelectedTint,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
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
    hint: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
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
    val (icon, hint) = pageTurnModePresentation(mode)
    ReaderSettingsChoiceOption(
        title = mode.label,
        hint = hint,
        icon = icon,
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
        title = mode.label,
        hint = mode.hint,
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

private fun pageTurnModePresentation(mode: ReaderPageTurnMode): Pair<ImageVector, String> =
    when (mode) {
        ReaderPageTurnMode.VerticalScroll -> Icons.Default.SwapVert to "连续滚动，适合长文与 Markdown"
        ReaderPageTurnMode.HorizontalSwipe -> Icons.AutoMirrored.Filled.ViewList to "左右滑动翻页"
        ReaderPageTurnMode.SimulationPageTurn -> Icons.AutoMirrored.Filled.MenuBook to "仿真卷曲翻页效果"
        ReaderPageTurnMode.CoverPageTurn -> Icons.Default.ViewCarousel to "新页覆盖旧页"
    }
