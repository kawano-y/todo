package com.kawano.todo.data.repository

import com.kawano.todo.data.local.TaskDao
import com.kawano.todo.data.local.toDomain
import com.kawano.todo.data.local.toEntity
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room による [TaskRepository] 実装。
 *
 * @param now `setCompleted(id, true)` で記録する完了日時の取得元（テストで固定できるよう注入可能）
 */
class TaskRepositoryImpl(
    private val dao: TaskDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) : TaskRepository {

    override fun observeActiveTasks(group: TaskGroup): Flow<List<Task>> =
        dao.observeActive(group).map { list -> list.map { it.toDomain() } }

    override fun observeCompletedTasks(group: TaskGroup): Flow<List<Task>> =
        dao.observeCompleted(group).map { list -> list.map { it.toDomain() } }

    override suspend fun getTask(id: Long): Task? = dao.getById(id)?.toDomain()

    /**
     * `task.id == 0` なら新規挿入して採番された id を返す。
     * それ以外は更新し、該当行が無ければその id で挿入する。いずれも `task.id` を返す。
     */
    override suspend fun upsert(task: Task): Long {
        val entity = task.toEntity()
        if (task.id == 0L) return dao.insert(entity)
        if (dao.update(entity) == 0) dao.insert(entity)
        return task.id
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.updateCompleted(
            id = id,
            completed = completed,
            completedAt = if (completed) now() else null,
        )
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
