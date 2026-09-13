package space.liushenme.markdownreader.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * DataStore 数字键的读写。Gson 恢复 JSON 时整数常变成 [Long]，
 * 若按 [intPreferencesKey] 写入成 Long 再读取会 ClassCastException。
 */
object PreferenceNumbers {
    val INT_KEYS = setOf(
        "reader_font_size",
        "reader_reader_padding_dp",
        "reader_last_highlight_color",
        "reader_text_color",
        "reader_bg_color",
        "reader_bg_alpha",
        "reader_reading_style_index",
        "bookshelf_grid_columns",
        "git_project_recent_read_count",
    )
    val FLOAT_KEYS = setOf("reader_line_spacing_multiplier")

    fun asInt(value: Any?): Int? = when (value) {
        null -> null
        is Int -> value
        is Number -> {
            val longVal = value.toLong()
            when {
                longVal in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() -> longVal.toInt()
                longVal in 0L..0xFFFFFFFFL -> longVal.toInt()
                else -> null
            }
        }
        else -> null
    }

    fun asFloat(value: Any?): Float? = when (value) {
        null -> null
        is Float -> value
        is Number -> value.toFloat()
        else -> null
    }

    fun writeNumber(prefs: MutablePreferences, name: String, value: Number) {
        when {
            name in FLOAT_KEYS -> prefs[floatPreferencesKey(name)] = value.toFloat()
            name in INT_KEYS -> prefs[intPreferencesKey(name)] = asInt(value) ?: value.toInt()
            isSignedInt(value) -> prefs[intPreferencesKey(name)] = value.toInt()
            isWholeNumber(value) -> prefs[longPreferencesKey(name)] = value.toLong()
            else -> prefs[floatPreferencesKey(name)] = value.toFloat()
        }
    }

    private fun isWholeNumber(value: Number): Boolean {
        val d = value.toDouble()
        return d == d.toLong().toDouble()
    }

    private fun isSignedInt(value: Number): Boolean {
        if (!isWholeNumber(value)) return false
        val longVal = value.toLong()
        return longVal in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    }
}

fun Preferences.intPref(key: Preferences.Key<Int>): Int? =
    PreferenceNumbers.asInt(rawPref(key.name))

fun Preferences.floatPref(key: Preferences.Key<Float>): Float? =
    PreferenceNumbers.asFloat(rawPref(key.name))

private fun Preferences.rawPref(name: String): Any? =
    asMap().entries.firstOrNull { it.key.name == name }?.value
