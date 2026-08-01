package space.liushenme.markdownreader.ui.screens.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity
import space.liushenme.markdownreader.data.local.entity.isAllGroup
import space.liushenme.markdownreader.data.local.entity.isFavoritesGroup
import space.liushenme.markdownreader.data.local.entity.isSystemGroup
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementScreen(
    navController: NavController,
    viewModel: GroupManagementViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsState()
    val pageBg = shelfStylePageBackground()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var addGroupName by remember { mutableStateOf("") }
    var groupPendingEdit by remember { mutableStateOf<ShelfGroupEntity?>(null) }
    var editGroupName by remember { mutableStateOf("") }
    var groupPendingDelete by remember { mutableStateOf<ShelfGroupEntity?>(null) }
    val allGroupLabel = stringResource(R.string.bookshelf_group_all)
    val favoritesGroupLabel = stringResource(R.string.bookshelf_group_favorites)

    var localGroups by remember { mutableStateOf(groups) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val latestLocalGroups = rememberUpdatedState(localGroups)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemStridePx = with(density) { 66.dp.toPx() }

    LaunchedEffect(groups) {
        if (draggingId == null) {
            localGroups = groups
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun commitReorder() {
        viewModel.reorderGroups(latestLocalGroups.value.map { it.id })
        draggingId = null
        dragOffsetY = 0f
    }

    Scaffold(
        containerColor = pageBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = stringResource(R.string.group_management_title),
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addGroupName = ""
                    showAddDialog = true
                },
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.group_management_add_cd),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(paddingValues),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    items = localGroups,
                    key = { _, item -> item.id },
                ) { _, group ->
                    val isDragging = group.id == draggingId
                    GroupManagementRow(
                        title = when {
                            group.isAllGroup -> allGroupLabel
                            group.isFavoritesGroup -> favoritesGroupLabel
                            else -> group.name
                        },
                        checked = group.isVisible,
                        canEdit = !group.isSystemGroup,
                        canDelete = !group.isSystemGroup,
                        isDragging = isDragging,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetY
                                    shadowElevation = 8.dp.toPx()
                                }
                            }
                            .pointerInput(group.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = group.id
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = { commitReorder() },
                                    onDragCancel = { commitReorder() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val current = latestLocalGroups.value
                                        val from = current.indexOfFirst { it.id == draggingId }
                                        if (from < 0) return@detectDragGesturesAfterLongPress
                                        var target = from
                                        while (dragOffsetY > itemStridePx / 2f &&
                                            target < current.lastIndex
                                        ) {
                                            target++
                                            dragOffsetY -= itemStridePx
                                        }
                                        while (dragOffsetY < -itemStridePx / 2f && target > 0) {
                                            target--
                                            dragOffsetY += itemStridePx
                                        }
                                        if (target != from) {
                                            localGroups = current.toMutableList().apply {
                                                add(target, removeAt(from))
                                            }
                                        }
                                    },
                                )
                            },
                        onCheckedChange = { checked ->
                            localGroups = latestLocalGroups.value.map {
                                if (it.id == group.id) it.copy(isVisible = checked) else it
                            }
                            viewModel.setVisible(group, checked)
                        },
                        onEdit = {
                            groupPendingEdit = group
                            editGroupName = group.name
                        },
                        onDelete = { groupPendingDelete = group },
                    )
                }
            }
        }
    }

    groupPendingEdit?.let { group ->
        AlertDialog(
            onDismissRequest = { groupPendingEdit = null },
            title = { Text(stringResource(R.string.group_management_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editGroupName,
                    onValueChange = { editGroupName = it },
                    label = { Text(stringResource(R.string.dialog_group_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = editGroupName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.renameGroup(group, name)
                            groupPendingEdit = null
                        }
                    },
                    enabled = editGroupName.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { groupPendingEdit = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.group_management_add_title)) },
            text = {
                OutlinedTextField(
                    value = addGroupName,
                    onValueChange = { addGroupName = it },
                    label = { Text(stringResource(R.string.dialog_group_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = addGroupName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.addGroup(name)
                            showAddDialog = false
                        }
                    },
                    enabled = addGroupName.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    groupPendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupPendingDelete = null },
            title = { Text(stringResource(R.string.group_management_delete_title)) },
            text = {
                Text(stringResource(R.string.group_management_delete_message, group.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(group)
                        groupPendingDelete = null
                    },
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { groupPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun GroupManagementRow(
    title: String,
    checked: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    isDragging: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isDragging) 3.dp else 1.dp,
        shadowElevation = if (isDragging) 4.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.group_management_drag_cd),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp)
                    .size(22.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.group_management_edit_cd),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.group_management_delete_cd),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
