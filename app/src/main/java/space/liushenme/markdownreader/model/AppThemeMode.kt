package space.liushenme.markdownreader.model

import androidx.annotation.StringRes
import space.liushenme.markdownreader.R

enum class AppThemeMode(
    @StringRes val labelRes: Int,
    @StringRes val hintRes: Int,
    @StringRes val shortLabelRes: Int,
) {
    SYSTEM(
        R.string.theme_mode_system_label,
        R.string.theme_mode_system_hint,
        R.string.theme_value_system,
    ),
    LIGHT(
        R.string.theme_mode_light_label,
        R.string.theme_mode_light_hint,
        R.string.theme_value_light,
    ),
    DARK(
        R.string.theme_mode_dark_label,
        R.string.theme_mode_dark_hint,
        R.string.theme_value_dark,
    ),
}
