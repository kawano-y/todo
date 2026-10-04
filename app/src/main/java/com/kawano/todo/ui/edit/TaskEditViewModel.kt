package com.kawano.todo.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kawano.todo.TodoApplication
import com.kawano.todo.domain.image.ImageStorage
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.domain.repository.TaskRepository
import com.kawano.todo.ui.navigation.EditArgs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * タスク作成・編集画面の ViewModel。
 *
 * 入力中の値（タイトル・メモ・グループ・新画像 URI・画像除去フラグ）は [SavedStateHandle] に保持し、
 * 画面回転やプロセス再生成後も復元する。
 *
 * @param now 新規作成時の createdAt（テストで差し替え可能）
 * @param parseUri URI 文字列 → [Uri]（ローカルテストでは Uri.parse が使えないため差し替え可能）
 */
class TaskEditViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val imageStorage: ImageStorage,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val parseUri: (String) -> Uri = Uri::parse,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>(EditArgs.TASK_ID) ?: EditArgs.NEW_TASK_ID
    private val isNew: Boolean = taskId == EditArgs.NEW_TASK_ID

    /** ロードした既存タスク（新規なら null）。isCompleted / completedAt / createdAt の維持に使う。 */
    private var originalTask: Task? = null

    private val _uiState = MutableStateFlow(restoreState())
    val uiState: StateFlow<TaskEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<TaskEditEvent>(Channel.BUFFERED)
    val events: Flow<TaskEditEvent> = _events.receiveAsFlow()

    init {
        if (!isNew) loadTask()
    }

    private fun restoreState(): TaskEditUiState {
        val initialGroup = savedStateHandle.get<String>(EditArgs.GROUP)
            ?.let { runCatching { TaskGroup.valueOf(it) }.getOrNull() }
            ?: TaskGroup.PRIVATE
        return TaskEditUiState(
            isNew = isNew,
            isLoading = !isNew,
            title = savedStateHandle[KEY_TITLE] ?: "",
            note = savedStateHandle[KEY_NOTE] ?: "",
            group = savedStateHandle.get<String>(KEY_GROUP)
                ?.let { runCatching { TaskGroup.valueOf(it) }.getOrNull() }
                ?: initialGroup,
            newImageUri = savedStateHandle[KEY_NEW_IMAGE_URI],
            removeImage = savedStateHandle[KEY_REMOVE_IMAGE] ?: false,
            titleTouched = savedStateHandle[KEY_TITLE_TOUCHED] ?: false,
        )
    }

    private fun loadTask() {
        viewModelScope.launch {
            val task = try {
                taskRepository.getTask(taskId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (task == null) {
                _events.send(TaskEditEvent.Done)
                return@launch
            }
            originalTask = task
            val restored = savedStateHandle.get<Boolean>(KEY_INITIALIZED) == true
            if (!restored) {
                // 初回ロード時のみタスクの値で入力欄を初期化する（復元時はユーザー入力を優先）
                savedStateHandle[KEY_TITLE] = task.title
                savedStateHandle[KEY_NOTE] = task.note
                savedStateHandle[KEY_GROUP] = task.group.name
                savedStateHandle[KEY_INITIALIZED] = true
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    existingImagePath = task.imagePath,
                    title = if (restored) state.title else task.title,
                    note = if (restored) state.note else task.note,
                    group = if (restored) state.group else task.group,
                )
            }
        }
    }

    fun onTitleChange(title: String) {
        savedStateHandle[KEY_TITLE] = title
        savedStateHandle[KEY_TITLE_TOUCHED] = true
        _uiState.update { it.copy(title = title, titleTouched = true) }
    }

    fun onNoteChange(note: String) {
        savedStateHandle[KEY_NOTE] = note
        _uiState.update { it.copy(note = note) }
    }

    fun onGroupChange(group: TaskGroup) {
        savedStateHandle[KEY_GROUP] = group.name
        _uiState.update { it.copy(group = group) }
    }

    /** Photo Picker で画像を選択した（添付・差し替え共通）。 */
    fun onImagePicked(uri: String) {
        savedStateHandle[KEY_NEW_IMAGE_URI] = uri
        savedStateHandle[KEY_REMOVE_IMAGE] = false
        _uiState.update { it.copy(newImageUri = uri, removeImage = false) }
    }

    /** 画像を外す。未保存の新画像は破棄し、既存画像があれば保存時に削除する。 */
    fun onRemoveImage() {
        val removeExisting = _uiState.value.existingImagePath != null
        savedStateHandle[KEY_NEW_IMAGE_URI] = null
        savedStateHandle[KEY_REMOVE_IMAGE] = removeExisting
        _uiState.update { it.copy(newImageUri = null, removeImage = removeExisting) }
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.isTitleValid) {
            savedStateHandle[KEY_TITLE_TOUCHED] = true
            _uiState.update { it.copy(titleTouched = true) }
            return
        }
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val oldImagePath = originalTask?.imagePath

            // 1. 新しい画像があればアプリ内ストレージへ保存
            val savedNewPath: String? = state.newImageUri?.let { uriString ->
                try {
                    imageStorage.save(parseUri(uriString))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(TaskEditEvent.ImageSaveFailed)
                    return@launch
                }
            }
            val finalImagePath = when {
                savedNewPath != null -> savedNewPath
                state.removeImage -> null
                else -> oldImagePath
            }

            // 2. タスクを保存
            val task = originalTask?.copy(
                title = state.title.trim(),
                note = state.note,
                group = state.group,
                imagePath = finalImagePath,
            ) ?: Task(
                title = state.title.trim(),
                note = state.note,
                group = state.group,
                imagePath = finalImagePath,
                createdAt = now(),
            )
            try {
                taskRepository.upsert(task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 新しく保存した画像は参照されないので後始末する
                savedNewPath?.let { deleteImageQuietly(it) }
                _uiState.update { it.copy(isSaving = false) }
                _events.send(TaskEditEvent.TaskSaveFailed)
                return@launch
            }

            // 3. 不要になった旧画像を削除（upsert 成功後に行い、DB が存在しないファイルを指さないようにする）
            if (oldImagePath != null && oldImagePath != finalImagePath) {
                deleteImageQuietly(oldImagePath)
            }
            _events.send(TaskEditEvent.Done)
        }
    }

    private suspend fun deleteImageQuietly(path: String) {
        try {
            imageStorage.delete(path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 画像ファイルの削除失敗は保存結果に影響させない
        }
    }

    companion object {
        internal const val KEY_TITLE = "edit_title"
        internal const val KEY_NOTE = "edit_note"
        internal const val KEY_GROUP = "edit_group"
        internal const val KEY_NEW_IMAGE_URI = "edit_new_image_uri"
        internal const val KEY_REMOVE_IMAGE = "edit_remove_image"
        internal const val KEY_TITLE_TOUCHED = "edit_title_touched"
        internal const val KEY_INITIALIZED = "edit_initialized"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TodoApplication
                TaskEditViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    taskRepository = app.container.taskRepository,
                    imageStorage = app.container.imageStorage,
                )
            }
        }
    }
}
