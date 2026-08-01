package space.liushenme.markdownreader.data.local.entity

/** 书架分组约定：含「全部」「收藏」等系统哨兵项。 */
object ShelfGroups {
    const val ALL_SENTINEL = "__all__"
    const val FAVORITES_SENTINEL = "__favorites__"

    fun isAll(name: String): Boolean = name == ALL_SENTINEL

    fun isFavorites(name: String): Boolean = name == FAVORITES_SENTINEL

    fun isSystemGroup(name: String): Boolean = isAll(name) || isFavorites(name)

    fun isUserGroupName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && !isSystemGroup(trimmed)
    }
}

val ShelfGroupEntity.isAllGroup: Boolean
    get() = ShelfGroups.isAll(name)

val ShelfGroupEntity.isFavoritesGroup: Boolean
    get() = ShelfGroups.isFavorites(name)

val ShelfGroupEntity.isSystemGroup: Boolean
    get() = ShelfGroups.isSystemGroup(name)
