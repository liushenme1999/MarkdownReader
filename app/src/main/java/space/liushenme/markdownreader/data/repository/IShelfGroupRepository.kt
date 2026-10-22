package space.liushenme.markdownreader.data.repository

import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity

interface IShelfGroupRepository {
    fun observeGroups(): Flow<List<ShelfGroupEntity>>
    suspend fun getGroups(): List<ShelfGroupEntity>
    suspend fun ensureAllGroup()
    suspend fun ensureFavoritesGroup()
    suspend fun syncFromBooks()
    suspend fun ensureGroup(name: String): Boolean
    suspend fun addGroup(name: String): Boolean
    suspend fun deleteGroup(group: ShelfGroupEntity)
    suspend fun renameGroup(group: ShelfGroupEntity, newName: String): Boolean
    suspend fun setVisible(group: ShelfGroupEntity, visible: Boolean)
    suspend fun reorderGroups(orderedIds: List<Long>)
}
