package space.liushenme.markdownreader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.theme.MainNavTabSelectedTint
import space.liushenme.markdownreader.ui.theme.ReaderBackgroundImages
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import space.liushenme.markdownreader.ui.theme.ReadingThemeStorage

private enum class ColorPickerTarget { Text, Background }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStyleEditSheet(
    theme: ReadingTheme,
    canDelete: Boolean,
    onThemeChange: (ReadingTheme) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val resolvedName = theme.resolvedName()
    var nameInput by remember(resolvedName) { mutableStateOf(resolvedName) }
    var textHex by remember { mutableStateOf(theme.textColor.toHexRgb()) }
    var bgHex by remember { mutableStateOf(theme.backgroundColor.toHexRgb()) }

    LaunchedEffect(theme.textColor) {
        val parsed = ReadingThemeStorage.parseHexColor(textHex)
        if (parsed == null || parsed.toArgb() != theme.textColor.toArgb()) {
            textHex = theme.textColor.toHexRgb()
        }
    }
    LaunchedEffect(theme.backgroundColor) {
        val parsed = ReadingThemeStorage.parseHexColor(bgHex)
        if (parsed == null || parsed.toArgb() != theme.backgroundColor.toArgb()) {
            bgHex = theme.backgroundColor.toHexRgb()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_style_edit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    onThemeChange(theme.copy(name = it))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reader_style_name)) },
                singleLine = true,
            )
            StyleColorRow(
                label = stringResource(R.string.reader_text_color),
                color = theme.textColor,
                hexValue = textHex,
                onHexChange = { raw ->
                    textHex = raw
                    ReadingThemeStorage.parseHexColor(raw)?.let { parsed ->
                        onThemeChange(theme.copy(textColor = parsed.copy(alpha = 1f)))
                    }
                },
                onSwatchClick = { pickerTarget = ColorPickerTarget.Text },
            )
            StyleColorRow(
                label = stringResource(R.string.reader_bg_color),
                color = theme.backgroundColor,
                hexValue = bgHex,
                onHexChange = { raw ->
                    bgHex = raw
                    ReadingThemeStorage.parseHexColor(raw)?.let { parsed ->
                        onThemeChange(
                            theme.copy(
                                backgroundColor = parsed.copy(alpha = 1f),
                                backgroundImageAsset = null,
                            ),
                        )
                    }
                },
                onSwatchClick = { pickerTarget = ColorPickerTarget.Background },
            )
            ReaderWideSliderRow(
                label = stringResource(R.string.reader_bg_alpha),
                valueText = stringResource(R.string.reader_bg_alpha_value, theme.backgroundAlpha),
                value = theme.backgroundAlpha.toFloat(),
                onValueChange = {
                    onThemeChange(
                        theme.copy(backgroundAlpha = ReadingThemeStorage.clampAlpha(it.toInt())),
                    )
                },
                valueRange = 0f..100f,
                steps = 99,
            )
            Text(
                text = stringResource(R.string.reader_bg_image),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            BackgroundImageRow(
                selectedAsset = theme.backgroundImageAsset,
                onSelectNone = { onThemeChange(theme.copy(backgroundImageAsset = null)) },
                onSelectAsset = { onThemeChange(theme.copy(backgroundImageAsset = it)) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canDelete) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.reader_style_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        onThemeChange(ReadingThemeStorage.initialStyle(theme))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = stringResource(R.string.reader_style_reset),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.reader_style_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.reader_style_delete_confirm_message,
                        theme.resolvedName(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    when (pickerTarget) {
        ColorPickerTarget.Text -> ReaderColorPickerDialog(
            title = stringResource(R.string.reader_text_color),
            initialColor = theme.textColor,
            onConfirm = { color ->
                onThemeChange(theme.copy(textColor = color.copy(alpha = 1f)))
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
        ColorPickerTarget.Background -> ReaderColorPickerDialog(
            title = stringResource(R.string.reader_bg_color),
            initialColor = theme.backgroundColor,
            onConfirm = { color ->
                onThemeChange(
                    theme.copy(
                        backgroundColor = color.copy(alpha = 1f),
                        backgroundImageAsset = null,
                    ),
                )
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
        null -> Unit
    }
}

@Composable
private fun StyleColorRow(
    label: String,
    color: Color,
    hexValue: String,
    onHexChange: (String) -> Unit,
    onSwatchClick: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                    .clickable(onClick = onSwatchClick),
            )
            OutlinedTextField(
                value = hexValue,
                onValueChange = onHexChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.reader_color_hex)) },
                placeholder = { Text(stringResource(R.string.reader_color_hex_hint)) },
                singleLine = true,
                shape = shape,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboard?.hide() },
                ),
            )
        }
    }
}

@Composable
internal fun BackgroundImageRow(
    selectedAsset: String?,
    onSelectNone: () -> Unit,
    onSelectAsset: (String) -> Unit,
) {
    val context = LocalContext.current
    val accent = MainNavTabSelectedTint
    val itemShape = RoundedCornerShape(10.dp)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        item(key = "none") {
            BackgroundImageThumb(
                selected = selectedAsset.isNullOrBlank(),
                accent = accent,
                shape = itemShape,
                onClick = onSelectNone,
                label = stringResource(R.string.reader_bg_image_none),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        items(ReaderBackgroundImages.defaultAssets, key = { it }) { asset ->
            val thumb = remember(asset) {
                ReaderBackgroundImages.loadThumbnail(context, asset)?.asImageBitmap()
            }
            BackgroundImageThumb(
                selected = selectedAsset == asset,
                accent = accent,
                shape = itemShape,
                onClick = { onSelectAsset(asset) },
                label = ReaderBackgroundImages.displayName(asset),
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = ReaderBackgroundImages.displayName(asset),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundImageThumb(
    selected: Boolean,
    accent: Color,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    label: String,
    preview: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp, 64.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    width = if (selected) 2.dp else 0.5.dp,
                    color = if (selected) {
                        accent
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    },
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            preview()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
