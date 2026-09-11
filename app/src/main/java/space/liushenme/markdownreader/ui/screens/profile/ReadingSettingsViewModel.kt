package space.liushenme.markdownreader.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.model.AppLanguage
import space.liushenme.markdownreader.model.AppThemeMode
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.platform.AppLocaleController
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ReadingSettingsViewModel @Inject constructor(
    private val readerSettingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    val currentTheme = readerSettingsRepository.readingTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadingTheme.Paper,
    )

    val fontSize = readerSettingsRepository.fontSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettingsRepository.DEFAULT_FONT_SIZE,
    )

    val readerPaddingDp = readerSettingsRepository.readerPaddingDp.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettingsRepository.DEFAULT_PADDING_DP,
    )

    val readerLineSpacingMultiplier = readerSettingsRepository.readerLineSpacingMultiplier.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettingsRepository.DEFAULT_LINE_SPACING_MULT,
    )

    val codeBlockWrap = readerSettingsRepository.codeBlockWrap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettingsRepository.DEFAULT_CODE_BLOCK_WRAP,
    )

    val pageTurnMode = readerSettingsRepository.pageTurnMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderPageTurnMode.VerticalScroll,
    )

    val appThemeMode = readerSettingsRepository.appThemeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppThemeMode.SYSTEM,
    )

    val appLanguage = readerSettingsRepository.appLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppLanguage.DEFAULT,
    )

    fun setTheme(theme: ReadingTheme) {
        viewModelScope.launch { readerSettingsRepository.setReadingTheme(theme) }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { readerSettingsRepository.setFontSize(size) }
    }

    fun setReaderPaddingDp(dp: Int) {
        viewModelScope.launch { readerSettingsRepository.setReaderPaddingDp(dp) }
    }

    fun setReaderLineSpacingMultiplier(mult: Float) {
        val snapped = (mult * 20f).roundToInt() / 20f
        viewModelScope.launch {
            readerSettingsRepository.setReaderLineSpacingMultiplier(snapped)
        }
    }

    fun setCodeBlockWrap(wrap: Boolean) {
        viewModelScope.launch { readerSettingsRepository.setCodeBlockWrap(wrap) }
    }

    fun setPageTurnMode(mode: ReaderPageTurnMode) {
        viewModelScope.launch { readerSettingsRepository.setPageTurnMode(mode) }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { readerSettingsRepository.setAppThemeMode(mode) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            readerSettingsRepository.setAppLanguage(language)
            // setApplicationLocales 需在主线程，且依赖已创建的 AppCompatActivity
            withContext(Dispatchers.Main.immediate) {
                AppLocaleController.apply(language)
            }
        }
    }
}
