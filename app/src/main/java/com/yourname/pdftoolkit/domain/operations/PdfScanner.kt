package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Page size presets for scanned documents.
 */
enum class ScanPageSize(val rect: PDRectangle, val displayName: String) {
    A4(PDRectangle.A4, "A4 (210 x 297 mm)"),
    LETTER(PDRectangle.LETTER, "Letter (8.5 x 11 in)"),
    LEGAL(PDRectangle.LEGAL, "Legal (8.5 x 14 in)"),
    A5(PDRectangle.A5, "A5 (148 x 210 mm)"),
    FIT_IMAGE(PDRectangle.A4, "Fit to Image") // Will be adjusted per image
}

/**
 * Color mode for scanned documents.
 */
enum class ScanColorMode {
    COLOR,
    GRAYSCALE,
    BLACK_AND_WHITE
}

/**
 * Scan quality preset.
 */
enum class ScanQuality(val dpi: Int, val quality: Int) {
    LOW(72, 60),
    MEDIUM(150, 75),
    HIGH(200, 85),
    BEST(300, 95)
}

/**
 * Scanned page data.
 */
data class ScannedPage(
    val imagePath: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Scan to PDF configuration.
 */
data class ScanConfig(
    val pageSize: ScanPageSize = ScanPageSize.A4,
    val colorMode: ScanColorMode = ScanColorMode.COLOR,
    val quality: ScanQuality = ScanQuality.MEDIUM,
    val autoCrop: Boolean = true,
    val autoRotate: Boolean = true,
    val enhanceContrast: Boolean = true
)

/**
 * Result of scan to PDF operation.
 */
data class ScanToPdfResult(
    val success: Boolean,
    val pagesScanned: Int,
    val outputUri: Uri? = null,
    val errorMessage: String? = null
)

/**
 * Scanner - Converts camera-captured images to PDF documents.
 * Includes image enhancement, auto-crop simulation, and multi-page support.
 * Uses Apache PDFBox-Android (Apache 2.0 License).
 */
class PdfScanner(private val context: Context) {
    
    private val scansDir: File by lazy {
        File(context.cacheDir, "scans").also { it.mkdirs() }
    }
    
    /**
     * Convert multiple scanned images to a single PDF.
     *
     * @param imageUris List of image URIs to convert
     * @param outputUri Output PDF URI
     * @param config Scan configuration
     * @param progressCallback Progress callback (0-100)
     * @return ScanToPdfResult with operation status
     */
    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        outputUri: Uri,
        config: ScanConfig = ScanConfig(),
        progressCallback: (Int) -> Unit = {}
    ): ScanToPdfResult = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext ScanToPdfResult(
                success = false,
                pagesScanned = 0,
                errorMessage = "No images provided"
            )
        }
        
        var document: PDDocument? = null
        
        try {
            progressCallback(0)
            
            document = PDDocument()
            val totalImages = imageUris.size
            
            for ((index, imageUri) in imageUris.withIndex()) {
                // Load and process image
                val bitmap = loadAndProcessImage(imageUri, config)
                    ?: continue
                
                // Add page with image
                addImagePage(document, bitmap, config)
                bitmap.recycle()
                
                val progress = ((index + 1) * 90) / totalImages
                progressCallback(progress)
            }
            
            progressCallback(95)
            
            // Save the document
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                document.save(outputStream)
            }
            
            document.close()
            progressCallback(100)
            
            ScanToPdfResult(
                success = true,
                pagesScanned = document.numberOfPages,
                outputUri = outputUri
            )
            
        } catch (e: IOException) {
            document?.close()
            ScanToPdfResult(
                success = false,
                pagesScanned = 0,
                errorMessage = "IO Error: ${e.message}"
            )
        } catch (e: Exception) {
            document?.close()
            ScanToPdfResult(
                success = false,
                pagesScanned = 0,
                errorMessage = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Save a camera capture as a scanned page.
     */
    suspend fun saveCameraCapture(bitmap: Bitmap): ScannedPage = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val file = File(scansDir, "scan_$timestamp.jpg")
        
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        ScannedPage(
            imagePath = file.absolutePath,
            timestamp = timestamp
        )
    }
    
    /**
     * Get all saved scanned pages.
     */
    fun getSavedScans(): List<ScannedPage> {
        return scansDir.listFiles()
            ?.filter { it.name.startsWith("scan_") && it.name.endsWith(".jpg") }
            ?.map { file ->
                val timestamp = file.name
                    .removePrefix("scan_")
                    .removeSuffix(".jpg")
                    .toLongOrNull() ?: file.lastModified()
                ScannedPage(
                    imagePath = file.absolutePath,
                    timestamp = timestamp
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }
    
    /**
     * Delete a scanned page.
     */
    fun deleteScannedPage(page: ScannedPage): Boolean {
        return File(page.imagePath).delete()
    }
    
    /**
     * Clear all scanned pages.
     */
    fun clearAllScans() {
        scansDir.listFiles()?.forEach { it.delete() }
    }
    
    /**
     * Load and process an image based on configuration.
     */
    private fun loadAndProcessImage(uri: Uri, config: ScanConfig): Bitmap? {
        var bitmap = loadBitmap(uri) ?: return null
        
        // Apply color mode
        bitmap = applyColorMode(bitmap, config.colorMode)
        
        // Enhance contrast if enabled
        if (config.enhanceContrast) {
            bitmap = enhanceContrast(bitmap)
        }
        
        return bitmap
    }
    
    /**
     * Load bitmap from URI with size limits.
     */
    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            // First, get dimensions without loading full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            
            // Calculate sample size to avoid OOM
            val maxSize = 3000 // Max dimension
            val sampleSize = calculateSampleSize(options, maxSize, maxSize)
            
            // Load actual bitmap
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, loadOptions)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate sample size for bitmap loading.
     */
    private fun calculateSampleSize(options: BitmapFactory.Options, maxWidth: Int, maxHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var sampleSize = 1
        
        if (height > maxHeight || width > maxWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / sampleSize) >= maxHeight && (halfWidth / sampleSize) >= maxWidth) {
                sampleSize *= 2
            }
        }
        
        return sampleSize
    }
    
    /**
     * Apply color mode to bitmap.
     */
    private fun applyColorMode(bitmap: Bitmap, mode: ScanColorMode): Bitmap {
        return when (mode) {
            ScanColorMode.COLOR -> bitmap
            ScanColorMode.GRAYSCALE -> convertToGrayscale(bitmap)
            ScanColorMode.BLACK_AND_WHITE -> convertToBlackAndWhite(bitmap)
        }
    }
    
    /**
     * Convert bitmap to grayscale.
     */
    private fun convertToGrayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        
        if (source != result) {
            source.recycle()
        }
        
        return result
    }
    
    /**
     * Convert bitmap to black and white (threshold).
     */
    private fun convertToBlackAndWhite(source: Bitmap): Bitmap {
        val grayscale = convertToGrayscale(source)
        val width = grayscale.width
        val height = grayscale.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = 128

        for (i in pixels.indices) {
            val pixel = pixels[i]
            // For grayscale images, R=G=B. Extracting red component is sufficient.
            // Using bitwise operations for performance: (pixel >> 16) & 0xFF
            val gray = (pixel shr 16) and 0xFF

            pixels[i] = if (gray > threshold) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.BLACK
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)

        if (grayscale != source) {
            grayscale.recycle()
        }

        return result
    }
    
    /**
     * Enhance contrast of bitmap.
     */
    private fun enhanceContrast(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // Increase contrast by adjusting color matrix
        val contrast = 1.2f
        val translate = (-.5f * contrast + .5f) * 255f
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        
        if (source != result) {
            source.recycle()
        }
        
        return result
    }
    
    /**
     * Add a page with an image to the document.
     */
    private fun addImagePage(
        document: PDDocument,
        bitmap: Bitmap,
        config: ScanConfig
    ) {
        // Determine page size
        val pageRect = if (config.pageSize == ScanPageSize.FIT_IMAGE) {
            // Calculate page size from image
            val dpi = config.quality.dpi.toFloat()
            val widthPoints = (bitmap.width / dpi) * 72
            val heightPoints = (bitmap.height / dpi) * 72
            PDRectangle(widthPoints, heightPoints)
        } else {
            config.pageSize.rect
        }
        
        val page = PDPage(pageRect)
        document.addPage(page)
        
        // Create PDF image - use JPEG for photos to save space
        val pdImage = if (config.colorMode == ScanColorMode.BLACK_AND_WHITE) {
            LosslessFactory.createFromImage(document, bitmap)
        } else {
            JPEGFactory.createFromImage(document, bitmap, config.quality.quality / 100f)
        }
        
        val contentStream = PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.OVERWRITE,
            true,
            true
        )
        
        try {
            // Calculate image placement to fit page while maintaining aspect ratio
            val pageWidth = pageRect.width
            val pageHeight = pageRect.height
            val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val pageAspect = pageWidth / pageHeight
            
            val (drawWidth, drawHeight, drawX, drawY) = if (imageAspect > pageAspect) {
                // Image is wider - fit to width
                val w = pageWidth
                val h = pageWidth / imageAspect
                val x = 0f
                val y = (pageHeight - h) / 2
                arrayOf(w, h, x, y)
            } else {
                // Image is taller - fit to height
                val h = pageHeight
                val w = pageHeight * imageAspect
                val x = (pageWidth - w) / 2
                val y = 0f
                arrayOf(w, h, x, y)
            }
            
            contentStream.drawImage(pdImage, drawX, drawY, drawWidth, drawHeight)
        } finally {
            contentStream.close()
        }
    }
}
