package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import space.liushenme.markdownreader.R
import kotlin.math.min

/** Dialogs and sheets rendered above the document and reader chrome. */
@Composable
internal fun ReaderOverlays(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    diagramPreviewBitmap: Bitmap?,
    showReaderSettings: Boolean,
    onDismissDiagramPreview: () -> Unit,
    onDismissReaderSettings: () -> Unit,
) {
    if (uiState.showMarkFinishedPrompt) {
        ReaderMarkFinishedDialog(
            onConfirm = { viewModel.dispatch(ReaderEvent.MarkFinished) },
            onDismiss = { viewModel.dispatch(ReaderEvent.DismissFinishPrompt) },
        )
    }

    diagramPreviewBitmap?.let { bitmap ->
        DiagramPreviewDialog(
            bitmap = bitmap,
            onDismiss = onDismissDiagramPreview,
        )
    }

    if (showReaderSettings) {
        ReaderSettingsSheet(
            styleState = uiState.readingStyleState,
            fontSize = uiState.fontSize,
            readerPaddingDp = uiState.readerPaddingDp,
            readerLineSpacingMultiplier = uiState.readerLineSpacingMultiplier,
            codeBlockWrap = uiState.codeBlockWrap,
            hideSystemBars = uiState.hideSystemBars,
            pageTurnMode = uiState.pageTurnMode,
            onSelectStyle = viewModel::selectReadingStyle,
            onAddStyle = viewModel::addReadingStyle,
            onUpdateStyle = viewModel::updateReadingStyle,
            onDeleteStyle = viewModel::deleteReadingStyle,
            onResetAllStyles = viewModel::resetAllReadingStyles,
            onFontSizeChange = viewModel::setFontSize,
            onPaddingDpChange = viewModel::setReaderPaddingDp,
            onLineSpacingChange = viewModel::setReaderLineSpacingMultiplier,
            onCodeBlockWrapChange = viewModel::setCodeBlockWrap,
            onHideSystemBarsChange = viewModel::setHideSystemBars,
            onPageTurnModeChange = viewModel::setPageTurnMode,
            onDismiss = onDismissReaderSettings,
        )
    }
}

@Composable
private fun ReaderMarkFinishedDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_mark_finished_title)) },
        text = { Text(stringResource(R.string.reader_mark_finished_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.reader_mark_finished_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_mark_finished_later))
            }
        },
    )
}

@Composable
private fun DiagramPreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offsetX by remember(bitmap) { mutableFloatStateOf(0f) }
    var offsetY by remember(bitmap) { mutableFloatStateOf(0f) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val glassTint = Color(0xFF20242B).copy(alpha = 0.72f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = LocalView.current
        val blurRadiusPx = with(LocalDensity.current) { 56.dp.roundToPx() }
        DisposableEffect(dialogView, blurRadiusPx) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    window?.let { dialogWindow ->
                        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        dialogWindow.attributes = dialogWindow.attributes.apply {
                            setBlurBehindRadius(blurRadiusPx)
                        }
                        dialogWindow.setBackgroundBlurRadius(blurRadiusPx)
                    }
                }
            }
            onDispose {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window?.setBackgroundBlurRadius(0)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glassTint)
                .pointerInput(bitmap, scale, offsetX, offsetY) {
                    detectTapGestures { tap ->
                        val imageW = bitmap.width.toFloat().coerceAtLeast(1f)
                        val imageH = bitmap.height.toFloat().coerceAtLeast(1f)
                        val fitScale = min(size.width / imageW, size.height / imageH)
                        val drawnW = imageW * fitScale * scale
                        val drawnH = imageH * fitScale * scale
                        val centerX = size.width / 2f + offsetX
                        val centerY = size.height / 2f + offsetY
                        val insideImage =
                            tap.x in (centerX - drawnW / 2f)..(centerX + drawnW / 2f) &&
                                tap.y in (centerY - drawnH / 2f)..(centerY + drawnH / 2f)
                        if (!insideImage) onDismiss()
                    }
                },
        ) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.reader_diagram_preview_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 8f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )

            Text(
                text = stringResource(R.string.reader_diagram_zoom_hint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.reader_diagram_close_cd),
                    tint = Color.White,
                )
            }
        }
    }
}
