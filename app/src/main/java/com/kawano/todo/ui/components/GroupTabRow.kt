package com.kawano.todo.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.kawano.todo.R
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.ui.theme.TodoTheme

/** PRIVATE / WORK を切り替える上部タブ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTabRow(
    selected: TaskGroup,
    onSelect: (TaskGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier,
    ) {
        TaskGroup.entries.forEach { group ->
            Tab(
                selected = group == selected,
                onClick = { onSelect(group) },
                text = { Text(stringResource(group.labelRes)) },
            )
        }
    }
}

/** グループの表示名（strings.xml）。 */
@get:StringRes
val TaskGroup.labelRes: Int
    get() = when (this) {
        TaskGroup.PRIVATE -> R.string.group_private
        TaskGroup.WORK -> R.string.group_work
    }

@Preview(showBackground = true)
@Composable
private fun GroupTabRowPreview() {
    TodoTheme {
        GroupTabRow(selected = TaskGroup.PRIVATE, onSelect = {})
    }
}
