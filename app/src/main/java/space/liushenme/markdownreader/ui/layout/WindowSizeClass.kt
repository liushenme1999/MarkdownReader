package space.liushenme.markdownreader.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable app-level breakpoints; keeps layout decisions testable without Configuration globals. */
enum class AppWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

fun appWindowWidthClass(width: Dp): AppWindowWidthClass = when {
    width < 600.dp -> AppWindowWidthClass.Compact
    width < 840.dp -> AppWindowWidthClass.Medium
    else -> AppWindowWidthClass.Expanded
}

fun AppWindowWidthClass.usesNavigationRail(): Boolean = this != AppWindowWidthClass.Compact

fun AppWindowWidthClass.usesWideReaderNavigation(): Boolean = this == AppWindowWidthClass.Expanded
