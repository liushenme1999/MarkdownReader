package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R

/** Load-state boundary around the actual Markdown/PDF rendering host. */
@Composable
internal fun ReaderContent(
    loadState: ReaderLoadState,
    hasContent: Boolean,
    isPdf: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val failed = loadState is ReaderLoadState.MissingSource ||
        loadState is ReaderLoadState.BrokenBundle ||
        loadState is ReaderLoadState.NetworkRequired ||
        loadState is ReaderLoadState.UnsupportedFormat ||
        loadState is ReaderLoadState.PermissionDenied ||
        loadState is ReaderLoadState.Failed
    when {
        failed -> ReaderLoadStateContent(
            state = loadState,
            onRetry = onRetry,
            onBack = onBack,
            modifier = modifier.fillMaxSize(),
        )
        hasContent -> content()
        else -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            if (isPdf) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.reader_opening_pdf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}
