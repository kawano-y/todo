package com.kawano.todo.domain.repository

import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeActiveTasks(group: TaskGroup): Flow<List<Task>>     // createdAt 降順
    fun observeCompletedTasks(group: TaskGroup): Flow<List<Task>>  // completedAt 降順
    suspend fun getTask(id: Long): Task?
    suspend fun upsert(task: Task): Long
    suspend fun setCompleted(id: Long, completed: Boolean)        // completedAt も更新
    suspend fun delete(id: Long)                                  // 画像ファイル削除は呼び出し側 (ViewModel) の責務
}
