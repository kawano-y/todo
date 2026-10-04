package com.kawano.todo.di

import android.content.Context
import com.kawano.todo.data.image.ImageStorageImpl
import com.kawano.todo.data.local.TodoDatabase
import com.kawano.todo.data.repository.TaskRepositoryImpl
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

/** アプリ本体で使うコンテナ。Room と filesDir/images への画像保存をバインドする。 */
class DefaultAppContainer(
    private val context: Context,
) : AppContainer {
    private val database by lazy { TodoDatabase.create(context) }

    override val taskRepository: TaskRepository by lazy { TaskRepositoryImpl(database.taskDao()) }
    override val imageStorage: ImageStorage by lazy { ImageStorageImpl(context) }
}
