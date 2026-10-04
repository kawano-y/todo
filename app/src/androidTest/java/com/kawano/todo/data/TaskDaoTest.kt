package com.kawano.todo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.kawano.todo.data.local.TaskDao
import com.kawano.todo.data.local.TaskEntity
import com.kawano.todo.data.local.TodoDatabase
import com.kawano.todo.data.repository.TaskRepositoryImpl
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** in-memory Room で [TaskDao] と [TaskRepositoryImpl] を検証する instrumented テスト。 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: TodoDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        title: String,
        group: TaskGroup = TaskGroup.PRIVATE,
        createdAt: Long = 0,
        isCompleted: Boolean = false,
        completedAt: Long? = null,
        imagePath: String? = null,
    ) = TaskEntity(
        title = title,
        note = "",
        group = group,
        imagePath = imagePath,
        isCompleted = isCompleted,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    @Test
    fun insertAndGet_roundTripsAllFields() = runTest {
        val id = dao.insert(
            entity("t", group = TaskGroup.WORK, createdAt = 10, imagePath = "/img.jpg")
                .copy(note = "memo")
        )

        val loaded = dao.getById(id)!!
        assertEquals(id, loaded.id)
        assertEquals("t", loaded.title)
        assertEquals("memo", loaded.note)
        assertEquals(TaskGroup.WORK, loaded.group)
        assertEquals("/img.jpg", loaded.imagePath)
        assertFalse(loaded.isCompleted)
        assertEquals(10L, loaded.createdAt)
        assertNull(loaded.completedAt)
    }

    @Test
    fun getById_missing_returnsNull() = runTest {
        assertNull(dao.getById(123))
    }

    @Test
    fun observeActive_filtersByGroupAndSortsByCreatedAtDesc() = runTest {
        val a = dao.insert(entity("a", createdAt = 100))
        val b = dao.insert(entity("b", createdAt = 300))
        val c = dao.insert(entity("c", createdAt = 200))
        dao.insert(entity("work", group = TaskGroup.WORK, createdAt = 400))
        dao.insert(entity("done", createdAt = 500, isCompleted = true, completedAt = 600))

        val result = dao.observeActive(TaskGroup.PRIVATE).first()

        assertEquals(listOf(b, c, a), result.map { it.id })
    }

    @Test
    fun observeCompleted_filtersByGroupAndSortsByCompletedAtDesc() = runTest {
        val a = dao.insert(entity("a", isCompleted = true, completedAt = 1_000))
        val b = dao.insert(entity("b", isCompleted = true, completedAt = 3_000))
        val c = dao.insert(entity("c", isCompleted = true, completedAt = 2_000))
        dao.insert(entity("active"))
        dao.insert(entity("work", group = TaskGroup.WORK, isCompleted = true, completedAt = 9_000))

        val result = dao.observeCompleted(TaskGroup.PRIVATE).first()

        assertEquals(listOf(b, c, a), result.map { it.id })
    }

    @Test
    fun update_existing_returnsOne_missing_returnsZero() = runTest {
        val id = dao.insert(entity("before"))

        assertEquals(1, dao.update(dao.getById(id)!!.copy(title = "after")))
        assertEquals("after", dao.getById(id)!!.title)
        assertEquals(0, dao.update(entity("ghost").copy(id = 999)))
    }

    @Test
    fun updateCompleted_and_deleteById() = runTest {
        val id = dao.insert(entity("a"))

        dao.updateCompleted(id, completed = true, completedAt = 42)
        dao.getById(id)!!.let {
            assertTrue(it.isCompleted)
            assertEquals(42L, it.completedAt)
        }

        dao.updateCompleted(id, completed = false, completedAt = null)
        dao.getById(id)!!.let {
            assertFalse(it.isCompleted)
            assertNull(it.completedAt)
        }

        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
        assertEquals(0, dao.deleteById(id))
    }

    @Test
    fun observeActive_emitsOnChanges() = runTest {
        dao.observeActive(TaskGroup.PRIVATE).test {
            assertEquals(emptyList<TaskEntity>(), awaitItem())

            val id = dao.insert(entity("a"))
            assertEquals(listOf(id), awaitItem().map { it.id })

            dao.updateCompleted(id, completed = true, completedAt = 1)
            assertEquals(emptyList<TaskEntity>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repository_endToEnd() = runTest {
        val repository = TaskRepositoryImpl(dao, now = { 7_777L })

        val id = repository.upsert(Task(title = "a", group = TaskGroup.WORK, createdAt = 1))
        assertEquals(id, repository.upsert(repository.getTask(id)!!.copy(title = "b")))
        assertEquals("b", repository.getTask(id)!!.title)

        repository.setCompleted(id, true)
        assertEquals(
            listOf(7_777L),
            repository.observeCompletedTasks(TaskGroup.WORK).first().map { it.completedAt },
        )
        assertTrue(repository.observeActiveTasks(TaskGroup.WORK).first().isEmpty())

        repository.setCompleted(id, false)
        assertNull(repository.getTask(id)!!.completedAt)

        repository.delete(id)
        assertNull(repository.getTask(id))
    }
}
