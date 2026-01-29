package com.yourname.pdftoolkit.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class ImageProcessorTest {

    @Test
    fun test_getImageDimensions() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val width = 100
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val file = File(context.cacheDir, "test_image.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = Uri.fromFile(file)

        val dimensions = ImageProcessor.getImageDimensions(context, uri)

        assertEquals(width, dimensions?.first)
        assertEquals(height, dimensions?.second)
    }
}
