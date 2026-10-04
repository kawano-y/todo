package com.kawano.todo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kawano.todo.domain.model.TaskGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** 指定グループの未完了タスク（createdAt 降順、同値は id 降順）。 */
    @Query(
        "SELECT * FROM tasks WHERE `group` = :group AND is_completed = 0 " +
            "ORDER BY created_at DESC, id DESC"
    )
    fun observeActive(group: TaskGroup): Flow<List<TaskEntity>>

    /** 指定グループの完了タスク（completedAt 降順、同値は id 降順）。 */
    @Query(
        "SELECT * FROM tasks WHERE `group` = :group AND is_completed = 1 " +
            "ORDER BY completed_at DESC, id DESC"
    )
    fun observeCompleted(group: TaskGroup): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** 新規挿入。生成された id（rowId）を返す。 */
    @Insert
    suspend fun insert(task: TaskEntity): Long

    /** 既存行の更新。更新された行数を返す（0 = 該当なし）。 */
    @Update
    suspend fun update(task: TaskEntity): Int

    /** 完了状態と完了日時のみ更新する。更新された行数を返す。 */
    @Query("UPDATE tasks SET is_completed = :completed, completed_at = :completedAt WHERE id = :id")
    suspend fun updateCompleted(id: Long, completed: Boolean, completedAt: Long?): Int

    /** 削除された行数を返す。 */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
