package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R

@Composable
internal fun ReaderLoadStateContent(
    state: ReaderLoadState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ReaderLoadState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        ReaderLoadState.Empty -> ReaderErrorContent(
            message = stringResource(R.string.reader_error_cannot_read_body),
            retryable = false,
            onRetry = onRetry,
            onBack = onBack,
            modifier = modifier,
        )
        ReaderLoadState.Ready -> Unit
        else -> ReaderErrorContent(
            message = state.loadMessage(),
            retryable = state is ReaderLoadState.NetworkRequired || state is ReaderLoadState.Failed,
            onRetry = onRetry,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun ReaderErrorContent(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (retryable) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.reader_retry))
                }
            }
            Button(onClick = onBack) {
                Text(stringResource(R.string.reader_back_to_bookshelf))
            }
        }
    }
}

private fun ReaderLoadState.loadMessage(): String = when (this) {
    is ReaderLoadState.MissingSource -> message
    is ReaderLoadState.BrokenBundle -> message
    is ReaderLoadState.NetworkRequired -> message
    is ReaderLoadState.UnsupportedFormat -> message
    is ReaderLoadState.PermissionDenied -> message
    is ReaderLoadState.Failed -> message
    ReaderLoadState.Loading -> ""
    ReaderLoadState.Empty -> ""
    ReaderLoadState.Ready -> ""
}
