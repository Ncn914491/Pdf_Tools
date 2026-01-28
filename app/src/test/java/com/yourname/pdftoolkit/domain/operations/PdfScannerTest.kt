package com.yourname.pdftoolkit.domain.operations

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PdfScannerTest {

    @Test
    fun verifyConvertToBlackAndWhiteLogic() {
        // Setup - 100x100 image
        val width = 100
        val height = 100
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Fill with specific pattern (Gradient)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = (x + y) % 256
                source.setPixel(x, y, Color.rgb(v, v, v))
            }
        }

        // Expected result using legacy logic (trusted reference)
        val expected = legacyConversion(source)

        // Actual result using reflection on PdfScanner
        val context = RuntimeEnvironment.getApplication()
        val scanner = PdfScanner(context)

        val method: Method = PdfScanner::class.java.getDeclaredMethod("convertToBlackAndWhite", Bitmap::class.java)
        method.isAccessible = true
        val actual = method.invoke(scanner, source) as Bitmap

        // Verify
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals("Pixel at $x, $y", expected.getPixel(x, y), actual.getPixel(x, y))
            }
        }
    }

    /**
     * Reference implementation of the legacy pixel-by-pixel logic to ensure
     * the optimized version maintains exact behavior.
     */
    private fun legacyConversion(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val grayscale = source // Input is already grayscale in this test context
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val threshold = 128

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = grayscale.getPixel(x, y)
                // Extract red component (works for grayscale where R=G=B)
                val gray = (pixel shr 16) and 0xFF
                val newColor = if (gray > threshold) {
                    android.graphics.Color.WHITE
                } else {
                    android.graphics.Color.BLACK
                }
                result.setPixel(x, y, newColor)
            }
        }

        return result
    }
}
