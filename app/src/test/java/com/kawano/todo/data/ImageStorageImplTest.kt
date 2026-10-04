package com.kawano.todo.data

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.kawano.todo.data.image.ExifTransform
import com.kawano.todo.data.image.ImageScaling
import com.kawano.todo.data.image.ImageStorageImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.max

/**
 * ImageStorageImpl の JVM ユニットテスト。
 * Bitmap 系は JVM で動かないため、純粋関数・delete・save の失敗パスのみ検証する。
 * 実際のデコード/保存は androidTest 側で検証する。
 */
class ImageStorageImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- calculateInSampleSize ----

    @Test
    fun inSampleSize_isOne_whenAlreadySmall() {
        assertEquals(1, ImageScaling.calculateInSampleSize(1000, 800, 2048))
        assertEquals(1, ImageScaling.calculateInSampleSize(2048, 2048, 2048))
        assertEquals(1, ImageScaling.calculateInSampleSize(1, 1, 2048))
    }

    @Test
    fun inSampleSize_keepsLongEdgeAtLeastMax() {
        // Pixel 6a の 12MP 写真: 4032/2 = 2016 < 2048 なので間引かない
        assertEquals(1, ImageScaling.calculateInSampleSize(4032, 3024, 2048))
        assertEquals(2, ImageScaling.calculateInSampleSize(4096, 3072, 2048))
        assertEquals(2, ImageScaling.calculateInSampleSize(3000, 8191, 2048))
        assertEquals(4, ImageScaling.calculateInSampleSize(3000, 8192, 2048))
        assertEquals(8, ImageScaling.calculateInSampleSize(20000, 100, 2048))
    }

    @Test
    fun inSampleSize_isPowerOfTwo_andDecodedLongEdgeNotBelowMax() {
        for (w in listOf(1, 100, 2047, 2049, 3000, 5000, 9999, 30000)) {
            for (h in listOf(1, 500, 2048, 4100, 16000)) {
                val s = ImageScaling.calculateInSampleSize(w, h, 2048)
                assertTrue("power of two: $s", s > 0 && (s and (s - 1)) == 0)
                val longEdge = max(w, h)
                if (longEdge > 2048) {
                    assertTrue("decoded long edge >= max ($w x $h, s=$s)", longEdge / s >= 2048)
                    assertTrue("not too conservative ($w x $h, s=$s)", longEdge / (s * 2) < 2048)
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun inSampleSize_rejectsNonPositiveMax() {
        ImageScaling.calculateInSampleSize(100, 100, 0)
    }

    // ---- scaleFactor / scaledSize ----

    @Test
    fun scaleFactor_isOne_whenWithinLimit() {
        assertEquals(1f, ImageScaling.scaleFactor(2048, 1000, 2048))
        assertEquals(2048 to 1000, ImageScaling.scaledSize(2048, 1000, 2048))
    }

    @Test
    fun scaledSize_fitsLongEdge_andKeepsAspect() {
        assertEquals(2048 to 1536, ImageScaling.scaledSize(4032, 3024, 2048))
        assertEquals(1536 to 2048, ImageScaling.scaledSize(3024, 4032, 2048))
        assertEquals(2048 to 2048, ImageScaling.scaledSize(5000, 5000, 2048))
    }

    @Test
    fun scaledSize_neverZero() {
        assertEquals(2048 to 1, ImageScaling.scaledSize(100000, 1, 2048))
    }

    // ---- exifTransform ----

    @Test
    fun exifTransform_mapsAllOrientations() {
        assertEquals(ExifTransform(0, false), ImageScaling.exifTransform(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(ExifTransform(0, false), ImageScaling.exifTransform(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(ExifTransform(0, true), ImageScaling.exifTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(ExifTransform(180, false), ImageScaling.exifTransform(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(ExifTransform(180, true), ImageScaling.exifTransform(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(ExifTransform(90, true), ImageScaling.exifTransform(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(ExifTransform(90, false), ImageScaling.exifTransform(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(ExifTransform(270, true), ImageScaling.exifTransform(ExifInterface.ORIENTATION_TRANSVERSE))
        assertEquals(ExifTransform(270, false), ImageScaling.exifTransform(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(ExifTransform(0, false), ImageScaling.exifTransform(42))
    }

    @Test
    fun exifTransform_flags() {
        assertTrue(ImageScaling.exifTransform(ExifInterface.ORIENTATION_NORMAL).isIdentity)
        assertTrue(ImageScaling.exifTransform(ExifInterface.ORIENTATION_ROTATE_90).swapsDimensions)
        assertTrue(ImageScaling.exifTransform(ExifInterface.ORIENTATION_TRANSVERSE).swapsDimensions)
        assertFalse(ImageScaling.exifTransform(ExifInterface.ORIENTATION_ROTATE_180).swapsDimensions)
        assertFalse(ImageScaling.exifTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL).isIdentity)
    }

    // ---- isInsideDirectory ----

    @Test
    fun isInsideDirectory_validatesNormalizedPath() {
        val images = tmp.newFolder("files", "images")
        val files = images.parentFile!!
        assertTrue(ImageScaling.isInsideDirectory(images, File(images, "a.jpg")))
        assertTrue(ImageScaling.isInsideDirectory(images, File(images, "sub/../a.jpg")))
        assertFalse(ImageScaling.isInsideDirectory(images, images))
        assertFalse(ImageScaling.isInsideDirectory(images, File(images, "../a.jpg")))
        assertFalse(ImageScaling.isInsideDirectory(images, File(files, "images2/a.jpg")))
        assertFalse(ImageScaling.isInsideDirectory(images, File(files, "other.db")))
    }

    // ---- delete ----

    @Test
    fun delete_removesFileInsideImagesDir() = runTest {
        val images = tmp.newFolder("images")
        val file = File(images, "x.jpg").apply { writeText("jpeg") }
        val storage = ImageStorageImpl(mockk(), images, StandardTestDispatcher(testScheduler))

        storage.delete(file.absolutePath)

        assertFalse(file.exists())
    }

    @Test
    fun delete_ignoresMissingFile() = runTest {
        val images = tmp.newFolder("images")
        val storage = ImageStorageImpl(mockk(), images, StandardTestDispatcher(testScheduler))

        storage.delete(File(images, "missing.jpg").absolutePath) // 例外が出ないこと
    }

    @Test
    fun delete_ignoresMissingImagesDir() = runTest {
        val images = File(tmp.root, "not-created/images")
        val storage = ImageStorageImpl(mockk(), images, StandardTestDispatcher(testScheduler))

        storage.delete(File(images, "missing.jpg").absolutePath)
    }

    @Test
    fun delete_doesNotTouchFilesOutsideImagesDir() = runTest {
        val images = tmp.newFolder("files", "images")
        val outside = File(images.parentFile, "secret.db").apply { writeText("db") }
        val sibling = tmp.newFolder("files", "images2")
        val siblingFile = File(sibling, "y.jpg").apply { writeText("jpeg") }
        val storage = ImageStorageImpl(mockk(), images, StandardTestDispatcher(testScheduler))

        storage.delete(outside.absolutePath)
        storage.delete(File(images, "../secret.db").path)
        storage.delete(siblingFile.absolutePath)
        storage.delete(images.absolutePath)
        storage.delete("")

        assertTrue(outside.exists())
        assertTrue(siblingFile.exists())
        assertTrue(images.isDirectory)
    }

    @Test
    fun delete_doesNotRemoveSubdirectory() = runTest {
        val images = tmp.newFolder("images")
        val sub = File(images, "sub").apply { mkdirs() }
        val storage = ImageStorageImpl(mockk(), images, StandardTestDispatcher(testScheduler))

        storage.delete(sub.absolutePath)

        assertTrue(sub.isDirectory)
    }

    // ---- save（失敗パスのみ）----

    @Test
    fun save_throws_andLeavesNoFile_whenStreamCannotBeOpened() = runTest {
        val images = tmp.newFolder("images")
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> {
            every { openInputStream(uri) } returns null
        }
        val storage = ImageStorageImpl(resolver, images, StandardTestDispatcher(testScheduler))

        try {
            storage.save(uri)
            fail("save should throw")
        } catch (e: FileNotFoundException) {
            // expected
        }
        assertEquals(0, images.listFiles()!!.size)
    }

    @Test
    fun save_propagatesResolverException() = runTest {
        val images = tmp.newFolder("images")
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> {
            every { openInputStream(uri) } throws SecurityException("no permission")
        }
        val storage = ImageStorageImpl(resolver, images, StandardTestDispatcher(testScheduler))

        try {
            storage.save(uri)
            fail("save should throw")
        } catch (e: SecurityException) {
            // expected
        }
        assertEquals(0, images.listFiles()!!.size)
    }
}
