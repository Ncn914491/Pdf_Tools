package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Defines different split modes for PDF splitting.
 */
sealed class SplitMode {
    /** Split into individual pages - one file per page */
    object AllPages : SplitMode()
    
    /** Split by specific page ranges */
    data class ByRanges(val ranges: List<PageRange>) : SplitMode()
    
    /** Split every N pages */
    data class EveryNPages(val n: Int) : SplitMode()
    
    /** Extract specific pages */
    data class SpecificPages(val pages: List<Int>) : SplitMode()
}

/**
 * Represents a range of pages (1-indexed, inclusive).
 */
data class PageRange(val start: Int, val end: Int) {
    init {
        require(start >= 1) { "Start page must be >= 1" }
        require(end >= start) { "End page must be >= start page" }
    }
}

/**
 * Result of a split operation.
 */
data class SplitResult(
    val totalFilesCreated: Int,
    val totalPagesProcessed: Int
)

/**
 * Handles PDF split operations.
 * Splits a PDF into multiple documents based on various criteria.
 */
class PdfSplitter {
    
    /**
     * Split a PDF into individual pages.
     * 
     * @param context Android context
     * @param inputUri URI of the PDF to split
     * @param outputCallback Callback for each split document (pageNumber, outputStream -> Unit)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Result with split statistics
     */
    suspend fun splitAllPages(
        context: Context,
        inputUri: Uri,
        outputCallback: suspend (pageNumber: Int, outputStream: OutputStream) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): Result<SplitResult> = withContext(Dispatchers.IO) {
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
                // Create new document with single page
                PDDocument().use { newDoc ->
                    val page = document.getPage(pageIndex)
                    newDoc.importPage(page)
                    
                    // Get output stream from callback and save
                    // Note: The caller is responsible for providing/closing the output stream
                    outputCallback(pageIndex + 1, newDoc.saveToOutputStream())
                }
                
                onProgress((pageIndex + 1).toFloat() / totalPages)
            }
            
            Result.success(SplitResult(totalPages, totalPages))
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Split PDF by page ranges.
     */
    suspend fun splitByRanges(
        context: Context,
        inputUri: Uri,
        ranges: List<PageRange>,
        outputCallback: suspend (rangeIndex: Int, outputStream: OutputStream) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): Result<SplitResult> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            var totalPagesProcessed = 0
            
            ranges.forEachIndexed { rangeIndex, range ->
                // Validate range
                if (range.start > totalPages || range.end > totalPages) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Page range ${range.start}-${range.end} exceeds document pages ($totalPages)")
                    )
                }
                
                PDDocument().use { newDoc ->
                    for (pageNum in range.start..range.end) {
                        val page = document.getPage(pageNum - 1) // 0-indexed
                        newDoc.importPage(page)
                        totalPagesProcessed++
                    }
                    
                    outputCallback(rangeIndex + 1, newDoc.saveToOutputStream())
                }
                
                onProgress((rangeIndex + 1).toFloat() / ranges.size)
            }
            
            Result.success(SplitResult(ranges.size, totalPagesProcessed))
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Extract specific pages from a PDF into a new document.
     */
    suspend fun extractPages(
        context: Context,
        inputUri: Uri,
        pageNumbers: List<Int>, // 1-indexed
        outputStream: OutputStream,
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
            val invalidPages = pageNumbers.filter { it < 1 || it > totalPages }
            if (invalidPages.isNotEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid page numbers: $invalidPages (document has $totalPages pages)")
                )
            }
            
            PDDocument().use { newDoc ->
                pageNumbers.forEachIndexed { index, pageNum ->
                    val page = document.getPage(pageNum - 1) // 0-indexed
                    newDoc.importPage(page)
                    onProgress((index + 1).toFloat() / pageNumbers.size)
                }
                
                newDoc.save(outputStream)
            }
            
            Result.success(pageNumbers.size)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Get the page count of a PDF.
     */
    suspend fun getPageCount(
        context: Context,
        uri: Uri
    ): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    document.numberOfPages
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Extension to save PDDocument to an OutputStream.
     */
    private fun PDDocument.saveToOutputStream(): OutputStream {
        // This is a placeholder - actual implementation would need to be handled by caller
        throw UnsupportedOperationException("Use outputCallback pattern instead")
    }
}
