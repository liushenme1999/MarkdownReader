package space.liushenme.markdownreader.git

data class GitProgress(
    val phase: Phase,
    /** 0–100，未知时为 -1 */
    val percent: Int = -1,
    val detail: String = "",
) {
    enum class Phase {
        PREPARING,
        CLONING,
        FETCHING,
        UPDATING,
        INDEXING,
        DONE,
        FAILED,
    }
}
