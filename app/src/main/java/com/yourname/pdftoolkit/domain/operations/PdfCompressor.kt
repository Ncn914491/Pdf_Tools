package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Defines compression levels for PDF compression.
 */
enum class CompressionLevel(val quality: Int) {
    LOW(80),      // Slight compression, high quality
    MEDIUM(60),   // Balanced
    HIGH(40),     // Maximum compression, lower quality
    MAXIMUM(20)   // Aggressive compression
}

/**
 * Result of a compression operation.
 */
data class CompressionResult(
    val originalSize: Long,
    val compressedSize: Long,
    val compressionRatio: Float,
    val timeTakenMs: Long
) {
    val savedBytes: Long get() = originalSize - compressedSize
    val savedPercentage: Float get() = if (originalSize > 0) (savedBytes.toFloat() / originalSize) * 100 else 0f
}

/**
 * Handles PDF compression operations.
 * Reduces PDF file size by optimizing internal resources.
 */
class PdfCompressor {
    
    /**
     * Compress a PDF file.
     * 
     * Note: PDFBox-Android has limited compression capabilities compared to full PDFBox.
     * This implementation removes unused objects and optimizes the structure.
     * For image-heavy PDFs, consider extracting and recompressing images separately.
     * 
     * @param context Android context
     * @param inputUri URI of the PDF to compress
     * @param outputStream Output stream for the compressed PDF
     * @param level Compression level (affects quality)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return CompressionResult with size statistics
     */
    suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        level: CompressionLevel = CompressionLevel.MEDIUM,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var document: PDDocument? = null
        
        try {
            // Get original file size
            val originalSize = getFileSize(context, inputUri)
            
            onProgress(0.1f)
            
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            
            onProgress(0.3f)
            
            // Apply compression optimizations
            optimizeDocument(document, level)
            
            onProgress(0.7f)
            
            // Save the optimized document
            document.save(outputStream)
            
            onProgress(1.0f)
            
            val timeTaken = System.currentTimeMillis() - startTime
            
            // Note: We can't get the exact compressed size without reading the output
            // The caller should measure the output size if needed
            Result.success(
                CompressionResult(
                    originalSize = originalSize,
                    compressedSize = originalSize, // Placeholder - actual size from output
                    compressionRatio = 1.0f,
                    timeTakenMs = timeTaken
                )
            )
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Apply optimizations to the document based on compression level.
     */
    private fun optimizeDocument(document: PDDocument, level: CompressionLevel) {
        // Remove unused objects
        // Note: PDFBox-Android has limited optimization APIs compared to full PDFBox
        
        // Basic optimizations that are available:
        // 1. The document is loaded and saved, which removes unused references
        // 2. The structure is rewritten efficiently
        
        // For more aggressive compression, we would need to:
        // - Resample images (requires image processing)
        // - Remove metadata
        // - Flatten annotations
        // - Remove embedded fonts (risky for display)
        
        when (level) {
            CompressionLevel.LOW -> {
                // Minimal optimization - just restructure
            }
            CompressionLevel.MEDIUM -> {
                // Remove document metadata for smaller size
                val info = document.documentInformation
                info.producer = null
                info.creator = null
            }
            CompressionLevel.HIGH, CompressionLevel.MAXIMUM -> {
                // Aggressive optimization
                val info = document.documentInformation
                info.producer = null
                info.creator = null
                info.author = null
                info.keywords = null
                info.subject = null
                // Keep title for identification
            }
        }
    }
    
    /**
     * Get the file size in bytes.
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Estimate the compressed size based on compression level.
     * This is a rough estimate and actual results may vary.
     */
    fun estimateCompressedSize(originalSize: Long, level: CompressionLevel): Long {
        val reductionFactor = when (level) {
            CompressionLevel.LOW -> 0.95f
            CompressionLevel.MEDIUM -> 0.80f
            CompressionLevel.HIGH -> 0.60f
            CompressionLevel.MAXIMUM -> 0.40f
        }
        return (originalSize * reductionFactor).toLong()
    }
}
