package space.liushenme.markdownreader.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import space.liushenme.markdownreader.BuildConfig
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val pageBg = shelfStylePageBackground()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val checking by viewModel.checking.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val alreadyLatest = stringResource(R.string.about_update_already_latest)
    val checkFailed = stringResource(R.string.about_update_check_failed)

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { message ->
            val text = when (message) {
                AboutUpdateMessage.AlreadyLatest -> alreadyLatest
                AboutUpdateMessage.CheckFailed -> checkFailed
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    val pending = availableUpdate
    if (showUpdateDialog && pending != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.about_update_found_title)) },
            text = {
                Text(stringResource(R.string.about_update_found_message, pending.versionName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(pending.pageUrl)),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.about_update_go_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = pageBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = stringResource(R.string.profile_menu_about),
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // mipmap/ic_launcher 在 API 26+ 是 AdaptiveIcon，painterResource 无法直接加载会崩溃
                AppLauncherIcon(
                    size = 88.dp,
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(22.dp),
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f),
                        )
                        .clip(RoundedCornerShape(22.dp)),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (checking && availableUpdate == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        val hasUpdate = availableUpdate != null
                        val actionLabel = stringResource(
                            if (hasUpdate) {
                                R.string.about_update_version_cd
                            } else {
                                R.string.about_check_update_cd
                            },
                        )
                        TextButton(
                            onClick = { viewModel.onUpdateActionClick() },
                            modifier = Modifier.semantics { contentDescription = actionLabel },
                        ) {
                            Text(
                                text = stringResource(
                                    if (hasUpdate) {
                                        R.string.about_update_version
                                    } else {
                                        R.string.about_check_update
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.about_supported_formats),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.about_format_md).trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Text(
                            text = stringResource(R.string.about_format_txt).trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Text(
                            text = stringResource(R.string.about_format_pdf).trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.copyright_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                )
                IcpFilingLink(
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun AppLauncherIcon(
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx().coerceAtLeast(1) }
    val bitmap = remember(context.packageName, px) {
        context.packageManager
            .getApplicationIcon(context.applicationInfo)
            .toBitmap(width = px, height = px)
            .asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/**
 * APP ICP 备案号：显著展示，并跳转工信部备案管理系统供查询核对。
 */
@Composable
internal fun IcpFilingLink(
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val filingNumber = stringResource(R.string.icp_filing_number)
    val displayText = stringResource(R.string.icp_filing_display, filingNumber)
    val beianUrl = stringResource(R.string.icp_beian_url)
    Text(
        text = displayText,
        style = style.copy(textDecoration = TextDecoration.Underline),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(
                onClickLabel = stringResource(R.string.icp_filing_hint),
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(beianUrl)),
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
