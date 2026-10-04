package com.kawano.todo.ui.completed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kawano.todo.ui.theme.TodoTheme

// TODO(T4): CompletedViewModel / CompletedContent を実装する（T0 ではシグネチャのみ）
@Composable
fun CompletedScreen(onOpenTask: (Long) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: CompletedScreen")
    }
}

@Preview(showBackground = true)
@Composable
private fun CompletedScreenPreview() {
    TodoTheme {
        CompletedScreen(onOpenTask = {})
    }
}
