package space.liushenme.markdownreader.ui.screens.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.model.LegalWebDocument
import space.liushenme.markdownreader.navigation.AppRoutes
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

/**
 * 服务协议：用户协议、隐私政策、个人信息收集清单、应用权限四个入口。
 *
 * 每个入口进入 [LegalWebScreen]，顶部链接图标可用系统浏览器打开官网页面。
 */
@Composable
fun ServiceAgreementScreen(navController: NavController) {
    val pageBg = shelfStylePageBackground()

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = stringResource(R.string.profile_menu_service_agreement),
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column {
                    LegalEntryItem(
                        icon = Icons.Default.Description,
                        titleRes = R.string.profile_menu_user_agreement,
                        subtitleRes = R.string.profile_menu_user_agreement_subtitle,
                        document = LegalWebDocument.USER_AGREEMENT,
                        navController = navController,
                    )
                    EntryDivider()
                    LegalEntryItem(
                        icon = Icons.Default.PrivacyTip,
                        titleRes = R.string.profile_menu_privacy_policy,
                        subtitleRes = R.string.profile_menu_privacy_policy_subtitle,
                        document = LegalWebDocument.PRIVACY_POLICY,
                        navController = navController,
                    )
                    EntryDivider()
                    LegalEntryItem(
                        icon = Icons.AutoMirrored.Filled.ListAlt,
                        titleRes = R.string.profile_menu_personal_info,
                        subtitleRes = R.string.profile_menu_personal_info_subtitle,
                        document = LegalWebDocument.PERSONAL_INFO,
                        navController = navController,
                    )
                    EntryDivider()
                    LegalEntryItem(
                        icon = Icons.Default.VerifiedUser,
                        titleRes = R.string.profile_menu_app_permissions,
                        subtitleRes = R.string.profile_menu_app_permissions_subtitle,
                        document = LegalWebDocument.APP_PERMISSIONS,
                        navController = navController,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegalEntryItem(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    document: LegalWebDocument,
    navController: NavController,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(AppRoutes.legalWeb(document)) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun EntryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}
