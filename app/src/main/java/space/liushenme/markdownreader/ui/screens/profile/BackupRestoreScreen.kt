package space.liushenme.markdownreader.ui.screens.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.navigation.AppRoutes
import space.liushenme.markdownreader.ui.components.ReaderSettingsGroupCard
import space.liushenme.markdownreader.ui.components.ReaderSettingsHintBanner
import space.liushenme.markdownreader.ui.components.ReaderSettingsSectionTitle
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

@Composable
fun BackupRestoreScreen(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val pageBg = shelfStylePageBackground()
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val events by viewModel.events.collectAsState()
    val restoreCandidates by viewModel.restoreCandidates.collectAsState()

    var url by remember(config.url) { mutableStateOf(config.url) }
    var account by remember(config.account) { mutableStateOf(config.account) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var dir by remember(config.dir) { mutableStateOf(config.dir) }
    var deviceName by remember(config.deviceName) { mutableStateOf(config.deviceName) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        val e = events ?: return@LaunchedEffect
        val text = when (e) {
            is BackupUiEvent.BackupSuccess ->
                context.getString(R.string.backup_toast_backup_success, e.fileName)
            is BackupUiEvent.BackupFailed ->
                context.getString(R.string.backup_toast_backup_failed, e.detail)
            BackupUiEvent.RestoreListEmpty ->
                context.getString(R.string.backup_toast_restore_list_empty)
            is BackupUiEvent.RestoreListFailed ->
                context.getString(R.string.backup_toast_restore_list_failed, e.detail)
            BackupUiEvent.RestoreSuccess ->
                context.getString(R.string.backup_toast_restore_success)
            is BackupUiEvent.RestoreFailed ->
                context.getString(R.string.backup_toast_restore_failed, e.detail)
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.clearEvent()
    }

    if (restoreCandidates != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) viewModel.dismissRestoreDialog() },
            title = { Text(stringResource(R.string.backup_restore_pick_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    restoreCandidates!!.forEach { name ->
                        Text(
                            text = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) { viewModel.restore(name) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissRestoreDialog() }, enabled = !busy) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = stringResource(R.string.backup_restore_title),
                    onNavigateBack = { navController.navigateUp() },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate(AppRoutes.BACKUP_HELP) },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.backup_help_cd),
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ReaderSettingsHintBanner(
                    text = stringResource(R.string.backup_restore_hint),
                )

                Column {
                    ReaderSettingsSectionTitle(
                        title = stringResource(R.string.backup_webdav_section),
                    )
                    ReaderSettingsGroupCard {
                        BackupTextField(
                            label = stringResource(R.string.backup_webdav_url),
                            value = url,
                            onValueChange = { url = it },
                            onFocusLostOrDone = { viewModel.setUrl(url) },
                            enabled = !busy,
                            singleLine = true,
                        )
                        BackupTextField(
                            label = stringResource(R.string.backup_webdav_account),
                            value = account,
                            onValueChange = { account = it },
                            onFocusLostOrDone = { viewModel.setAccount(account) },
                            enabled = !busy,
                            singleLine = true,
                        )
                        BackupTextField(
                            label = stringResource(R.string.backup_webdav_password),
                            value = password,
                            onValueChange = { password = it },
                            onFocusLostOrDone = { viewModel.setPassword(password) },
                            enabled = !busy,
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardType = KeyboardType.Password,
                            trailing = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(
                                        if (passwordVisible) {
                                            stringResource(R.string.backup_password_hide)
                                        } else {
                                            stringResource(R.string.backup_password_show)
                                        },
                                    )
                                }
                            },
                        )
                        BackupTextField(
                            label = stringResource(R.string.backup_webdav_dir),
                            value = dir,
                            onValueChange = { dir = it },
                            onFocusLostOrDone = { viewModel.setDir(dir) },
                            enabled = !busy,
                            singleLine = true,
                        )
                        BackupTextField(
                            label = stringResource(R.string.backup_webdav_device_name),
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            onFocusLostOrDone = { viewModel.setDeviceName(deviceName) },
                            enabled = !busy,
                            singleLine = true,
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.backup_only_latest_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.backup_only_latest_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                )
                            }
                            Switch(
                                checked = config.onlyLatestBackup,
                                onCheckedChange = { viewModel.setOnlyLatestBackup(it) },
                                enabled = !busy,
                            )
                        }
                    }
                }

                Column {
                    ReaderSettingsSectionTitle(
                        title = stringResource(R.string.backup_actions_section),
                    )
                    ReaderSettingsGroupCard {
                        BackupActionRow(
                            title = stringResource(R.string.backup_action_backup),
                            subtitle = stringResource(R.string.backup_action_backup_summary),
                            enabled = !busy,
                            onClick = {
                                viewModel.backupNow(url, account, password, dir, deviceName)
                            },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        BackupActionRow(
                            title = stringResource(R.string.backup_action_restore),
                            subtitle = stringResource(R.string.backup_action_restore_summary),
                            enabled = !busy,
                            onClick = {
                                viewModel.prepareRestore(url, account, password, dir, deviceName)
                            },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        BackupActionRow(
                            title = stringResource(R.string.backup_action_help),
                            subtitle = stringResource(R.string.backup_action_help_summary),
                            enabled = !busy,
                            onClick = { navController.navigate(AppRoutes.BACKUP_HELP) },
                        )
                    }
                }
            }

            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun BackupTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLostOrDone: () -> Unit,
    enabled: Boolean,
    singleLine: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailing,
    )
    LaunchedEffect(value) {
        delay(400)
        onFocusLostOrDone()
    }
}

@Composable
private fun BackupActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}
