package com.kawano.todo.data.local

import androidx.room.TypeConverter
import com.kawano.todo.domain.model.TaskGroup

/** Room 用 TypeConverter。[TaskGroup] は enum 名（"PRIVATE" / "WORK"）の文字列で保存する。 */
class Converters {
    @TypeConverter
    fun fromTaskGroup(group: TaskGroup): String = group.name

    @TypeConverter
    fun toTaskGroup(value: String): TaskGroup = TaskGroup.valueOf(value)
}
