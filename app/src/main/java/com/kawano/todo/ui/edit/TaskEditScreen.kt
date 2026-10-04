package com.kawano.todo.ui.edit

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kawano.todo.R
import com.kawano.todo.domain.model.TaskGroup
import com.kawano.todo.ui.components.labelRes
import com.kawano.todo.ui.theme.TodoTheme
import java.io.File

/**
 * タスク作成・編集画面。taskId / 初期 group は SavedStateHandle から取得する（[com.kawano.todo.ui.navigation.EditArgs]）。
 */
@Composable
fun TaskEditScreen(
    onDone: () -> Unit,
    viewModel: TaskEditViewModel = viewModel(factory = TaskEditViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    TaskEditEvent.Done -> currentOnDone()
                    TaskEditEvent.ImageSaveFailed ->
                        snackbarHostState.showSnackbar(context.getString(R.string.edit_error_image_save))
                    TaskEditEvent.TaskSaveFailed ->
                        snackbarHostState.showSnackbar(context.getString(R.string.edit_error_task_save))
                }
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri.toString())
    }

    TaskEditContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onDone,
        onSave = viewModel::onSave,
        onTitleChange = viewModel::onTitleChange,
        onNoteChange = viewModel::onNoteChange,
        onGroupChange = viewModel::onGroupChange,
        onPickImage = {
            pickImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        },
        onRemoveImage = viewModel::onRemoveImage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditContent(
    state: TaskEditUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onGroupChange: (TaskGroup) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.edit_screen_title_new else R.string.edit_screen_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = onSave, enabled = state.canSave) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GroupSelector(
                selected = state.group,
                onSelect = onGroupChange,
                enabled = !state.isSaving,
            )
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.edit_label_title)) },
                singleLine = true,
                isError = state.showTitleError,
                supportingText = if (state.showTitleError) {
                    { Text(stringResource(R.string.edit_error_title_required)) }
                } else {
                    null
                },
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.edit_label_note)) },
                minLines = 3,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            ImageSection(
                previewImage = state.previewImage,
                enabled = !state.isSaving,
                onPickImage = onPickImage,
                onRemoveImage = onRemoveImage,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSelector(
    selected: TaskGroup,
    onSelect: (TaskGroup) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.edit_label_group),
            style = MaterialTheme.typography.labelLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val groups = TaskGroup.entries
            groups.forEachIndexed { index, group ->
                SegmentedButton(
                    selected = group == selected,
                    onClick = { onSelect(group) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = groups.size),
                    label = { Text(stringResource(group.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun ImageSection(
    previewImage: String?,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.edit_label_image),
            style = MaterialTheme.typography.labelLarge,
        )
        if (previewImage == null) {
            OutlinedButton(onClick = onPickImage, enabled = enabled) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit_action_attach_image))
            }
        } else {
            val model: Any = if (previewImage.startsWith("/")) File(previewImage) else previewImage
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.cd_task_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickImage, enabled = enabled) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_action_replace_image))
                }
                OutlinedButton(onClick = onRemoveImage, enabled = enabled) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "新規")
@Composable
private fun TaskEditContentNewPreview() {
    TodoTheme {
        TaskEditContent(
            state = TaskEditUiState(isNew = true, group = TaskGroup.WORK),
            onBack = {},
            onSave = {},
            onTitleChange = {},
            onNoteChange = {},
            onGroupChange = {},
            onPickImage = {},
            onRemoveImage = {},
        )
    }
}

@Preview(showBackground = true, name = "既存・画像あり")
@Composable
private fun TaskEditContentExistingPreview() {
    TodoTheme {
        TaskEditContent(
            state = TaskEditUiState(
                isNew = false,
                title = "牛乳を買う",
                note = "低脂肪のもの\n2 本",
                group = TaskGroup.PRIVATE,
                existingImagePath = "/data/user/0/com.kawano.todo/files/images/sample.jpg",
            ),
            onBack = {},
            onSave = {},
            onTitleChange = {},
            onNoteChange = {},
            onGroupChange = {},
            onPickImage = {},
            onRemoveImage = {},
        )
    }
}

@Preview(showBackground = true, name = "ダーク・タイトル未入力", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskEditContentDarkPreview() {
    TodoTheme(darkTheme = true) {
        TaskEditContent(
            state = TaskEditUiState(isNew = true, title = "", titleTouched = true),
            onBack = {},
            onSave = {},
            onTitleChange = {},
            onNoteChange = {},
            onGroupChange = {},
            onPickImage = {},
            onRemoveImage = {},
        )
    }
}
