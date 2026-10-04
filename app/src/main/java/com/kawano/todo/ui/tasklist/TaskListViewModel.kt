package com.kawano.todo.ui.tasklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kawano.todo.TodoApplication
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.domain.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskListViewModel(
    private val taskRepository: TaskRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 選択中グループ。画面回転・プロセス再生成でも保持するため SavedStateHandle に TaskGroup.name で保存する。 */
    private val selectedGroup: Flow<TaskGroup> =
        savedStateHandle.getStateFlow(KEY_SELECTED_GROUP, TaskGroup.PRIVATE.name)
            .map(::parseGroup)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TaskListUiState> =
        selectedGroup
            .flatMapLatest { group ->
                taskRepository.observeActiveTasks(group).map { tasks ->
                    TaskListUiState(selectedGroup = group, tasks = tasks, isLoading = false)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TaskListUiState(
                    selectedGroup = parseGroup(savedStateHandle[KEY_SELECTED_GROUP]),
                ),
            )

    private val _events = Channel<TaskListEvent>(Channel.BUFFERED)

    /** one-shot イベント（完了通知 → Snackbar）。1 回だけ消費される。 */
    val events: Flow<TaskListEvent> = _events.receiveAsFlow()

    fun selectGroup(group: TaskGroup) {
        savedStateHandle[KEY_SELECTED_GROUP] = group.name
    }

    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            taskRepository.setCompleted(taskId, true)
            _events.send(TaskListEvent.TaskCompleted(taskId))
        }
    }

    fun undoComplete(taskId: Long) {
        viewModelScope.launch {
            taskRepository.setCompleted(taskId, false)
        }
    }

    companion object {
        internal const val KEY_SELECTED_GROUP = "tasklist_selected_group"

        private fun parseGroup(name: String?): TaskGroup =
            TaskGroup.entries.firstOrNull { it.name == name } ?: TaskGroup.PRIVATE

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TodoApplication
                TaskListViewModel(
                    taskRepository = app.container.taskRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
