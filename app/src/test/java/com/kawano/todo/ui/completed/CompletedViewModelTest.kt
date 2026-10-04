package com.kawano.todo.ui.completed

import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.kawano.todo.domain.image.ImageStorage
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.domain.repository.TaskRepository
import com.kawano.todo.fake.FakeImageStorage
import com.kawano.todo.fake.FakeTaskRepository
import com.kawano.todo.testutil.MainDispatcherRule
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompletedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun completed(
        id: Long,
        group: TaskGroup,
        completedAt: Long,
        imagePath: String? = null,
    ) = Task(
        id = id,
        title = "task$id",
        group = group,
        imagePath = imagePath,
        isCompleted = true,
        createdAt = 1,
        completedAt = completedAt,
    )

    private fun active(id: Long, group: TaskGroup) =
        Task(id = id, title = "task$id", group = group, createdAt = 1)

    private val defaultTasks = listOf(
        completed(1, TaskGroup.PRIVATE, completedAt = 100),
        completed(2, TaskGroup.PRIVATE, completedAt = 300),
        completed(3, TaskGroup.PRIVATE, completedAt = 200, imagePath = "/images/3.jpg"),
        completed(4, TaskGroup.WORK, completedAt = 500),
        active(5, TaskGroup.PRIVATE),
    )

    private fun createViewModel(
        repository: TaskRepository = FakeTaskRepository(defaultTasks),
        imageStorage: ImageStorage = FakeImageStorage(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = CompletedViewModel(repository, imageStorage, savedStateHandle)

    /** 条件を満たす状態が来るまで読み飛ばす。 */
    private suspend fun ReceiveTurbine<CompletedUiState>.awaitUntil(
        predicate: (CompletedUiState) -> Boolean,
    ): CompletedUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private suspend fun ReceiveTurbine<CompletedUiState>.awaitLoaded() = awaitUntil { !it.isLoading }

    @Test
    fun initialState_showsPrivateCompletedTasksSortedByCompletedAtDesc() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(TaskGroup.PRIVATE, state.selectedGroup)
            assertEquals(listOf(2L, 3L, 1L), state.tasks.map { it.id })
            assertFalse(state.showEmpty)
        }
    }

    @Test
    fun tasksAreSortedByCompletedAtDesc_evenIfRepositoryReturnsUnsorted() = runTest {
        val fake = FakeTaskRepository(defaultTasks)
        val unsorted = object : TaskRepository by fake {
            override fun observeCompletedTasks(group: TaskGroup): Flow<List<Task>> =
                flow { fake.observeCompletedTasks(group).collect { emit(it.reversed()) } }
        }
        val viewModel = createViewModel(repository = unsorted)

        viewModel.uiState.test {
            assertEquals(listOf(2L, 3L, 1L), awaitLoaded().tasks.map { it.id })
        }
    }

    @Test
    fun selectGroup_switchesListAndSavesToSavedStateHandle() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.selectGroup(TaskGroup.WORK)
            val state = awaitUntil { it.selectedGroup == TaskGroup.WORK && !it.isLoading }
            assertEquals(listOf(4L), state.tasks.map { it.id })
        }
        assertEquals(TaskGroup.WORK.name, handle.get<String>(CompletedViewModel.KEY_SELECTED_GROUP))
    }

    @Test
    fun selectedGroup_isRestoredFromSavedStateHandle() = runTest {
        val handle = SavedStateHandle(mapOf(CompletedViewModel.KEY_SELECTED_GROUP to TaskGroup.WORK.name))
        val viewModel = createViewModel(savedStateHandle = handle)

        assertEquals(TaskGroup.WORK, viewModel.uiState.value.selectedGroup)
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(TaskGroup.WORK, state.selectedGroup)
            assertEquals(listOf(4L), state.tasks.map { it.id })
        }
    }

    @Test
    fun invalidSavedGroup_fallsBackToPrivate() = runTest {
        val handle = SavedStateHandle(mapOf(CompletedViewModel.KEY_SELECTED_GROUP to "UNKNOWN"))
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.uiState.test {
            assertEquals(TaskGroup.PRIVATE, awaitLoaded().selectedGroup)
        }
    }

    @Test
    fun restore_setsNotCompletedAndPostsMessage() = runTest {
        val repository = FakeTaskRepository(defaultTasks)
        val viewModel = createViewModel(repository = repository)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.restore(2)
            val state = awaitUntil { it.userMessage != null }
            assertEquals(listOf(3L, 1L), state.tasks.map { it.id })
            val message = state.userMessage as CompletedMessage.Restored
            assertEquals("task2", message.title)

            viewModel.onMessageShown(message.id)
            assertNull(awaitUntil { it.userMessage == null }.userMessage)
        }
        assertEquals(listOf(2L to false), repository.setCompletedCalls)
        assertFalse(repository.getTask(2)!!.isCompleted)
        assertNull(repository.getTask(2)!!.completedAt)
    }

    @Test
    fun delete_requiresConfirmation() = runTest {
        val repository = FakeTaskRepository(defaultTasks)
        val viewModel = createViewModel(repository = repository)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.requestDelete(2)
            assertEquals(2L, awaitUntil { it.pendingDelete != null }.pendingDelete!!.id)

            viewModel.dismissDelete()
            assertNull(awaitUntil { it.pendingDelete == null }.pendingDelete)
        }
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun confirmDelete_withoutImage_deletesTaskOnly() = runTest {
        val repository = FakeTaskRepository(defaultTasks)
        val imageStorage = FakeImageStorage()
        val viewModel = createViewModel(repository = repository, imageStorage = imageStorage)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.requestDelete(1)
            awaitUntil { it.pendingDelete != null }
            viewModel.confirmDelete()
            val state = awaitUntil { it.userMessage is CompletedMessage.Deleted }
            assertNull(state.pendingDelete)
            assertEquals(listOf(2L, 3L), state.tasks.map { it.id })
        }
        assertEquals(listOf(1L), repository.deletedIds)
        assertTrue(imageStorage.deletedPaths.isEmpty())
    }

    @Test
    fun confirmDelete_withImage_deletesTaskAndImage() = runTest {
        val repository = FakeTaskRepository(defaultTasks)
        val imageStorage = FakeImageStorage()
        val viewModel = createViewModel(repository = repository, imageStorage = imageStorage)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.requestDelete(3)
            awaitUntil { it.pendingDelete != null }
            viewModel.confirmDelete()
            val state = awaitUntil { it.userMessage is CompletedMessage.Deleted }
            assertEquals(listOf(2L, 1L), state.tasks.map { it.id })
        }
        assertEquals(listOf(3L), repository.deletedIds)
        assertEquals(listOf("/images/3.jpg"), imageStorage.deletedPaths)
    }

    @Test
    fun confirmDelete_imageDeleteFailure_stillTreatsTaskDeletionAsSuccess() = runTest {
        val repository = FakeTaskRepository(defaultTasks)
        val failingStorage = object : ImageStorage {
            override suspend fun save(uri: Uri): String = error("unused")
            override suspend fun delete(path: String) = throw java.io.IOException("boom")
        }
        val viewModel = createViewModel(repository = repository, imageStorage = failingStorage)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.requestDelete(3)
            awaitUntil { it.pendingDelete != null }
            viewModel.confirmDelete()
            val state = awaitUntil { it.userMessage != null }
            assertTrue(state.userMessage is CompletedMessage.Deleted)
            assertEquals(listOf(2L, 1L), state.tasks.map { it.id })
        }
        assertEquals(listOf(3L), repository.deletedIds)
    }

    @Test
    fun confirmDelete_withoutPending_doesNothing() = runTest {
        val repository = FakeTaskRepository(defaultTasks)
        val viewModel = createViewModel(repository = repository)

        viewModel.confirmDelete()

        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun emptyGroup_showsEmptyState() = runTest {
        val repository = FakeTaskRepository(listOf(completed(1, TaskGroup.PRIVATE, completedAt = 100)))
        val viewModel = createViewModel(repository = repository)

        viewModel.uiState.test {
            assertFalse(awaitLoaded().showEmpty)
            viewModel.selectGroup(TaskGroup.WORK)
            val state = awaitUntil { it.selectedGroup == TaskGroup.WORK && !it.isLoading }
            assertTrue(state.tasks.isEmpty())
            assertTrue(state.showEmpty)
        }
    }

    @Test
    fun whileLoading_emptyStateIsNotShown() = runTest {
        val fake = FakeTaskRepository(defaultTasks)
        val neverEmits = object : TaskRepository by fake {
            override fun observeCompletedTasks(group: TaskGroup): Flow<List<Task>> =
                flow { awaitCancellation() }
        }
        val viewModel = createViewModel(repository = neverEmits)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertFalse(state.showEmpty)
            expectNoEvents()
        }
    }
}
