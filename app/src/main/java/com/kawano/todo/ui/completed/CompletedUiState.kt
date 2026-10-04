package com.kawano.todo.ui.completed

import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup

/**
 * 完了一覧画面の UI 状態。
 *
 * @property selectedGroup 選択中のグループ（SavedStateHandle に保存）
 * @property tasks 選択中グループの完了タスク（completedAt 降順）
 * @property isLoading 選択中グループの一覧をまだ受け取っていない間は true（空状態を出さない）
 * @property pendingDelete 削除確認ダイアログの対象。null ならダイアログ非表示
 * @property userMessage Snackbar で表示する一時メッセージ。表示後に `onMessageShown` で消す
 */
data class CompletedUiState(
    val selectedGroup: TaskGroup = TaskGroup.PRIVATE,
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val pendingDelete: Task? = null,
    val userMessage: CompletedMessage? = null,
) {
    /** 読み込み済みかつ 0 件のときだけ空状態を表示する。 */
    val showEmpty: Boolean get() = !isLoading && tasks.isEmpty()
}

/** Snackbar 用メッセージ。同じ種類が連続しても再表示されるよう [id] で区別する。 */
sealed interface CompletedMessage {
    val id: Long

    /** 未完了に戻した。 */
    data class Restored(val title: String, override val id: Long) : CompletedMessage

    /** 削除した。 */
    data class Deleted(val title: String, override val id: Long) : CompletedMessage

    /** 操作に失敗した。 */
    data class Failed(override val id: Long) : CompletedMessage
}
