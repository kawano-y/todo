package com.kawano.todo.fake

import android.net.Uri
import com.kawano.todo.domain.image.ImageStorage

/**
 * ViewModel テスト用の ImageStorage。
 *
 * 注意: ローカルユニットテストでは android.net.Uri は実装を持たない（`Uri.parse()` は null を返す）。
 * テストで Uri が必要なら `io.mockk.mockk<Uri>()` を使うこと。
 * そのため save の戻り値は URI の中身に依存せず、呼び出し順の連番パス
 * `"/fake/images/image_<n>.jpg"` を返す。
 *
 * - [savedUris]: save に渡された Uri（引数の同一性検証用）
 * - [savedPaths]: save が返したパス
 * - [deletedPaths]: delete に渡されたパス
 * - [failOnSave] を true にすると save が例外を投げる
 */
class FakeImageStorage : ImageStorage {
    val savedUris = mutableListOf<Uri>()
    val savedPaths = mutableListOf<String>()
    val deletedPaths = mutableListOf<String>()
    var failOnSave: Boolean = false

    override suspend fun save(uri: Uri): String {
        if (failOnSave) throw java.io.IOException("FakeImageStorage: save failed")
        savedUris += uri
        val path = "/fake/images/image_${savedPaths.size + 1}.jpg"
        savedPaths += path
        return path
    }

    override suspend fun delete(path: String) {
        deletedPaths += path
    }
}
