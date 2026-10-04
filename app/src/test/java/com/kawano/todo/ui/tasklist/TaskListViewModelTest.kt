package com.kawano.todo.ui.tasklist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.fake.FakeTaskRepository
import com.kawano.todo.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val privateOld = Task(id = 1, title = "private-old", group = TaskGroup.PRIVATE, createdAt = 100)
    private val privateNew = Task(id = 2, title = "private-new", group = TaskGroup.PRIVATE, createdAt = 200)
    private val work = Task(id = 3, title = "work", group = TaskGroup.WORK, createdAt = 150)
    private val privateDone = Task(
        id = 4, title = "private-done", group = TaskGroup.PRIVATE,
        isCompleted = true, createdAt = 50, completedAt = 300,
    )

    private fun createViewModel(
        tasks: List<Task> = listOf(privateOld, privateNew, work, privateDone),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): Pair<TaskListViewModel, FakeTaskRepository> {
        val repository = FakeTaskRepository(tasks)
        return TaskListViewModel(repository, savedStateHandle) to repository
    }

    /** 読み込み中 (isLoading) の状態を読み飛ばして最初のロード済み状態を返す。 */
    private suspend fun ReceiveTurbine<TaskListUiState>.awaitLoaded(): TaskListUiState {
        var item = awaitItem()
        while (item.isLoading) item = awaitItem()
        return item
    }

    @Test
    fun `初期状態はプライベートの未完了タスクを createdAt 降順で表示する`() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(TaskGroup.PRIVATE, state.selectedGroup)
            assertEquals(listOf(privateNew, privateOld), state.tasks)
            assertFalse(state.isEmpty)
        }
    }

    @Test
    fun `グループを切り替えると仕事のタスクが表示され SavedStateHandle に保存される`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val (viewModel, _) = createViewModel(savedStateHandle = savedStateHandle)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.selectGroup(TaskGroup.WORK)
            val state = awaitItem()
            assertEquals(TaskGroup.WORK, state.selectedGroup)
            assertEquals(listOf(work), state.tasks)

            viewModel.selectGroup(TaskGroup.PRIVATE)
            assertEquals(listOf(privateNew, privateOld), awaitItem().tasks)
        }
        assertEquals(TaskGroup.PRIVATE.name, savedStateHandle[TaskListViewModel.KEY_SELECTED_GROUP])
    }

    @Test
    fun `SavedStateHandle に保存済みのグループで復元される`() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(TaskListViewModel.KEY_SELECTED_GROUP to TaskGroup.WORK.name),
        )
        val (viewModel, _) = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals(TaskGroup.WORK, viewModel.uiState.value.selectedGroup)
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(TaskGroup.WORK, state.selectedGroup)
            assertEquals(listOf(work), state.tasks)
        }
    }

    @Test
    fun `完了するとリポジトリが更新され一覧から消え完了イベントが届く`() = runTest {
        val (viewModel, repository) = createViewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.events.test {
                viewModel.completeTask(privateNew.id)
                assertEquals(TaskListEvent.TaskCompleted(privateNew.id), awaitItem())
            }
            assertEquals(listOf(privateOld), awaitItem().tasks)
        }
        assertEquals(listOf(privateNew.id to true), repository.setCompletedCalls)
        assertTrue(repository.tasks.first { it.id == privateNew.id }.isCompleted)
    }

    @Test
    fun `元に戻すと未完了に戻り一覧に再表示される`() = runTest {
        val (viewModel, repository) = createViewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.completeTask(privateNew.id)
            assertEquals(listOf(privateOld), awaitItem().tasks)

            viewModel.undoComplete(privateNew.id)
            assertEquals(listOf(privateNew, privateOld), awaitItem().tasks)
        }
        assertEquals(
            listOf(privateNew.id to true, privateNew.id to false),
            repository.setCompletedCalls,
        )
        val restored = repository.tasks.first { it.id == privateNew.id }
        assertFalse(restored.isCompleted)
        assertEquals(null, restored.completedAt)
    }

    @Test
    fun `タスクが無いグループは空状態になる`() = runTest {
        val (viewModel, _) = createViewModel(tasks = listOf(privateOld))

        viewModel.uiState.test {
            assertFalse(awaitLoaded().isEmpty)

            viewModel.selectGroup(TaskGroup.WORK)
            val state = awaitItem()
            assertTrue(state.tasks.isEmpty())
            assertTrue(state.isEmpty)
        }
    }

    @Test
    fun `最後のタスクを完了すると空状態になる`() = runTest {
        val (viewModel, _) = createViewModel(tasks = listOf(privateOld))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.completeTask(privateOld.id)
            assertTrue(awaitItem().isEmpty)
        }
    }

    @Test
    fun `読み込み前は空状態を表示しない`() {
        val state = TaskListUiState()
        assertTrue(state.isLoading)
        assertFalse(state.isEmpty)
    }
}
