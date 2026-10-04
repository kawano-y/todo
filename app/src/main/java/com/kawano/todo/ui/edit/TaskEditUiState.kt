package com.kawano.todo.ui.edit

import com.kawano.todo.domain.model.TaskGroup

/**
 * タスク作成・編集画面の UI 状態。
 *
 * @property isNew 新規作成なら true
 * @property isLoading 既存タスクのロード中
 * @property existingImagePath 保存済みタスクの画像パス（DB 上の値）
 * @property newImageUri 今回選択した未保存の画像 URI 文字列
 * @property removeImage 既存画像を外す操作をしたか
 * @property titleTouched タイトルを一度でも編集した / 保存を試みたか（エラー表示用）
 */
data class TaskEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val title: String = "",
    val note: String = "",
    val group: TaskGroup = TaskGroup.PRIVATE,
    val existingImagePath: String? = null,
    val newImageUri: String? = null,
    val removeImage: Boolean = false,
    val titleTouched: Boolean = false,
    val isSaving: Boolean = false,
) {
    /** プレビューに表示する画像（未保存の URI 文字列 or 保存済みの絶対パス）。 */
    val previewImage: String?
        get() = newImageUri ?: existingImagePath.takeUnless { removeImage }

    val isTitleValid: Boolean get() = title.isNotBlank()

    val showTitleError: Boolean get() = titleTouched && !isTitleValid

    val canSave: Boolean get() = !isLoading && !isSaving && isTitleValid
}

/** ViewModel から画面への one-shot イベント。 */
sealed interface TaskEditEvent {
    /** 保存完了、または対象タスクが存在しない → 画面を閉じる */
    data object Done : TaskEditEvent

    /** 画像の保存に失敗した */
    data object ImageSaveFailed : TaskEditEvent

    /** タスクの保存に失敗した */
    data object TaskSaveFailed : TaskEditEvent
}
