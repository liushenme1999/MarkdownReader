package space.liushenme.markdownreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import space.liushenme.markdownreader.ui.theme.ReadingThemeStorage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.theme.MainNavTabSelectedTint
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReaderColorPickerDialog(
    title: String,
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember(initialColor) { initialColor.toHsv() }
    var hue by remember { mutableFloatStateOf(start.first) }
    var saturation by remember { mutableFloatStateOf(start.second) }
    var value by remember { mutableFloatStateOf(start.third) }
    val current = hsvToColor(hue, saturation, value)
    var hexInput by remember { mutableStateOf(initialColor.toHexRgb()) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun applyColor(color: Color, updateHex: Boolean) {
        val hsv = color.toHsv()
        hue = hsv.first
        saturation = hsv.second
        value = hsv.third
        if (updateHex) hexInput = color.toHexRgb()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.15f)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(hue) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val apply = { pos: Offset ->
                                    saturation = (pos.x / size.width).coerceIn(0f, 1f)
                                    value = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                                    hexInput = hsvToColor(hue, saturation, value).toHexRgb()
                                }
                                apply(down.position)
                                drag(down.id) { change ->
                                    change.consume()
                                    apply(change.position)
                                }
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(hsvToColor(hue, 1f, 1f))
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.White, Color.Transparent),
                            ),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black),
                            ),
                        )
                        val cx = saturation * size.width
                        val cy = (1f - value) * size.height
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val apply = { pos: Offset ->
                                    hue = (pos.x / size.width).coerceIn(0f, 1f) * 360f
                                    hexInput = hsvToColor(hue, saturation, value).toHexRgb()
                                }
                                apply(down.position)
                                drag(down.id) { change ->
                                    change.consume()
                                    apply(change.position)
                                }
                            }
                        },
                ) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(hueColors()),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    val x = (hue / 360f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = size.height / 2f,
                        center = Offset(x, size.height / 2f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(current)
                            .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = raw
                            ReadingThemeStorage.parseHexColor(raw)?.let { parsed ->
                                applyColor(parsed, updateHex = false)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.reader_color_hex)) },
                        placeholder = { Text(stringResource(R.string.reader_color_hex_hint)) },
                        singleLine = true,
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
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.copy(alpha = 1f)) }) {
                Text(
                    text = stringResource(R.string.action_confirm),
                    color = MainNavTabSelectedTint,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

internal fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

internal fun Color.toHsv(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val sat = if (max == 0f) 0f else delta / max
    return Triple(hue, sat, max)
}

internal fun Color.toHexRgb(): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

private fun hueColors(): List<Color> = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)
