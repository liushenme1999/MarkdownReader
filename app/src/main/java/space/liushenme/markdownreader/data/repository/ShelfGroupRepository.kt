package space.liushenme.markdownreader.data.repository

import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.ShelfGroupDao
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity
import space.liushenme.markdownreader.data.local.entity.ShelfGroups
import space.liushenme.markdownreader.data.local.entity.isSystemGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShelfGroupRepository @Inject constructor(
    private val shelfGroupDao: ShelfGroupDao,
    private val bookDao: BookDao,
) {
    fun observeGroups(): Flow<List<ShelfGroupEntity>> = shelfGroupDao.observeAll()

    suspend fun getGroups(): List<ShelfGroupEntity> = shelfGroupDao.getAll()

    suspend fun ensureAllGroup() {
        if (shelfGroupDao.getByName(ShelfGroups.ALL_SENTINEL) != null) return
        val groups = shelfGroupDao.getAll()
        if (groups.isNotEmpty()) {
            shelfGroupDao.updateAll(groups.map { it.copy(sortOrder = it.sortOrder + 1) })
        }
        shelfGroupDao.insert(
            ShelfGroupEntity(
                name = ShelfGroups.ALL_SENTINEL,
                sortOrder = 0L,
                isVisible = true,
            )
        )
    }

    /** 首次创建时插在「全部」之后；若已存在则不动顺序。 */
    suspend fun ensureFavoritesGroup() {
        if (shelfGroupDao.getByName(ShelfGroups.FAVORITES_SENTINEL) != null) return
        val allGroup = shelfGroupDao.getByName(ShelfGroups.ALL_SENTINEL)
        val insertOrder = if (allGroup != null) {
            val threshold = allGroup.sortOrder
            val toShift = shelfGroupDao.getAll().filter { it.sortOrder > threshold }
            if (toShift.isNotEmpty()) {
                shelfGroupDao.updateAll(toShift.map { it.copy(sortOrder = it.sortOrder + 1) })
            }
            threshold + 1
        } else {
            shelfGroupDao.getMaxSortOrder() + 1
        }
        shelfGroupDao.insert(
            ShelfGroupEntity(
                name = ShelfGroups.FAVORITES_SENTINEL,
                sortOrder = insertOrder,
                isVisible = true,
            )
        )
    }

    /** 确保系统分组存在，并将书上已有、但表中缺失的分组名补齐。 */
    suspend fun syncFromBooks() {
        ensureAllGroup()
        ensureFavoritesGroup()
        val existing = shelfGroupDao.getAll().map { it.name }.toSet()
        val fromBooks = bookDao.getDistinctShelfGroups()
            .map { it.trim() }
            .filter { ShelfGroups.isUserGroupName(it) }
        var nextOrder = shelfGroupDao.getMaxSortOrder() + 1
        for (name in fromBooks) {
            if (name !in existing) {
                shelfGroupDao.insert(
                    ShelfGroupEntity(name = name, sortOrder = nextOrder, isVisible = true)
                )
                nextOrder++
            }
        }
    }

    suspend fun ensureGroup(name: String): Boolean {
        if (!ShelfGroups.isUserGroupName(name)) return false
        val trimmed = name.trim()
        if (shelfGroupDao.getByName(trimmed) != null) return true
        val order = shelfGroupDao.getMaxSortOrder() + 1
        return shelfGroupDao.insert(
            ShelfGroupEntity(name = trimmed, sortOrder = order, isVisible = true)
        ) != -1L
    }

    suspend fun addGroup(name: String): Boolean {
        if (!ShelfGroups.isUserGroupName(name)) return false
        val trimmed = name.trim()
        if (shelfGroupDao.getByName(trimmed) != null) return false
        val order = shelfGroupDao.getMaxSortOrder() + 1
        return shelfGroupDao.insert(
            ShelfGroupEntity(name = trimmed, sortOrder = order, isVisible = true)
        ) != -1L
    }

    suspend fun deleteGroup(group: ShelfGroupEntity) {
        if (group.isSystemGroup) return
        bookDao.clearShelfGroupByName(group.name)
        shelfGroupDao.delete(group)
    }

    /**
     * 重命名用户分组，并同步更新书籍上的 shelfGroup。
     * @return true 表示已更新或名称未变；false 表示名称非法或与其它分组冲突。
     */
    suspend fun renameGroup(group: ShelfGroupEntity, newName: String): Boolean {
        if (group.isSystemGroup) return false
        if (!ShelfGroups.isUserGroupName(newName)) return false
        val trimmed = newName.trim()
        if (trimmed == group.name) return true
        if (shelfGroupDao.getByName(trimmed) != null) return false
        bookDao.renameShelfGroup(group.name, trimmed)
        shelfGroupDao.update(group.copy(name = trimmed))
        return true
    }

    suspend fun setVisible(group: ShelfGroupEntity, visible: Boolean) {
        if (group.isVisible == visible) return
        shelfGroupDao.update(group.copy(isVisible = visible))
    }

    suspend fun reorderGroups(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        val byId = shelfGroupDao.getAll().associateBy { it.id }
        val updates = orderedIds.mapIndexedNotNull { index, id ->
            val group = byId[id] ?: return@mapIndexedNotNull null
            if (group.sortOrder == index.toLong()) null
            else group.copy(sortOrder = index.toLong())
        }
        if (updates.isNotEmpty()) {
            shelfGroupDao.updateAll(updates)
        }
    }
}
