package space.liushenme.markdownreader.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.theme.MainNavTabSelectedTint
import space.liushenme.markdownreader.ui.theme.ReaderBackgroundImages
import space.liushenme.markdownreader.ui.theme.ReadingStyleState
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun ReadingTheme.resolvedName(): String =
    name.trim().takeIf { it.isNotEmpty() }
        ?: nameRes?.let { stringResource(it) }
        ?: stringResource(R.string.reader_style_default_name)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadingColorBackgroundPanel(
    styleState: ReadingStyleState,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val accent = MainNavTabSelectedTint
    val cardShape = RoundedCornerShape(12.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reader_style_long_press_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(styleState.styles, key = { index, theme ->
                "${index}_${theme.presetKey}_${theme.name}_${theme.contentSignature()}"
            }) { index, theme ->
                val selected = index == styleState.selectedIndex
                val thumb = remember(theme.backgroundImageAsset) {
                    theme.backgroundImageAsset?.let {
                        ReaderBackgroundImages.loadThumbnail(context, it)?.asImageBitmap()
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(cardShape)
                            .combinedClickable(
                                onClick = { onSelect(index) },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onEdit(index)
                                },
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    accent
                                } else {
                                    theme.textColor.copy(alpha = 0.35f)
                                },
                                shape = cardShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(theme.backgroundColor),
                        )
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = theme.backgroundAlpha / 100f,
                            )
                        }
                        Text(
                            text = "Aa",
                            color = theme.textColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                    Text(
                        text = theme.resolvedName(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            accent
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item(key = "add") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(cardShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = cardShape,
                            )
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.reader_style_add),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    Text(
                        text = stringResource(R.string.reader_style_add),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item(key = "reset_all") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(cardShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = cardShape,
                            )
                            .clickable(onClick = onResetAll),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.reader_style_reset_all),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    Text(
                        text = stringResource(R.string.reader_style_reset_all),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingStyleSettingsBlock(
    styleState: ReadingStyleState,
    onSelect: (Int) -> Unit,
    onAdd: ((Int) -> Unit) -> Unit,
    onUpdate: (Int, ReadingTheme) -> Unit,
    onDelete: (Int) -> Unit,
    onResetAll: (keepCustom: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    var keepCustomStyles by remember { mutableStateOf(true) }
    ReadingColorBackgroundPanel(
        styleState = styleState,
        onSelect = onSelect,
        onAdd = { onAdd { editingIndex = it } },
        onEdit = { index ->
            onSelect(index)
            editingIndex = index
        },
        onResetAll = {
            keepCustomStyles = true
            showResetAllConfirm = true
        },
        modifier = modifier,
    )
    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirm = false },
            title = { Text(stringResource(R.string.reader_style_reset_all_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.reader_style_reset_all_message))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keepCustomStyles = !keepCustomStyles },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = keepCustomStyles,
                            onCheckedChange = { keepCustomStyles = it },
                        )
                        Text(
                            text = stringResource(R.string.reader_style_reset_all_keep_custom),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllConfirm = false
                        editingIndex = null
                        onResetAll(keepCustomStyles)
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    val index = editingIndex
    if (index != null) {
        val theme = styleState.styles.getOrNull(index)
        if (theme != null) {
            ReadingStyleEditSheet(
                theme = theme,
                canDelete = styleState.styles.size > 1,
                onThemeChange = { onUpdate(index, it) },
                onDelete = {
                    onDelete(index)
                    editingIndex = null
                },
                onDismiss = { editingIndex = null },
            )
        } else {
            editingIndex = null
        }
    }
}
