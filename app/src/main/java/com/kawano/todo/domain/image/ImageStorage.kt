package com.kawano.todo.domain.image

import android.net.Uri

interface ImageStorage {
    suspend fun save(uri: Uri): String   // コピー後の絶対パスを返す
    suspend fun delete(path: String)
}
