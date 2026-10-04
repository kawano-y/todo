package com.kawano.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.kawano.todo.R
import com.kawano.todo.ui.theme.TodoTheme
import java.io.File

/**
 * タスクに紐づく画像のサムネイル。imagePath が null の場合は何も表示しない。
 *
 * imagePath は通常 filesDir 内の絶対パス。暫定 ImageStorage が返す `content://` 等の URI 文字列も表示できる。
 * サイズは呼び出し側で modifier 指定可能（デフォルト 56dp 角）。
 */
@Composable
fun TaskThumbnail(
    imagePath: String?,
    modifier: Modifier = Modifier,
) {
    if (imagePath == null) return
    val model: Any = if (imagePath.startsWith("/")) File(imagePath) else imagePath
    SubcomposeAsyncImage(
        model = model,
        contentDescription = stringResource(R.string.cd_task_image),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp)),
        error = { ThumbnailPlaceholder() },
        loading = { ThumbnailPlaceholder(showIcon = false) },
    )
}

@Composable
private fun ThumbnailPlaceholder(showIcon: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (showIcon) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun TaskThumbnailPreview() {
    TodoTheme {
        TaskThumbnail(imagePath = "/nonexistent.jpg")
    }
}
