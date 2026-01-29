package com.yourname.pdftoolkit.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Supported output formats for image processing.
 */
enum class OutputFormat(val extension: String, val mimeType: String) {
    WEBP("webp", "image/webp"),
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

/**
 * Preset resolutions for image resizing.
 */
enum class ResolutionPreset(val width: Int, val height: Int, val displayName: String) {
    THUMBNAIL(150, 150, "Thumbnail (150x150)"),
    SMALL(640, 480, "Small (640x480)"),
    MEDIUM(1024, 768, "Medium (1024x768)"),
    HD(1280, 720, "HD (1280x720)"),
    FULL_HD(1920, 1080, "Full HD (1920x1080)"),
    QHD(2560, 1440, "2K QHD (2560x1440)"),
    UHD_4K(3840, 2160, "4K UHD (3840x2160)")
}

/**
 * Aspect ratio presets for cropping.
 */
enum class AspectRatioPreset(val widthRatio: Float, val heightRatio: Float, val displayName: String) {
    FREE(0f, 0f, "Free"),
    SQUARE(1f, 1f, "Square (1:1)"),
    RATIO_4_3(4f, 3f, "4:3"),
    RATIO_16_9(16f, 9f, "16:9"),
    A4_PORTRAIT(210f, 297f, "A4 Portrait"),
    A4_LANDSCAPE(297f, 210f, "A4 Landscape"),
    LETTER_PORTRAIT(8.5f, 11f, "Letter Portrait"),
    LETTER_LANDSCAPE(11f, 8.5f, "Letter Landscape")
}

/**
 * Result of image processing operation.
 */
data class ImageProcessResult(
    val success: Boolean,
    val outputUri: Uri? = null,
    val outputFile: File? = null,
    val originalSize: Long = 0,
    val processedSize: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val errorMessage: String? = null
) {
    val compressionRatio: Float
        get() = if (originalSize > 0) processedSize.toFloat() / originalSize else 1f
    
    val savedBytes: Long
        get() = originalSize - processedSize
    
    val savedPercentage: Float
        get() = if (originalSize > 0) (1 - compressionRatio) * 100 else 0f
}

/**
 * Core image processing utility using Android Bitmap APIs.
 * Memory-safe with proper inSampleSize calculation and bitmap recycling.
 * 
 * Features:
 * - Resize to custom/preset dimensions
 * - Compress with quality control
 * - Format conversion (JPEG ↔ WebP ↔ PNG)
 * - EXIF metadata stripping
 * - Aspect ratio maintenance
 * - Memory-safe processing for large images
 */
object ImageProcessor {
    
    // Maximum dimension to load full resolution
    private const val MAX_DECODE_DIMENSION = 4096
    
    // Default WebP quality (75-80 for good balance)
    private const val DEFAULT_WEBP_QUALITY = 78
    
    // Default JPEG quality
    private const val DEFAULT_JPEG_QUALITY = 85
    
    /**
     * Resize an image to specified dimensions.
     * 
     * @param context Android context
     * @param inputUri Source image URI
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @param maintainAspectRatio If true, scales to fit within target dimensions
     * @param format Output format
     * @param quality Compression quality (1-100)
     * @return ImageProcessResult with output details
     */
    suspend fun resize(
        context: Context,
        inputUri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        maintainAspectRatio: Boolean = true,
        format: OutputFormat = OutputFormat.WEBP,
        quality: Int = DEFAULT_WEBP_QUALITY
    ): ImageProcessResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext ImageProcessResult(
                    success = false,
                    errorMessage = "Cannot open input file"
                )
            
            // Get original file size
            val originalSize = getFileSize(context, inputUri)
            
            // Load bitmap with EXIF rotation
            val bitmap = loadBitmapWithExif(context, inputUri, max(targetWidth, targetHeight))
                ?: return@withContext ImageProcessResult(
                    success = false,
                    errorMessage = "Cannot decode image"
                )
            
            try {
                // Calculate scaled dimensions
                val (scaledWidth, scaledHeight) = if (maintainAspectRatio) {
                    calculateScaledDimensions(bitmap.width, bitmap.height, targetWidth, targetHeight)
                } else {
                    Pair(targetWidth, targetHeight)
                }
                
                // Create scaled bitmap
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                
                try {
                    // Save to temp file
                    val outputFile = createTempFile(context, format)
                    val processedSize = saveBitmap(resizedBitmap, outputFile, format, quality)
                    
                    ImageProcessResult(
                        success = true,
                        outputFile = outputFile,
                        outputUri = Uri.fromFile(outputFile),
                        originalSize = originalSize,
                        processedSize = processedSize,
                        width = scaledWidth,
                        height = scaledHeight
                    )
                } finally {
                    if (resizedBitmap != bitmap) {
                        resizedBitmap.recycle()
                    }
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ImageProcessResult(
                success = false,
                errorMessage = "Resize failed: ${e.message}"
            )
        }
    }
    
