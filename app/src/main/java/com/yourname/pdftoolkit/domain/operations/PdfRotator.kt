package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Rotation angles supported for PDF pages.
 */
enum class RotationAngle(val degrees: Int) {
    ROTATE_90(90),
    ROTATE_180(180),
    ROTATE_270(270)
}

/**
 * Handles PDF page rotation operations.
 */
class PdfRotator {
    
    /**
     * Rotate all pages in a PDF by the specified angle.
     * 
     * @param context Android context
     * @param inputUri URI of the PDF to rotate
     * @param outputStream Output stream for the rotated PDF
     * @param angle Rotation angle (90, 180, or 270 degrees)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Number of pages rotated
     */
    suspend fun rotateAllPages(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        angle: RotationAngle,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            
            if (totalPages == 0) {
                return@withContext Result.failure(
                    IllegalStateException("PDF has no pages")
                )
            }
            
            for (pageIndex in 0 until totalPages) {
                val page = document.getPage(pageIndex)
                val currentRotation = page.rotation
                val newRotation = (currentRotation + angle.degrees) % 360
                page.rotation = newRotation
                
                onProgress((pageIndex + 1).toFloat() / totalPages)
            }
            
            document.save(outputStream)
            
            Result.success(totalPages)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Rotate specific pages in a PDF.
     * 
     * @param context Android context
     * @param inputUri URI of the PDF to rotate
     * @param outputStream Output stream for the rotated PDF
     * @param rotations Map of page numbers (1-indexed) to rotation angles
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Number of pages rotated
     */
    suspend fun rotateSpecificPages(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        rotations: Map<Int, RotationAngle>,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            
            // Validate page numbers
            val invalidPages = rotations.keys.filter { it < 1 || it > totalPages }
            if (invalidPages.isNotEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid page numbers: $invalidPages")
                )
            }
            
            var rotatedCount = 0
            rotations.entries.forEachIndexed { index, (pageNum, angle) ->
                val page = document.getPage(pageNum - 1) // 0-indexed
                val currentRotation = page.rotation
                val newRotation = (currentRotation + angle.degrees) % 360
                page.rotation = newRotation
                rotatedCount++
                
                onProgress((index + 1).toFloat() / rotations.size)
            }
            
            document.save(outputStream)
            
            Result.success(rotatedCount)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Get the current rotation of a specific page.
     */
    suspend fun getPageRotation(
        context: Context,
        uri: Uri,
        pageNumber: Int
    ): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    if (pageNumber in 1..document.numberOfPages) {
                        document.getPage(pageNumber - 1).rotation
                    } else {
                        0
                    }
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
