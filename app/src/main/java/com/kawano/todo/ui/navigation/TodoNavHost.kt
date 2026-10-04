package com.kawano.todo.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kawano.todo.R
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.ui.completed.CompletedScreen
import com.kawano.todo.ui.edit.TaskEditScreen
import com.kawano.todo.ui.tasklist.TaskListScreen

/**
 * ルート定義。
 *
 * edit 画面の引数（`SavedStateHandle` から同名キーで取得する）:
 * - [EditArgs.TASK_ID]: `Long`。未指定（新規作成）は [EditArgs.NEW_TASK_ID] (= -1L)。
 *   `savedStateHandle.get<Long>(EditArgs.TASK_ID)` は常に non-null の Long を返す。
 * - [EditArgs.GROUP]: `String?`（`TaskGroup.name`）。新規作成時の初期グループ。
 *   既存タスク編集時は null（タスク自身の group を使う）。
 *   `savedStateHandle.get<String>(EditArgs.GROUP)?.let(TaskGroup::valueOf)`
 */
object TodoRoutes {
    const val TASKS = "tasks"
    const val COMPLETED = "completed"
    const val EDIT = "edit?${EditArgs.TASK_ID}={${EditArgs.TASK_ID}}&${EditArgs.GROUP}={${EditArgs.GROUP}}"

    /** 新規作成: `editRoute(group = g)`、既存編集: `editRoute(taskId = id)` */
    fun editRoute(taskId: Long? = null, group: TaskGroup? = null): String {
        val params = buildList {
            if (taskId != null) add("${EditArgs.TASK_ID}=$taskId")
            if (group != null) add("${EditArgs.GROUP}=${group.name}")
        }
        return if (params.isEmpty()) "edit" else "edit?" + params.joinToString("&")
    }
}

object EditArgs {
    const val TASK_ID = "taskId"
    const val GROUP = "group"
    const val NEW_TASK_ID = -1L
}

private enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TASKS(TodoRoutes.TASKS, R.string.nav_tasks, Icons.AutoMirrored.Filled.List),
    COMPLETED(TodoRoutes.COMPLETED, R.string.nav_completed, Icons.Filled.CheckCircle),
}

@Composable
fun TodoNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        // 各画面が自前の Scaffold でステータスバー等の insets を処理するため、ここでは insets を付けない
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodoRoutes.TASKS,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(TodoRoutes.TASKS) {
                TaskListScreen(
                    onAddTask = { group -> navController.navigate(TodoRoutes.editRoute(group = group)) },
                    onOpenTask = { id -> navController.navigate(TodoRoutes.editRoute(taskId = id)) },
                )
            }
            composable(TodoRoutes.COMPLETED) {
                CompletedScreen(
                    onOpenTask = { id -> navController.navigate(TodoRoutes.editRoute(taskId = id)) },
                )
            }
            composable(
                route = TodoRoutes.EDIT,
                arguments = listOf(
                    navArgument(EditArgs.TASK_ID) {
                        type = NavType.LongType
                        defaultValue = EditArgs.NEW_TASK_ID
                    },
                    navArgument(EditArgs.GROUP) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                TaskEditScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
