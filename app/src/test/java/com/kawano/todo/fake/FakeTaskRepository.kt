package com.kawano.todo.fake

import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * ViewModel テスト用の TaskRepository。
 *
 * - 初期データは [setTasks] / コンストラクタで投入
 * - 現在の全タスクは [tasks] で同期的に検証可能
 * - [now] を差し替えると setCompleted の completedAt を固定できる
 * - 呼び出し履歴（[upsertCalls], [deletedIds], [setCompletedCalls]）を記録
 */
class FakeTaskRepository(
    initialTasks: List<Task> = emptyList(),
    var now: () -> Long = { System.currentTimeMillis() },
) : TaskRepository {

    private val state = MutableStateFlow(initialTasks.associateBy { it.id })
    private var nextId = (initialTasks.maxOfOrNull { it.id } ?: 0L) + 1

    val upsertCalls = mutableListOf<Task>()
    val deletedIds = mutableListOf<Long>()
    val setCompletedCalls = mutableListOf<Pair<Long, Boolean>>()

    /** 現在保持している全タスク（id 昇順）。 */
    val tasks: List<Task> get() = state.value.values.sortedBy { it.id }

    fun setTasks(tasks: List<Task>) {
        state.value = tasks.associateBy { it.id }
        nextId = (tasks.maxOfOrNull { it.id } ?: 0L) + 1
    }

    override fun observeActiveTasks(group: TaskGroup): Flow<List<Task>> =
        state.map { all ->
            all.values
                .filter { it.group == group && !it.isCompleted }
                .sortedByDescending { it.createdAt }
        }

    override fun observeCompletedTasks(group: TaskGroup): Flow<List<Task>> =
        state.map { all ->
            all.values
                .filter { it.group == group && it.isCompleted }
                .sortedByDescending { it.completedAt ?: 0L }
        }

    override suspend fun getTask(id: Long): Task? = state.value[id]

    override suspend fun upsert(task: Task): Long {
        upsertCalls += task
        val id = if (task.id == 0L) nextId++ else task.id
        state.update { it + (id to task.copy(id = id)) }
        return id
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        setCompletedCalls += id to completed
        state.update { current ->
            val task = current[id] ?: return@update current
            current + (id to task.copy(
                isCompleted = completed,
                completedAt = if (completed) now() else null,
            ))
        }
    }

    override suspend fun delete(id: Long) {
        deletedIds += id
        state.update { it - id }
    }
}
