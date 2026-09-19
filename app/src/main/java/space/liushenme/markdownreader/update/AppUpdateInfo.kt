package space.liushenme.markdownreader.update

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val pageUrl: String,
) {
    fun isNewerThan(localVersionCode: Int): Boolean = versionCode > localVersionCode
}
