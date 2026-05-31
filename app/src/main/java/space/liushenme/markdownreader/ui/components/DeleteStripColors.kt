package space.liushenme.markdownreader.ui.components

import androidx.compose.ui.graphics.Color

/** 删除条为 error 底时，保证图标在深浅底色上均可见。 */
fun iconTintForDeleteStrip(error: Color): Color {
    val l = error.red * 0.299f + error.green * 0.587f + error.blue * 0.114f
    return if (l > 0.55f) Color(0xFF1C1B1F) else Color.White
}
