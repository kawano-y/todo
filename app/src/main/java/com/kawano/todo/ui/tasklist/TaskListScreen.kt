package com.kawano.todo.ui.tasklist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.ui.theme.TodoTheme

// TODO(T3): TaskListViewModel / TaskListContent を実装する（T0 ではシグネチャのみ）
@Composable
fun TaskListScreen(onAddTask: (TaskGroup) -> Unit, onOpenTask: (Long) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: TaskListScreen")
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskListScreenPreview() {
    TodoTheme {
        TaskListScreen(onAddTask = {}, onOpenTask = {})
    }
}
