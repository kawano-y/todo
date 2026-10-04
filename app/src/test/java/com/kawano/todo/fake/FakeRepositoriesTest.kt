package com.kawano.todo.fake

import android.net.Uri
import app.cash.turbine.test
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T0 で用意したフェイク自体の動作確認。 */
class FakeRepositoriesTest {

    private fun task(id: Long, group: TaskGroup, createdAt: Long, completedAt: Long? = null) = Task(
        id = id,
        title = "task$id",
        group = group,
        createdAt = createdAt,
        isCompleted = completedAt != null,
        completedAt = completedAt,
    )

    @Test
    fun observeActiveTasks_filtersByGroupAndSortsByCreatedAtDesc() = runTest {
        val repo = FakeTaskRepository(
            listOf(
                task(1, TaskGroup.PRIVATE, createdAt = 100),
                task(2, TaskGroup.PRIVATE, createdAt = 300),
                task(3, TaskGroup.WORK, createdAt = 200),
                task(4, TaskGroup.PRIVATE, createdAt = 400, completedAt = 500),
            ),
        )

        repo.observeActiveTasks(TaskGroup.PRIVATE).test {
            assertEquals(listOf(2L, 1L), awaitItem().map { it.id })
        }
    }

    @Test
    fun setCompleted_movesTaskToCompletedAndBack() = runTest {
        val repo = FakeTaskRepository(listOf(task(1, TaskGroup.WORK, createdAt = 100)), now = { 999L })

        repo.observeCompletedTasks(TaskGroup.WORK).test {
            assertEquals(emptyList<Task>(), awaitItem())

            repo.setCompleted(1, true)
            val completed = awaitItem().single()
            assertTrue(completed.isCompleted)
            assertEquals(999L, completed.completedAt)

            repo.setCompleted(1, false)
            assertEquals(emptyList<Task>(), awaitItem())
        }
        assertNull(repo.getTask(1)!!.completedAt)
        assertEquals(listOf(1L to true, 1L to false), repo.setCompletedCalls)
    }

    @Test
    fun upsert_assignsNewIdAndUpdatesExisting() = runTest {
        val repo = FakeTaskRepository()
        val id = repo.upsert(Task(title = "new", group = TaskGroup.PRIVATE, createdAt = 1))
        assertEquals(1L, id)

        repo.upsert(repo.getTask(id)!!.copy(title = "edited"))
        assertEquals("edited", repo.getTask(id)!!.title)
        assertEquals(1, repo.tasks.size)

        repo.delete(id)
        assertNull(repo.getTask(id))
        assertEquals(listOf(1L), repo.deletedIds)
    }

    @Test
    fun fakeImageStorage_recordsSavedAndDeletedPaths() = runTest {
        val storage = FakeImageStorage()
        val uri = mockk<Uri>()

        val path = storage.save(uri)
        storage.delete(path)

        assertEquals(listOf(uri), storage.savedUris)
        assertEquals(listOf(path), storage.savedPaths)
        assertEquals(listOf(path), storage.deletedPaths)
    }
}
