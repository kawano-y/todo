package com.kawano.todo.domain.model

enum class TaskGroup { PRIVATE, WORK }

data class Task(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val group: TaskGroup,
    val imagePath: String? = null,   // filesDir 内の絶対パス
    val isCompleted: Boolean = false,
    val createdAt: Long,             // epoch millis
    val completedAt: Long? = null,
)
