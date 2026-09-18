package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

internal const val PDF_DARK_PAGE_LUMINANCE_THRESHOLD = 0.5f

internal val PDF_PAGE_INVERT_MATRIX_VALUES = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

internal fun shouldInvertPdfPages(backgroundLuminance: Float): Boolean =
    backgroundLuminance < PDF_DARK_PAGE_LUMINANCE_THRESHOLD

internal fun pdfPageInvertColorMatrix(): ColorMatrix = ColorMatrix(PDF_PAGE_INVERT_MATRIX_VALUES)

internal fun pdfPageInvertColorFilter(): ColorMatrixColorFilter =
    ColorMatrixColorFilter(pdfPageInvertColorMatrix())
