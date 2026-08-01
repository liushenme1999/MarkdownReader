package space.liushenme.markdownreader.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

@Composable
fun BackupHelpScreen(
    navController: NavController,
) {
    val pageBg = shelfStylePageBackground()

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = stringResource(R.string.backup_help_title),
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            HelpSection(
                title = stringResource(R.string.backup_help_intro_title),
                body = stringResource(R.string.backup_help_intro_body),
            )
            Spacer(modifier = Modifier.height(20.dp))
            HelpSection(
                title = stringResource(R.string.backup_help_jianguoyun_title),
                body = stringResource(R.string.backup_help_jianguoyun_body),
            )
            Spacer(modifier = Modifier.height(20.dp))
            HelpSection(
                title = stringResource(R.string.backup_help_auto_title),
                body = stringResource(R.string.backup_help_auto_body),
            )
            Spacer(modifier = Modifier.height(20.dp))
            HelpSection(
                title = stringResource(R.string.backup_help_tips_title),
                body = stringResource(R.string.backup_help_tips_body),
            )
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    body: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
    )
}
