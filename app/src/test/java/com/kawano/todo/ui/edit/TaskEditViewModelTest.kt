package com.kawano.todo.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kawano.todo.domain.model.Task
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.fake.FakeImageStorage
import com.kawano.todo.fake.FakeTaskRepository
import com.kawano.todo.testutil.MainDispatcherRule
import com.kawano.todo.ui.navigation.EditArgs
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeTaskRepository()
    private val imageStorage = FakeImageStorage()

    private val pickedUri: Uri = mockk()
    private val uriMap = mapOf(PICKED_URI to pickedUri)

    private fun createViewModel(
        handle: SavedStateHandle = newTaskHandle(),
        now: Long = NOW,
    ) = TaskEditViewModel(
        savedStateHandle = handle,
        taskRepository = repository,
        imageStorage = imageStorage,
        now = { now },
        parseUri = { uriMap.getValue(it) },
    )

    private fun newTaskHandle(group: TaskGroup? = null) = SavedStateHandle(
        buildMap {
            put(EditArgs.TASK_ID, EditArgs.NEW_TASK_ID)
            if (group != null) put(EditArgs.GROUP, group.name)
        },
    )

    private fun editHandle(id: Long) = SavedStateHandle(mapOf(EditArgs.TASK_ID to id))

    private val existingTask = Task(
        id = 10,
        title = "既存タスク",
        note = "メモ",
        group = TaskGroup.WORK,
        imagePath = "/images/old.jpg",
        isCompleted = true,
        createdAt = 100L,
        completedAt = 200L,
    )

    @Test
    fun newTask_save_usesInitialGroupAndCreatedAt() = runTest {
        val vm = createViewModel(newTaskHandle(TaskGroup.WORK))
        assertEquals(TaskGroup.WORK, vm.uiState.value.group)
        assertTrue(vm.uiState.value.isNew)
        assertFalse(vm.uiState.value.isLoading)

        vm.events.test {
            vm.onTitleChange("  買い物  ")
            vm.onNoteChange("牛乳")
            vm.onSave()
            assertEquals(TaskEditEvent.Done, awaitItem())
        }

        val saved = repository.tasks.single()
        assertEquals("買い物", saved.title)
        assertEquals("牛乳", saved.note)
        assertEquals(TaskGroup.WORK, saved.group)
        assertEquals(NOW, saved.createdAt)
        assertNull(saved.imagePath)
        assertFalse(saved.isCompleted)
        assertTrue(imageStorage.savedPaths.isEmpty())
    }

    @Test
    fun newTask_withoutGroupArg_defaultsToPrivate() {
        val vm = createViewModel(newTaskHandle(group = null))
        assertEquals(TaskGroup.PRIVATE, vm.uiState.value.group)
    }

    @Test
    fun existingTask_loadsAndSavesKeepingCompletionState() = runTest {
        repository.setTasks(listOf(existingTask))
        val vm = createViewModel(editHandle(existingTask.id))

        val loaded = vm.uiState.value
        assertFalse(loaded.isLoading)
        assertFalse(loaded.isNew)
        assertEquals("既存タスク", loaded.title)
        assertEquals("メモ", loaded.note)
        assertEquals(TaskGroup.WORK, loaded.group)
        assertEquals("/images/old.jpg", loaded.previewImage)

        vm.events.test {
            vm.onTitleChange("編集後")
            vm.onGroupChange(TaskGroup.PRIVATE)
            vm.onSave()
            assertEquals(TaskEditEvent.Done, awaitItem())
        }

        val saved = repository.tasks.single()
        assertEquals(existingTask.id, saved.id)
        assertEquals("編集後", saved.title)
        assertEquals(TaskGroup.PRIVATE, saved.group)
        assertTrue(saved.isCompleted)
        assertEquals(200L, saved.completedAt)
        assertEquals(100L, saved.createdAt)
        assertEquals("/images/old.jpg", saved.imagePath)
        assertTrue(imageStorage.deletedPaths.isEmpty())
    }

    @Test
    fun existingTask_notFound_emitsDone() = runTest {
        val vm = createViewModel(editHandle(999))
        vm.events.test {
            assertEquals(TaskEditEvent.Done, awaitItem())
        }
        assertTrue(repository.upsertCalls.isEmpty())
    }

    @Test
    fun newTask_withImage_savesImageAndStoresPath() = runTest {
        val vm = createViewModel()
        vm.onTitleChange("画像つき")
        vm.onImagePicked(PICKED_URI)
        assertEquals(PICKED_URI, vm.uiState.value.previewImage)

        vm.events.test {
            vm.onSave()
            assertEquals(TaskEditEvent.Done, awaitItem())
        }

        assertSame(pickedUri, imageStorage.savedUris.single())
        val path = imageStorage.savedPaths.single()
        assertEquals(path, repository.tasks.single().imagePath)
        assertTrue(imageStorage.deletedPaths.isEmpty())
    }

    @Test
    fun replaceImage_deletesOldImage() = runTest {
        repository.setTasks(listOf(existingTask))
        val vm = createViewModel(editHandle(existingTask.id))

        vm.events.test {
            vm.onImagePicked(PICKED_URI)
            vm.onSave()
            assertEquals(TaskEditEvent.Done, awaitItem())
        }

        val newPath = imageStorage.savedPaths.single()
        assertEquals(newPath, repository.tasks.single().imagePath)
        assertEquals(listOf("/images/old.jpg"), imageStorage.deletedPaths)
    }

    @Test
    fun removeImage_deletesOldImageAndClearsPath() = runTest {
        repository.setTasks(listOf(existingTask))
        val vm = createViewModel(editHandle(existingTask.id))

        vm.onRemoveImage()
        assertNull(vm.uiState.value.previewImage)

        vm.events.test {
            vm.onSave()
            assertEquals(TaskEditEvent.Done, awaitItem())
        }

        assertNull(repository.tasks.single().imagePath)
        assertEquals(listOf("/images/old.jpg"), imageStorage.deletedPaths)
        assertTrue(imageStorage.savedPaths.isEmpty())
    }

    @Test
    fun blankTitle_cannotSave() = runTest {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.canSave)
        assertFalse(vm.uiState.value.showTitleError)

        vm.onTitleChange("   ")
        assertFalse(vm.uiState.value.canSave)
        assertTrue(vm.uiState.value.showTitleError)

        vm.events.test {
            vm.onSave()
            expectNoEvents()
        }
        assertTrue(repository.upsertCalls.isEmpty())
    }

    @Test
    fun imageSaveFailure_doesNotUpsertAndReenablesSave() = runTest {
        imageStorage.failOnSave = true
        val vm = createViewModel()
        vm.onTitleChange("タスク")
        vm.onImagePicked(PICKED_URI)

        vm.events.test {
            vm.onSave()
            assertEquals(TaskEditEvent.ImageSaveFailed, awaitItem())
        }

        assertTrue(repository.upsertCalls.isEmpty())
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.canSave)
        // 入力は保持される
        assertEquals(PICKED_URI, vm.uiState.value.newImageUri)
    }

    @Test
    fun restoresInputsFromSavedStateHandle() = runTest {
        repository.setTasks(listOf(existingTask))
        val handle = editHandle(existingTask.id)
        val first = createViewModel(handle)
        first.onTitleChange("入力途中")
        first.onNoteChange("メモ途中")
        first.onGroupChange(TaskGroup.PRIVATE)
        first.onRemoveImage()

        // プロセス再生成を想定: 同じ SavedStateHandle で ViewModel を作り直す
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val second = createViewModel(restoredHandle)

        val state = second.uiState.value
        assertFalse(state.isLoading)
        assertEquals("入力途中", state.title)
        assertEquals("メモ途中", state.note)
        assertEquals(TaskGroup.PRIVATE, state.group)
        assertTrue(state.removeImage)
        assertNull(state.previewImage)
    }

    @Test
    fun restoresPickedImageForNewTask() {
        val handle = newTaskHandle(TaskGroup.WORK)
        val first = createViewModel(handle)
        first.onTitleChange("新規途中")
        first.onImagePicked(PICKED_URI)

        val second = createViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertEquals("新規途中", second.uiState.value.title)
        assertEquals(PICKED_URI, second.uiState.value.previewImage)
        assertEquals(TaskGroup.WORK, second.uiState.value.group)
    }

    @Test
    fun upsertFailure_deletesNewlySavedImage() = runTest {
        val failingRepository = object : com.kawano.todo.domain.repository.TaskRepository by repository {
            override suspend fun upsert(task: Task): Long = throw IllegalStateException("db error")
        }
        val vm = TaskEditViewModel(
            savedStateHandle = newTaskHandle(),
            taskRepository = failingRepository,
            imageStorage = imageStorage,
            now = { NOW },
            parseUri = { uriMap.getValue(it) },
        )
        vm.onTitleChange("タスク")
        vm.onImagePicked(PICKED_URI)

        vm.events.test {
            vm.onSave()
            assertEquals(TaskEditEvent.TaskSaveFailed, awaitItem())
        }

        assertEquals(imageStorage.savedPaths, imageStorage.deletedPaths)
        assertFalse(vm.uiState.value.isSaving)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val PICKED_URI = "content://media/picker/0/1"
    }
}
