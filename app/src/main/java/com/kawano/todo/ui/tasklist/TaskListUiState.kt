package com.kawano.todo.ui.tasklist

import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup

/** タスク一覧画面の UI 状態。 */
data class TaskListUiState(
    val selectedGroup: TaskGroup = TaskGroup.PRIVATE,
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** 読み込み完了後にタスクが 0 件のときだけ空状態を表示する。 */
    val isEmpty: Boolean get() = !isLoading && tasks.isEmpty()
}

/** ViewModel から画面への one-shot イベント。 */
sealed interface TaskListEvent {
    /** タスクを完了にした（Snackbar で「元に戻す」を提示する）。 */
    data class TaskCompleted(val taskId: Long) : TaskListEvent
}