    /**
     * Resize to a preset resolution.
     */
    suspend fun resizeToPreset(
        context: Context,
        inputUri: Uri,
        preset: ResolutionPreset,
        format: OutputFormat = OutputFormat.WEBP,
        quality: Int = DEFAULT_WEBP_QUALITY
    ): ImageProcessResult = resize(
        context = context,
        inputUri = inputUri,
        targetWidth = preset.width,
        targetHeight = preset.height,
        maintainAspectRatio = true,
        format = format,
        quality = quality
    )
    
    /**
     * Compress an image with specified quality.
     * 
     * @param context Android context
     * @param inputUri Source image URI
     * @param quality Compression quality (1-100)
     * @param format Output format (WebP recommended for best compression)
     * @return ImageProcessResult with compression details
     */
    suspend fun compress(
        context: Context,
        inputUri: Uri,
        quality: Int,
        format: OutputFormat = OutputFormat.WEBP
    ): ImageProcessResult = withContext(Dispatchers.IO) {
        try {
            val originalSize = getFileSize(context, inputUri)
            
            // Load bitmap with EXIF rotation
            val bitmap = loadBitmapWithExif(context, inputUri, MAX_DECODE_DIMENSION)
                ?: return@withContext ImageProcessResult(
                    success = false,
                    errorMessage = "Cannot decode image"
                )
            
            try {
                val outputFile = createTempFile(context, format)
                val processedSize = saveBitmap(bitmap, outputFile, format, quality)
                
                ImageProcessResult(
                    success = true,
                    outputFile = outputFile,
                    outputUri = Uri.fromFile(outputFile),
                    originalSize = originalSize,
                    processedSize = processedSize,
                    width = bitmap.width,
                    height = bitmap.height
                )
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ImageProcessResult(
                success = false,
                errorMessage = "Compression failed: ${e.message}"
            )
        }
    }
    
    /**
     * Convert image to different format.
     * 
     * @param context Android context
     * @param inputUri Source image URI
     * @param targetFormat Target format (JPEG, WebP, PNG)
     * @param quality Compression quality (1-100)
     * @return ImageProcessResult with conversion details
     */
    suspend fun convertFormat(
        context: Context,
        inputUri: Uri,
        targetFormat: OutputFormat,
        quality: Int = DEFAULT_WEBP_QUALITY
    ): ImageProcessResult = withContext(Dispatchers.IO) {
        try {
            val originalSize = getFileSize(context, inputUri)
            
            val bitmap = loadBitmapWithExif(context, inputUri, MAX_DECODE_DIMENSION)
                ?: return@withContext ImageProcessResult(
                    success = false,
                    errorMessage = "Cannot decode image"
                )
            
            try {
                val outputFile = createTempFile(context, targetFormat)
                val processedSize = saveBitmap(bitmap, outputFile, targetFormat, quality)
                
                ImageProcessResult(
                    success = true,
                    outputFile = outputFile,
                    outputUri = Uri.fromFile(outputFile),
                    originalSize = originalSize,
                    processedSize = processedSize,
                    width = bitmap.width,
                    height = bitmap.height
                )
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ImageProcessResult(
                success = false,
                errorMessage = "Format conversion failed: ${e.message}"
            )
        }
    }
    
    /**
     * Strip EXIF metadata from image.
     * Creates a clean copy without any embedded metadata.
     * 
     * @param context Android context
     * @param inputUri Source image URI
     * @param format Output format
     * @param quality Compression quality
     * @return ImageProcessResult with stripped image
     */
    suspend fun stripMetadata(
        context: Context,
        inputUri: Uri,
        format: OutputFormat = OutputFormat.JPEG,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): ImageProcessResult = withContext(Dispatchers.IO) {
        try {
            val originalSize = getFileSize(context, inputUri)
            
            // Load bitmap WITHOUT applying EXIF rotation (we want raw pixels)
            // Then save without any EXIF data
            val bitmap = loadBitmapRaw(context, inputUri, MAX_DECODE_DIMENSION)
                ?: return@withContext ImageProcessResult(
                    success = false,
                    errorMessage = "Cannot decode image"
                )
            
            try {
                // Read EXIF to apply rotation before stripping
                val rotatedBitmap = applyExifRotation(context, inputUri, bitmap)
                
                try {
                    val outputFile = createTempFile(context, format)
                    val processedSize = saveBitmap(rotatedBitmap, outputFile, format, quality)
                    
                    ImageProcessResult(
                        success = true,
                        outputFile = outputFile,
                        outputUri = Uri.fromFile(outputFile),
                        originalSize = originalSize,
                        processedSize = processedSize,
                        width = rotatedBitmap.width,
                        height = rotatedBitmap.height
                    )
                } finally {
                    if (rotatedBitmap != bitmap) {
                        rotatedBitmap.recycle()
                    }
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ImageProcessResult(
                success = false,
                errorMessage = "Metadata stripping failed: ${e.message}"
            )
        }
    }
    
    /**
     * Process multiple images in batch.
     * 
     * @param context Android context
     * @param inputUris List of source image URIs
     * @param operation Processing operation to apply
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return List of ImageProcessResult
     */
    suspend fun batchProcess(
        context: Context,
        inputUris: List<Uri>,
        operation: suspend (Uri) -> ImageProcessResult,
        onProgress: (Float) -> Unit = {}
    ): List<ImageProcessResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ImageProcessResult>()
        
        inputUris.forEachIndexed { index, uri ->
            val result = operation(uri)
            results.add(result)
            onProgress((index + 1).toFloat() / inputUris.size)
        }
        
        results
    }
    
    /**
     * Get image dimensions without loading full bitmap.
     */
    suspend fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                Pair(options.outWidth, options.outHeight)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Load bitmap with memory-safe options and EXIF rotation applied.
     */
    private fun loadBitmapWithExif(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val rawBitmap = loadBitmapRaw(context, uri, maxDimension) ?: return null
        return applyExifRotation(context, uri, rawBitmap)
    }
    
    /**
     * Load bitmap without EXIF rotation.
     */
    private fun loadBitmapRaw(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        return try {
            // First pass: get dimensions
            val dimensions = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                Pair(options.outWidth, options.outHeight)
            } ?: return null
            
            // Calculate inSampleSize
            val sampleSize = calculateInSampleSize(dimensions.first, dimensions.second, maxDimension)
            
            // Second pass: decode with sample size
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Apply EXIF rotation to bitmap.
     */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()
            
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap
            }
            
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            
            rotatedBitmap
        } catch (e: Exception) {
            bitmap
        }
    }
    
    /**
     * Calculate inSampleSize for memory-efficient loading.
     */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            
            while ((halfWidth / sampleSize) >= maxDimension || (halfHeight / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }
        
        return sampleSize
    }
    
    /**
     * Calculate scaled dimensions maintaining aspect ratio.
     */
    private fun calculateScaledDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Int, Int> {
        val widthRatio = targetWidth.toFloat() / originalWidth
        val heightRatio = targetHeight.toFloat() / originalHeight
        val scale = min(widthRatio, heightRatio)
        
        return Pair(
            (originalWidth * scale).roundToInt(),
            (originalHeight * scale).roundToInt()
        )
    }
    
    /**
     * Save bitmap to file with specified format and quality.
     * Returns the file size in bytes.
     */
    private fun saveBitmap(
        bitmap: Bitmap,
        outputFile: File,
        format: OutputFormat,
        quality: Int
    ): Long {
        FileOutputStream(outputFile).use { outputStream ->
            val compressFormat = when (format) {
                OutputFormat.WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            }
            
            bitmap.compress(compressFormat, quality, outputStream)
            outputStream.flush()
        }
        
        return outputFile.length()
    }
    
    /**
     * Create a temporary file for output.
     */
    private fun createTempFile(context: Context, format: OutputFormat): File {
        val timestamp = System.currentTimeMillis()
        return File(context.cacheDir, "processed_${timestamp}.${format.extension}")
    }
    
    /**
     * Get file size from URI.
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Encode bitmap to byte array for in-memory operations.
     */
    fun encodeBitmap(
        bitmap: Bitmap,
        format: OutputFormat,
        quality: Int
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        
        val compressFormat = when (format) {
            OutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        
        bitmap.compress(compressFormat, quality, outputStream)
        return outputStream.toByteArray()
    }
}
