package com.kawano.todo.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.kawano.todo.domain.image.ImageStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 画像を `filesDir/images/<UUID>.jpg` にコピー保存する [ImageStorage] 実装。
 *
 * save:
 * 1. `inJustDecodeBounds` で寸法だけ取得
 * 2. [ImageScaling.calculateInSampleSize] で間引き率を決めてデコード
 * 3. 長辺 [MAX_LONG_EDGE] に収まるよう拡縮 + EXIF の回転/反転を補正（1 回の Matrix 変換）
 * 4. 一時ファイル (`.tmp`) に JPEG 品質 [JPEG_QUALITY] で書き込み → rename で確定
 *
 * 失敗時は一時ファイルを消したうえで例外（[IOException] など）を投げる。
 *
 * delete: パスを正規化し `images` ディレクトリ配下のファイルのみ削除する。
 * 存在しないパスはエラーにしない。配下外のパスは削除せず無視する（警告ログのみ）。
 */
class ImageStorageImpl(
    private val contentResolver: ContentResolver,
    private val imagesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ImageStorage {

    constructor(context: Context) : this(
        contentResolver = context.contentResolver,
        imagesDir = File(context.filesDir, IMAGES_DIR_NAME),
    )

    override suspend fun save(uri: Uri): String = withContext(ioDispatcher) {
        val bitmap = try {
            decodeNormalizedBitmap(uri)
        } catch (e: OutOfMemoryError) {
            throw IOException("Out of memory while decoding image: $uri", e)
        }
        try {
            writeJpegAtomically(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun delete(path: String) = withContext(ioDispatcher) {
        val target = File(path)
        if (!ImageScaling.isInsideDirectory(imagesDir, target)) {
            Log.w(TAG, "Refused to delete a file outside of $imagesDir: $path")
            return@withContext
        }
        val canonical = target.canonicalFile
        if (!canonical.isFile) return@withContext // 存在しない（or ディレクトリ）なら何もしない
        if (!canonical.delete() && canonical.exists()) {
            throw IOException("Failed to delete image: $path")
        }
    }

    /** デコード → 縮小 → EXIF 補正済みの Bitmap を返す。 */
    private fun decodeNormalizedBitmap(uri: Uri): Bitmap {
        // 1. 寸法だけ読む
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Not a decodable image: $uri")
        }

        // 2. inSampleSize で間引いてデコード
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageScaling.calculateInSampleSize(
                bounds.outWidth, bounds.outHeight, MAX_LONG_EDGE,
            )
        }
        val decoded = openStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Failed to decode image: $uri")

        // 3. EXIF の向き（読めなければ補正なし）
        val orientation = try {
            openStream(uri).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read EXIF: $uri", e)
            ExifInterface.ORIENTATION_NORMAL
        }
        val transform = ImageScaling.exifTransform(orientation)

        // 4. 長辺 MAX_LONG_EDGE に収める拡縮 + 回転/反転
        val scale = ImageScaling.scaleFactor(decoded.width, decoded.height, MAX_LONG_EDGE)
        if (scale == 1f && transform.isIdentity) return decoded

        val matrix = Matrix().apply {
            setScale(scale, scale)
            if (transform.rotationDegrees != 0) postRotate(transform.rotationDegrees.toFloat())
            if (transform.flipHorizontal) postScale(-1f, 1f)
        }
        val result = try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (e: Throwable) {
            decoded.recycle()
            throw e
        }
        if (result !== decoded) decoded.recycle()
        return result
    }

    /** 一時ファイルに書いてから rename する。失敗時は一時ファイルを残さない。 */
    private fun writeJpegAtomically(bitmap: Bitmap): String {
        if (!imagesDir.isDirectory && !imagesDir.mkdirs() && !imagesDir.isDirectory) {
            throw IOException("Failed to create directory: $imagesDir")
        }
        val name = "${UUID.randomUUID()}.jpg"
        val tmp = File(imagesDir, "$name$TMP_SUFFIX")
        val dest = File(imagesDir, name)
        try {
            FileOutputStream(tmp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IOException("Failed to encode JPEG")
                }
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(dest)) {
                throw IOException("Failed to rename $tmp to $dest")
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
        return dest.absolutePath
    }

    private fun openStream(uri: Uri): InputStream =
        contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open input stream: $uri")

    companion object {
        const val IMAGES_DIR_NAME = "images"
        const val MAX_LONG_EDGE = 2048
        const val JPEG_QUALITY = 85
        private const val TMP_SUFFIX = ".tmp"
        private const val TAG = "ImageStorageImpl"
    }
}

/** EXIF の向きを表す変換。回転（時計回り）→ 左右反転 の順に適用する。 */
data class ExifTransform(
    val rotationDegrees: Int,
    val flipHorizontal: Boolean,
) {
    val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal

    /** 変換後に幅と高さが入れ替わるか。 */
    val swapsDimensions: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270
}

/** Android API に依存しない純粋関数群（JVM ユニットテスト対象）。 */
object ImageScaling {

    /**
     * デコード後の長辺が [maxLongEdge] 以上を保てる最大の 2 の累乗を返す。
     * （最終的な縮小は [scaleFactor] で行うため、ここでは画質を落としすぎない側に倒す）
     */
    fun calculateInSampleSize(width: Int, height: Int, maxLongEdge: Int): Int {
        require(maxLongEdge > 0) { "maxLongEdge must be positive: $maxLongEdge" }
        val longEdge = max(width, height)
        if (longEdge <= maxLongEdge) return 1
        var sample = 1
        while (longEdge / (sample * 2) >= maxLongEdge) {
            sample *= 2
        }
        return sample
    }

    /** 長辺を [maxLongEdge] 以下にするための倍率（1 以下）。既に収まっていれば 1。 */
    fun scaleFactor(width: Int, height: Int, maxLongEdge: Int): Float {
        require(maxLongEdge > 0) { "maxLongEdge must be positive: $maxLongEdge" }
        val longEdge = max(width, height)
        if (longEdge <= maxLongEdge) return 1f
        return maxLongEdge.toFloat() / longEdge
    }

    /** [scaleFactor] 適用後の (幅, 高さ)。各辺 1 以上。 */
    fun scaledSize(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
        val scale = scaleFactor(width, height, maxLongEdge)
        if (scale == 1f) return width to height
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** EXIF Orientation 値 (1〜8) を回転・反転に変換する。未知の値は無変換。 */
    fun exifTransform(orientation: Int): ExifTransform = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0, true)
        ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180, false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180, true)
        ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90, true)
        ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90, false)
        ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270, true)
        ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270, false)
        else -> ExifTransform(0, false)
    }

    /** [file] を正規化したパスが [dir] 配下（dir 自身は含まない）にあるか。 */
    fun isInsideDirectory(dir: File, file: File): Boolean {
        val dirPath = try {
            dir.canonicalPath
        } catch (e: IOException) {
            return false
        }
        val filePath = try {
            file.canonicalPath
        } catch (e: IOException) {
            return false
        }
        return filePath.startsWith(dirPath + File.separator)
    }
}
