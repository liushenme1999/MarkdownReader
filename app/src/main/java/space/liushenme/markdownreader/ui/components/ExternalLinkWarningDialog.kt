package space.liushenme.markdownreader.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R

/** Confirms navigation to an external web page from rendered Markdown. */
@Composable
fun ExternalLinkWarningDialog(
    url: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_link_warning_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.external_link_warning_message))
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.external_link_warning_url_label))
                Text(
                    text = url,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? ClipboardManager
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
                            Toast.makeText(
                                context,
                                context.getString(R.string.external_link_warning_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.external_link_warning_copy))
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                ) {
                    Text(stringResource(R.string.external_link_warning_continue))
                }
            }
        },
        dismissButton = null,
    )
}
