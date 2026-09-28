package space.liushenme.markdownreader.ui.layout

/** Orientation used by navigation, shelf, and reader layouts. */
enum class AppWindowOrientation {
    Portrait,
    Landscape,
}

fun appWindowOrientation(widthPx: Int, heightPx: Int): AppWindowOrientation =
    if (widthPx > heightPx) AppWindowOrientation.Landscape else AppWindowOrientation.Portrait

fun isLandscapeWindow(widthPx: Int, heightPx: Int): Boolean =
    appWindowOrientation(widthPx, heightPx) == AppWindowOrientation.Landscape
