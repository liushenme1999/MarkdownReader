package space.liushenme.markdownreader.ui.screens.bookshelf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity
import space.liushenme.markdownreader.data.local.entity.isSystemGroup
import space.liushenme.markdownreader.data.repository.ShelfGroupRepository
import javax.inject.Inject

@HiltViewModel
class GroupManagementViewModel @Inject constructor(
    private val shelfGroupRepository: ShelfGroupRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val groups = shelfGroupRepository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastMessages = toastChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            shelfGroupRepository.syncFromBooks()
        }
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            val ok = shelfGroupRepository.addGroup(name)
            toastChannel.trySend(
                if (ok) {
                    appContext.getString(R.string.toast_group_added)
                } else {
                    appContext.getString(R.string.toast_group_add_failed)
                }
            )
        }
    }

    fun deleteGroup(group: ShelfGroupEntity) {
        if (group.isSystemGroup) return
        viewModelScope.launch {
            shelfGroupRepository.deleteGroup(group)
            toastChannel.trySend(appContext.getString(R.string.toast_group_deleted, group.name))
        }
    }

    fun renameGroup(group: ShelfGroupEntity, newName: String) {
        if (group.isSystemGroup) return
        viewModelScope.launch {
            val ok = shelfGroupRepository.renameGroup(group, newName)
            toastChannel.trySend(
                if (ok) {
                    appContext.getString(R.string.toast_group_renamed)
                } else {
                    appContext.getString(R.string.toast_group_rename_failed)
                }
            )
        }
    }

    fun setVisible(group: ShelfGroupEntity, visible: Boolean) {
        viewModelScope.launch {
            shelfGroupRepository.setVisible(group, visible)
        }
    }

    fun reorderGroups(orderedIds: List<Long>) {
        viewModelScope.launch {
            shelfGroupRepository.reorderGroups(orderedIds)
        }
    }
}
