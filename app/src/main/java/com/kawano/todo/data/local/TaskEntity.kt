package com.kawano.todo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup

/**
 * `tasks` テーブルの行。`group` 列は [Converters] により String（enum 名）で保存される。
 */
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["group", "is_completed"])],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String,
    @ColumnInfo(name = "group")
    val group: TaskGroup,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    note = note,
    group = group,
    imagePath = imagePath,
    isCompleted = isCompleted,
    createdAt = createdAt,
    completedAt = completedAt,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    note = note,
    group = group,
    imagePath = imagePath,
    isCompleted = isCompleted,
    createdAt = createdAt,
    completedAt = completedAt,
)
