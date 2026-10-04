package com.kawano.todo.ui.tasklist

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kawano.todo.R
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.ui.components.GroupTabRow
import com.kawano.todo.ui.components.TaskThumbnail
import com.kawano.todo.ui.theme.TodoTheme
import kotlinx.coroutines.launch

@Composable
fun TaskListScreen(
    onAddTask: (TaskGroup) -> Unit,
    onOpenTask: (Long) -> Unit,
    viewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val completedMessage = stringResource(R.string.tasklist_completed_message)
    val undoLabel = stringResource(R.string.tasklist_undo)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is TaskListEvent.TaskCompleted -> {
                        // 連続で完了した場合は前の Snackbar を閉じて最新のものを出す
                        snackbarHostState.currentSnackbarData?.dismiss()
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = completedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoComplete(event.taskId)
                            }
                        }
                    }
                }
            }
        }
    }

    TaskListContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onSelectGroup = viewModel::selectGroup,
        onCompleteTask = viewModel::completeTask,
        onAddTask = { onAddTask(state.selectedGroup) },
        onOpenTask = onOpenTask,
    )
}

/** ViewModel に依存しない stateless なタスク一覧。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListContent(
    state: TaskListUiState,
    onSelectGroup: (TaskGroup) -> Unit,
    onCompleteTask: (Long) -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_tasks)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.tasklist_add_task),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            GroupTabRow(selected = state.selectedGroup, onSelect = onSelectGroup)
            when {
                state.isLoading -> Unit
                state.isEmpty -> EmptyState(modifier = Modifier.weight(1f))
                else -> TaskList(
                    tasks = state.tasks,
                    onCompleteTask = onCompleteTask,
                    onOpenTask = onOpenTask,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    onCompleteTask: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // 最後の行が FAB に隠れないよう下部に余白を確保
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(items = tasks, key = { it.id }) { task ->
            TaskRow(
                task = task,
                onComplete = { onCompleteTask(task.id) },
                onClick = { onOpenTask(task.id) },
                modifier = Modifier.animateItem(),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkboxDescription = stringResource(R.string.tasklist_complete_task, task.title)
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Checkbox(
                checked = false,
                onCheckedChange = { checked -> if (checked) onComplete() },
                modifier = Modifier.semantics { contentDescription = checkboxDescription },
            )
        },
        headlineContent = {
            Text(text = task.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (task.note.isNotBlank()) {
            { Text(text = task.note, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = if (task.imagePath != null) {
            { TaskThumbnail(imagePath = task.imagePath) }
        } else {
            null
        },
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.tasklist_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Preview ----

private val previewTasks = listOf(
    Task(id = 1, title = "牛乳を買う", note = "低脂肪のもの", group = TaskGroup.PRIVATE, createdAt = 3),
    Task(id = 2, title = "部屋の掃除", group = TaskGroup.PRIVATE, createdAt = 2),
    Task(
        id = 3,
        title = "写真付きのとても長いタイトルのタスクで表示が崩れないか確認する",
        note = "メモが長い場合は 1 行で省略表示されることを確認するためのテキストです",
        group = TaskGroup.PRIVATE,
        imagePath = "/nonexistent.jpg",
        createdAt = 1,
    ),
)

@Preview(name = "データあり", showBackground = true)
@Composable
private fun TaskListContentPreview() {
    TodoTheme {
        TaskListContent(
            state = TaskListUiState(tasks = previewTasks, isLoading = false),
            onSelectGroup = {},
            onCompleteTask = {},
            onAddTask = {},
            onOpenTask = {},
        )
    }
}

@Preview(name = "空状態", showBackground = true)
@Composable
private fun TaskListContentEmptyPreview() {
    TodoTheme {
        TaskListContent(
            state = TaskListUiState(selectedGroup = TaskGroup.WORK, isLoading = false),
            onSelectGroup = {},
            onCompleteTask = {},
            onAddTask = {},
            onOpenTask = {},
        )
    }
}

@Preview(name = "ダーク", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskListContentDarkPreview() {
    TodoTheme {
        TaskListContent(
            state = TaskListUiState(tasks = previewTasks, isLoading = false),
            onSelectGroup = {},
            onCompleteTask = {},
            onAddTask = {},
            onOpenTask = {},
        )
    }
}
