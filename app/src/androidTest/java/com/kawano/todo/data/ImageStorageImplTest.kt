package com.kawano.todo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kawano.todo.data.image.ImageStorageImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * 実際の BitmapFactory / ExifInterface / ContentResolver を使った ImageStorageImpl の検証。
 * テスト用の画像ディレクトリ（filesDir/images_test）を使い、本番の images には触れない。
 */
@RunWith(AndroidJUnit4::class)
class ImageStorageImplTest {

    private lateinit var context: Context
    private lateinit var imagesDir: File
    private lateinit var srcDir: File
    private lateinit var storage: ImageStorageImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imagesDir = File(context.filesDir, "images_test").apply { deleteRecursively() }
        srcDir = File(context.cacheDir, "image_storage_test_src").apply {
            deleteRecursively()
            mkdirs()
        }
        storage = ImageStorageImpl(context.contentResolver, imagesDir)
    }

    @After
    fun tearDown() {
        imagesDir.deleteRecursively()
        srcDir.deleteRecursively()
    }

    private fun writeJpeg(name: String, width: Int, height: Int, orientation: Int? = null): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val file = File(srcDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (orientation != null) {
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    private fun boundsOf(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return opts.outWidth to opts.outHeight
    }

    private fun savedFiles(): List<File> = imagesDir.listFiles()?.toList().orEmpty()

    @Test
    fun save_smallImage_keepsSize_andReturnsAbsolutePathInImagesDir() = runTest {
        val path = storage.save(writeJpeg("small.jpg", 640, 480))

        val file = File(path)
        assertTrue(file.isAbsolute)
        assertTrue(file.isFile)
        assertEquals(imagesDir.canonicalPath, file.parentFile!!.canonicalPath)
        assertTrue(file.name.endsWith(".jpg"))
        assertEquals(640 to 480, boundsOf(path))
        assertEquals(listOf(file.name), savedFiles().map { it.name })
    }

    @Test
    fun save_largeImage_isScaledToLongEdge2048() = runTest {
        val path = storage.save(writeJpeg("large.jpg", 4096, 3072))

        assertEquals(2048 to 1536, boundsOf(path))
    }

    @Test
    fun save_appliesExifRotation() = runTest {
        val path = storage.save(
            writeJpeg("rotated.jpg", 400, 200, ExifInterface.ORIENTATION_ROTATE_90),
        )

        assertEquals(200 to 400, boundsOf(path))
        // 保存後の JPEG には向き情報が無い（= 画素自体が回転済み）
        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )
        assertTrue(
            orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED,
        )
    }

    @Test
    fun save_largeRotatedImage_isScaledAndRotated() = runTest {
        val path = storage.save(
            writeJpeg("large_rotated.jpg", 4096, 2048, ExifInterface.ORIENTATION_ROTATE_270),
        )

        assertEquals(1024 to 2048, boundsOf(path))
    }

    @Test
    fun save_twice_createsDistinctFiles() = runTest {
        val uri = writeJpeg("dup.jpg", 100, 100)
        val a = storage.save(uri)
        val b = storage.save(uri)

        assertTrue(a != b)
        assertEquals(2, savedFiles().size)
    }

    @Test
    fun save_notAnImage_throws_andLeavesNoFile() = runTest {
        val file = File(srcDir, "not_image.jpg").apply { writeText("this is not a jpeg") }

        try {
            storage.save(Uri.fromFile(file))
            fail("save should throw")
        } catch (e: IOException) {
            // expected
        }
        assertEquals(emptyList<File>(), savedFiles())
    }

    @Test
    fun save_missingFile_throws() = runTest {
        try {
            storage.save(Uri.fromFile(File(srcDir, "missing.jpg")))
            fail("save should throw")
        } catch (e: IOException) {
            // expected (FileNotFoundException)
        }
        assertEquals(emptyList<File>(), savedFiles())
    }

    @Test
    fun delete_removesSavedFile_andIgnoresMissing() = runTest {
        val path = storage.save(writeJpeg("to_delete.jpg", 100, 100))

        storage.delete(path)
        assertFalse(File(path).exists())

        storage.delete(path) // 2 回目（存在しない）でも例外にならない
    }

    @Test
    fun delete_refusesPathOutsideImagesDir() = runTest {
        val outside = File(context.filesDir, "image_storage_outside.txt").apply { writeText("keep") }
        try {
            storage.delete(outside.absolutePath)
            storage.delete(File(imagesDir, "../${outside.name}").path)

            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }

    @Test
    fun contextConstructor_usesFilesDirImages() = runTest {
        val prodStorage = ImageStorageImpl(context)
        val path = prodStorage.save(writeJpeg("prod.jpg", 10, 10))
        try {
            assertEquals(
                File(context.filesDir, ImageStorageImpl.IMAGES_DIR_NAME).canonicalPath,
                File(path).parentFile!!.canonicalPath,
            )
        } finally {
            prodStorage.delete(path)
        }
        assertFalse(File(path).exists())
    }
}
