package com.kawano.todo.di

import android.content.Context
import com.kawano.todo.domain.image.ImageStorage
import com.kawano.todo.domain.repository.TaskRepository

/**
 * 手動 DI コンテナ。UI 層はここから domain の interface のみを受け取る。
 *
 * ViewModel からは以下のように取得する:
 * ```
 * val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TodoApplication
 * app.container.taskRepository
 * ```
 */
interface AppContainer {
    val taskRepository: TaskRepository
    val imageStorage: ImageStorage
}

/**
 * アプリ本体で使うコンテナ。
 *
 * T0 時点では暫定の in-memory 実装をバインドしている（アプリ再起動でデータは消える）。
 * T6 で `TaskRepositoryImpl`（Room）/ `ImageStorageImpl` に差し替える。
 */
class DefaultAppContainer(
    @Suppress("unused") private val context: Context,
) : AppContainer {
    override val taskRepository: TaskRepository by lazy { InMemoryTaskRepository() }
    override val imageStorage: ImageStorage by lazy { PassThroughImageStorage() }
}
