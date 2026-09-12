package space.liushenme.markdownreader.ui.screens.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R

@Composable
internal fun BookshelfRemoveConfirmDialog(
    selectedCount: Int,
    showDeleteCloudBackup: Boolean,
    onConfirm: (deleteCloudBackup: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteCloudBackup by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_remove_from_shelf_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dialog_remove_from_shelf_message, selectedCount))
                if (showDeleteCloudBackup) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteCloudBackup = !deleteCloudBackup }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteCloudBackup,
                            onCheckedChange = { deleteCloudBackup = it },
                        )
                        Text(
                            text = stringResource(R.string.dialog_remove_delete_cloud_backup),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteCloudBackup) }) {
                Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookshelfGroupDialog(
    groupInput: String,
    existingGroups: List<String>,
    onGroupInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_set_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existingGroups.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.dialog_group_existing_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        existingGroups.forEach { name ->
                            FilterChip(
                                selected = groupInput == name,
                                onClick = { onGroupInputChange(name) },
                                label = { Text(name) },
                            )
                        }
                        FilterChip(
                            selected = groupInput.isEmpty(),
                            onClick = { onGroupInputChange("") },
                            label = { Text(stringResource(R.string.dialog_group_ungroup)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = groupInput,
                    onValueChange = onGroupInputChange,
                    label = { Text(stringResource(R.string.dialog_group_name_label)) },
                    supportingText = { Text(stringResource(R.string.dialog_group_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun BookshelfImportMethodDialog(
    onLocalImport: () -> Unit,
    onUrlImport: () -> Unit,
    onGitHubImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_import_book_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.dialog_import_supported_formats),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onLocalImport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.dialog_import_from_local),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = onUrlImport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.dialog_import_from_url),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = onGitHubImport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.dialog_import_from_github),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun BookshelfUrlImportDialog(
    urlText: String,
    onUrlTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_url_import_title)) },
        text = {
            OutlinedTextField(
                value = urlText,
                onValueChange = onUrlTextChange,
                label = { Text(stringResource(R.string.dialog_url_import_label)) },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun BookshelfGitHubImportDialog(
    urlText: String,
    branchText: String,
    onUrlTextChange: (String) -> Unit,
    onBranchTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_github_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.dialog_github_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = onUrlTextChange,
                    label = { Text(stringResource(R.string.dialog_github_import_label)) },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = branchText,
                    onValueChange = onBranchTextChange,
                    label = { Text(stringResource(R.string.dialog_github_import_branch_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun BookshelfGitCloneProgressDialog(
    detail: String,
    percent: Int,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.dialog_github_cloning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (detail.isNotBlank()) {
                    Text(
                        stringResource(R.string.dialog_github_cloning_detail, detail),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (percent in 0..100) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
