package space.liushenme.markdownreader.update

fun interface AppUpdateFetcher {
    fun fetchLatest(): AppUpdateInfo
}
