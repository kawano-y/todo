package com.kawano.todo.data

import app.cash.turbine.test
import com.kawano.todo.data.local.TaskDao
import com.kawano.todo.data.local.TaskEntity
import com.kawano.todo.data.local.toDomain
import com.kawano.todo.data.local.toEntity
import com.kawano.todo.data.repository.TaskRepositoryImpl
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [TaskRepositoryImpl] のロジック（Entity 変換・upsert 分岐・completedAt 設定）を JVM 上で検証する。
 * DAO は SQL の意味を模した in-memory フェイクで置き換える（実 SQL は androidTest の TaskDaoTest で検証）。
 */
class TaskRepositoryImplTest {

    private lateinit var dao: FakeTaskDao
    private var clock = 1_000L
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeTaskDao()
        repository = TaskRepositoryImpl(dao, now = { clock })
    }

    private fun task(
        title: String,
        group: TaskGroup = TaskGroup.PRIVATE,
        createdAt: Long = 0L,
        id: Long = 0L,
    ) = Task(id = id, title = title, group = group, createdAt = createdAt)

    @Test
    fun entityMapping_roundTripsAllFields() {
        val original = Task(
            id = 7,
            title = "t",
            note = "n",
            group = TaskGroup.WORK,
            imagePath = "/data/images/a.jpg",
            isCompleted = true,
            createdAt = 10,
            completedAt = 20,
        )
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun upsert_newTask_insertsAndReturnsGeneratedId() = runTest {
        val id1 = repository.upsert(task("a"))
        val id2 = repository.upsert(task("b"))

        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals("a", repository.getTask(id1)?.title)
        assertEquals(id2, repository.getTask(id2)?.id)
    }

    @Test
    fun upsert_existingTask_updatesAndReturnsSameId() = runTest {
        val id = repository.upsert(task("before", note = "x"))
        val loaded = repository.getTask(id)!!

        val returned = repository.upsert(loaded.copy(title = "after", imagePath = "/p.jpg"))

        assertEquals(id, returned)
        assertEquals(1, dao.rows.value.size)
        val updated = repository.getTask(id)!!
        assertEquals("after", updated.title)
        assertEquals("/p.jpg", updated.imagePath)
    }

    @Test
    fun upsert_unknownNonZeroId_insertsWithThatId() = runTest {
        val returned = repository.upsert(task("x", id = 42))

        assertEquals(42L, returned)
        assertEquals("x", repository.getTask(42)?.title)
    }

    @Test
    fun getTask_missing_returnsNull() = runTest {
        assertNull(repository.getTask(99))
    }

    @Test
    fun setCompleted_true_setsCompletedAtFromClock_false_clearsIt() = runTest {
        val id = repository.upsert(task("a"))

        clock = 5_000L
        repository.setCompleted(id, true)
        repository.getTask(id)!!.let {
            assertTrue(it.isCompleted)
            assertEquals(5_000L, it.completedAt)
        }

        repository.setCompleted(id, false)
        repository.getTask(id)!!.let {
            assertEquals(false, it.isCompleted)
            assertNull(it.completedAt)
        }
    }

    @Test
    fun observe_filtersByGroupAndCompletion_andSorts() = runTest {
        val old = repository.upsert(task("old", createdAt = 100))
        val new = repository.upsert(task("new", createdAt = 200))
        repository.upsert(task("work", group = TaskGroup.WORK, createdAt = 300))
        val done1 = repository.upsert(task("done1", createdAt = 50))
        val done2 = repository.upsert(task("done2", createdAt = 60))
        clock = 2_000L; repository.setCompleted(done2, true)
        clock = 3_000L; repository.setCompleted(done1, true)

        assertEquals(
            listOf(new, old),
            repository.observeActiveTasks(TaskGroup.PRIVATE).first().map { it.id },
        )
        assertEquals(
            listOf(done1, done2),
            repository.observeCompletedTasks(TaskGroup.PRIVATE).first().map { it.id },
        )
        assertEquals(
            listOf("work"),
            repository.observeActiveTasks(TaskGroup.WORK).first().map { it.title },
        )
        assertTrue(repository.observeCompletedTasks(TaskGroup.WORK).first().isEmpty())
    }

    @Test
    fun observeActive_emitsUpdatesOnCompleteAndDelete() = runTest {
        repository.observeActiveTasks(TaskGroup.PRIVATE).test {
            assertEquals(emptyList<Task>(), awaitItem())

            val id = repository.upsert(task("a"))
            assertEquals(listOf(id), awaitItem().map { it.id })

            repository.setCompleted(id, true)
            assertEquals(emptyList<Task>(), awaitItem())

            repository.setCompleted(id, false)
            assertEquals(listOf(id), awaitItem().map { it.id })

            repository.delete(id)
            assertEquals(emptyList<Task>(), awaitItem())
            assertNull(repository.getTask(id))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_missingId_doesNothing() = runTest {
        repository.upsert(task("a"))
        repository.delete(999)
        assertEquals(1, dao.rows.value.size)
    }

    private fun task(title: String, note: String) =
        Task(title = title, note = note, group = TaskGroup.PRIVATE, createdAt = 0)
}

/** TaskDao の SQL 挙動を模したフェイク。 */
private class FakeTaskDao : TaskDao {
    val rows = MutableStateFlow<Map<Long, TaskEntity>>(emptyMap())
    private var lastRowId = 0L

    override fun observeActive(group: TaskGroup): Flow<List<TaskEntity>> = rows.map { all ->
        all.values.filter { it.group == group && !it.isCompleted }
            .sortedWith(compareByDescending<TaskEntity> { it.createdAt }.thenByDescending { it.id })
    }

    override fun observeCompleted(group: TaskGroup): Flow<List<TaskEntity>> = rows.map { all ->
        all.values.filter { it.group == group && it.isCompleted }
            .sortedWith(compareByDescending<TaskEntity> { it.completedAt }.thenByDescending { it.id })
    }

    override suspend fun getById(id: Long): TaskEntity? = rows.value[id]

    override suspend fun insert(task: TaskEntity): Long {
        val id = if (task.id == 0L) lastRowId + 1 else task.id
        require(id !in rows.value) { "UNIQUE constraint failed: tasks.id" }
        lastRowId = maxOf(lastRowId, id)
        rows.update { it + (id to task.copy(id = id)) }
        return id
    }

    override suspend fun update(task: TaskEntity): Int {
        if (task.id !in rows.value) return 0
        rows.update { it + (task.id to task) }
        return 1
    }

    override suspend fun updateCompleted(id: Long, completed: Boolean, completedAt: Long?): Int {
        val current = rows.value[id] ?: return 0
        rows.update { it + (id to current.copy(isCompleted = completed, completedAt = completedAt)) }
        return 1
    }

    override suspend fun deleteById(id: Long): Int {
        if (id !in rows.value) return 0
        rows.update { it - id }
        return 1
    }
}
