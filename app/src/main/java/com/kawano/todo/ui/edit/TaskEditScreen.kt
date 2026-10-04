package com.kawano.todo.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kawano.todo.ui.theme.TodoTheme

// TODO(T5): TaskEditViewModel / TaskEditContent を実装する（T0 ではシグネチャのみ）
// taskId / group は SavedStateHandle から取得する（キーと規則は ui/navigation/TodoNavHost.kt の EditArgs を参照）
@Composable
fun TaskEditScreen(onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: TaskEditScreen")
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskEditScreenPreview() {
    TodoTheme {
        TaskEditScreen(onDone = {})
    }
}
