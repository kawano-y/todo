package com.kawano.todo.ui.completed

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kawano.todo.R
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.ui.components.GroupTabRow
import com.kawano.todo.ui.components.TaskThumbnail
import com.kawano.todo.ui.theme.TodoTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun CompletedScreen(
    onOpenTask: (Long) -> Unit,
    viewModel: CompletedViewModel = viewModel(factory = CompletedViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.userMessage) {
        val message = state.userMessage ?: return@LaunchedEffect
        val text = when (message) {
            is CompletedMessage.Restored -> context.getString(R.string.completed_restored, message.title)
            is CompletedMessage.Deleted -> context.getString(R.string.completed_deleted, message.title)
            is CompletedMessage.Failed -> context.getString(R.string.completed_operation_failed)
        }
        // 新しいメッセージが来るとこの effect はキャンセルされ、表示中の Snackbar も閉じられる
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown(message.id)
    }

    CompletedContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onSelectGroup = viewModel::selectGroup,
        onOpenTask = onOpenTask,
        onRestore = viewModel::restore,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
    )
}

/** ViewModel に依存しない完了一覧の本体。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedContent(
    state: CompletedUiState,
    onSelectGroup: (TaskGroup) -> Unit,
    onOpenTask: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.completed_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            GroupTabRow(selected = state.selectedGroup, onSelect = onSelectGroup)
            when {
                state.isLoading -> Unit // 読み込み中は空状態を出さない
                state.showEmpty -> EmptyState(Modifier.fillMaxSize())
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        CompletedTaskRow(
                            task = task,
                            onClick = { onOpenTask(task.id) },
                            onRestore = { onRestore(task.id) },
                            onDelete = { onRequestDelete(task.id) },
                            modifier = Modifier.animateItem(),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.completed_delete_dialog_title)) },
            text = { Text(stringResource(R.string.completed_delete_dialog_message, task.title)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.completed_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompletedTaskRow(
    task: Task,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.note.isNotBlank()) {
                Text(
                    text = task.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            task.completedAt?.let { completedAt ->
                Text(
                    text = stringResource(R.string.completed_completed_at, formatDateTime(completedAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (task.imagePath != null) {
            Spacer(Modifier.width(12.dp))
            TaskThumbnail(imagePath = task.imagePath)
        }
        RowMenu(onRestore = onRestore, onDelete = onDelete)
    }
}

@Composable
private fun RowMenu(onRestore: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.completed_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.completed_action_restore)) },
                onClick = {
                    expanded = false
                    onRestore()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** 端末ロケールの日時フォーマット（例: 2026/10/04 9:30）。 */
@Composable
private fun formatDateTime(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    return format.format(Date(epochMillis))
}

private val previewTasks = listOf(
    Task(
        id = 1,
        title = "牛乳を買う",
        note = "低脂肪のもの",
        group = TaskGroup.PRIVATE,
        isCompleted = true,
        createdAt = 1_759_500_000_000,
        completedAt = 1_759_560_000_000,
    ),
    Task(
        id = 2,
        title = "とても長いタイトルのタスクがここに入って二行目まで折り返して省略されることを確認する",
        note = "メモも長い場合は 1 行で省略される。メモも長い場合は 1 行で省略される。",
        group = TaskGroup.PRIVATE,
        imagePath = "/nonexistent.jpg",
        isCompleted = true,
        createdAt = 1_759_400_000_000,
        completedAt = 1_759_450_000_000,
    ),
    Task(
        id = 3,
        title = "メモなしのタスク",
        group = TaskGroup.PRIVATE,
        isCompleted = true,
        createdAt = 1_759_300_000_000,
        completedAt = 1_759_350_000_000,
    ),
)

@Composable
private fun PreviewContent(state: CompletedUiState) {
    CompletedContent(
        state = state,
        onSelectGroup = {},
        onOpenTask = {},
        onRestore = {},
        onRequestDelete = {},
        onConfirmDelete = {},
        onDismissDelete = {},
    )
}

@Preview(showBackground = true, name = "データあり")
@Composable
private fun CompletedContentPreview() {
    TodoTheme {
        PreviewContent(CompletedUiState(tasks = previewTasks, isLoading = false))
    }
}

@Preview(showBackground = true, name = "空")
@Composable
private fun CompletedContentEmptyPreview() {
    TodoTheme {
        PreviewContent(CompletedUiState(selectedGroup = TaskGroup.WORK, isLoading = false))
    }
}

@Preview(showBackground = true, name = "ダーク", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompletedContentDarkPreview() {
    TodoTheme {
        PreviewContent(CompletedUiState(tasks = previewTasks, isLoading = false))
    }
}
