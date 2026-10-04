package com.kawano.todo.di

import android.net.Uri
import com.kawano.todo.domain.image.ImageStorage
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * T0 の暫定実装。T6 で Room / ImageStorageImpl に差し替えた後は削除してよい。
 */

/** MutableStateFlow ベースの揮発性 TaskRepository。 */
internal class InMemoryTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow<Map<Long, Task>>(emptyMap())
    private var nextId = 1L

    override fun observeActiveTasks(group: TaskGroup): Flow<List<Task>> =
        tasks.map { all ->
            all.values
                .filter { it.group == group && !it.isCompleted }
                .sortedByDescending { it.createdAt }
        }

    override fun observeCompletedTasks(group: TaskGroup): Flow<List<Task>> =
        tasks.map { all ->
            all.values
                .filter { it.group == group && it.isCompleted }
                .sortedByDescending { it.completedAt ?: 0L }
        }

    override suspend fun getTask(id: Long): Task? = tasks.value[id]

    override suspend fun upsert(task: Task): Long = synchronized(this) {
        val id = if (task.id == 0L) nextId++ else task.id
        tasks.update { it + (id to task.copy(id = id)) }
        id
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        tasks.update { current ->
            val task = current[id] ?: return@update current
            current + (id to task.copy(
                isCompleted = completed,
                completedAt = if (completed) System.currentTimeMillis() else null,
            ))
        }
    }

    override suspend fun delete(id: Long) {
        tasks.update { it - id }
    }
}

/** 画像をコピーせず URI 文字列をそのまま返す暫定 ImageStorage。delete は何もしない。 */
internal class PassThroughImageStorage : ImageStorage {
    override suspend fun save(uri: Uri): String = uri.toString()
    override suspend fun delete(path: String) = Unit
}
