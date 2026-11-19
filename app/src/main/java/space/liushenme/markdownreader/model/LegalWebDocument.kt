package space.liushenme.markdownreader.model

import androidx.annotation.StringRes
import space.liushenme.markdownreader.R

/**
 * 「服务协议」里可以打开的在线文档。
 *
 * 页面内容托管在官网，App 内用 WebView 展示；右上角链接图标可用系统浏览器打开同一地址。
 * 修改 URL 时需同步官网路由（LiuTechShareWeb/src/router/index.js）。
 */
enum class LegalWebDocument(
    @StringRes val titleRes: Int,
    val url: String,
) {
    USER_AGREEMENT(
        titleRes = R.string.profile_menu_user_agreement,
        url = "https://liushenme.space/liu-tech-share/privacy/mdreader/user-agreement",
    ),
    PRIVACY_POLICY(
        titleRes = R.string.profile_menu_privacy_policy,
        url = "https://liushenme.space/liu-tech-share/privacy/mdreader",
    ),
    PERSONAL_INFO(
        titleRes = R.string.profile_menu_personal_info,
        url = "https://liushenme.space/liu-tech-share/privacy/mdreader/personal-info",
    ),
    APP_PERMISSIONS(
        titleRes = R.string.profile_menu_app_permissions,
        url = "https://liushenme.space/liu-tech-share/privacy/mdreader/permissions",
    ),
    ;

    /** 应用内 WebView 使用的地址：官网据此隐藏导航栏、主题按钮与页脚。 */
    val embedUrl: String
        get() = url + if (url.contains('?')) "&embed=1" else "?embed=1"

    companion object {
        fun fromKey(key: String?): LegalWebDocument? =
            entries.find { it.name.equals(key, ignoreCase = true) }
    }
}
