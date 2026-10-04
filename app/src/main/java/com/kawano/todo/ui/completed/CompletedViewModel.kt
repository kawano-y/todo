package com.kawano.todo.ui.completed

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kawano.todo.TodoApplication
import com.kawano.todo.domain.image.ImageStorage
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CompletedViewModel(
    private val repository: TaskRepository,
    private val imageStorage: ImageStorage,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val selectedGroup: StateFlow<TaskGroup> = savedStateHandle
        .getStateFlow(KEY_SELECTED_GROUP, TaskGroup.PRIVATE.name)
        .map(::parseGroup)
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentGroup())

    /** 受け取った一覧と、それがどのグループのものか（グループ切替直後の古い一覧を区別するため）。 */
    private data class Loaded(val group: TaskGroup, val tasks: List<Task>)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loaded = selectedGroup.flatMapLatest { group ->
        repository.observeCompletedTasks(group)
            .map<List<Task>, Loaded?> { tasks ->
                Loaded(group, tasks.sortedByDescending { it.completedAt ?: 0L })
            }
            .onStart { emit(null) }
    }

    private val pendingDeleteId = MutableStateFlow<Long?>(null)
    private val userMessage = MutableStateFlow<CompletedMessage?>(null)
    private var nextMessageId = 0L

    val uiState: StateFlow<CompletedUiState> = combine(
        selectedGroup,
        loaded,
        pendingDeleteId,
        userMessage,
    ) { group, loaded, pendingId, message ->
        val isLoading = loaded == null || loaded.group != group
        val tasks = if (isLoading) emptyList() else loaded!!.tasks
        CompletedUiState(
            selectedGroup = group,
            tasks = tasks,
            isLoading = isLoading,
            pendingDelete = pendingId?.let { id -> tasks.firstOrNull { it.id == id } },
            userMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CompletedUiState(selectedGroup = currentGroup()),
    )

    fun selectGroup(group: TaskGroup) {
        savedStateHandle[KEY_SELECTED_GROUP] = group.name
        pendingDeleteId.value = null
    }

    /** 未完了に戻す。完了一覧から消え、Snackbar で通知する。 */
    fun restore(taskId: Long) {
        viewModelScope.launch {
            val title = uiState.value.tasks.firstOrNull { it.id == taskId }?.title
                ?: repository.getTask(taskId)?.title.orEmpty()
            try {
                repository.setCompleted(taskId, false)
                postMessage { CompletedMessage.Restored(title, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore task $taskId", e)
                postMessage { CompletedMessage.Failed(it) }
            }
        }
    }

    /** 削除確認ダイアログを表示する。 */
    fun requestDelete(taskId: Long) {
        pendingDeleteId.value = taskId
    }

    fun dismissDelete() {
        pendingDeleteId.value = null
    }

    /**
     * 確認ダイアログの対象を削除する。タスク削除後に画像があれば ImageStorage からも削除する。
     * 画像削除の失敗はログのみとし、タスク削除は成功扱いにする。
     */
    fun confirmDelete() {
        val taskId = pendingDeleteId.value ?: return
        pendingDeleteId.value = null
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch
            try {
                repository.delete(task.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete task ${task.id}", e)
                postMessage { CompletedMessage.Failed(it) }
                return@launch
            }
            task.imagePath?.let { path ->
                try {
                    imageStorage.delete(path)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete image for task ${task.id}", e)
                }
            }
            postMessage { CompletedMessage.Deleted(task.title, it) }
        }
    }

    /** Snackbar 表示後に呼ぶ。 */
    fun onMessageShown(messageId: Long) {
        userMessage.update { current -> if (current?.id == messageId) null else current }
    }

    private fun postMessage(create: (Long) -> CompletedMessage) {
        userMessage.value = create(nextMessageId++)
    }

    private fun currentGroup(): TaskGroup =
        parseGroup(savedStateHandle.get<String>(KEY_SELECTED_GROUP))

    companion object {
        private const val TAG = "CompletedViewModel"
        const val KEY_SELECTED_GROUP = "completed_selected_group"

        private fun parseGroup(name: String?): TaskGroup =
            TaskGroup.entries.firstOrNull { it.name == name } ?: TaskGroup.PRIVATE

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TodoApplication
                CompletedViewModel(
                    repository = app.container.taskRepository,
                    imageStorage = app.container.imageStorage,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
