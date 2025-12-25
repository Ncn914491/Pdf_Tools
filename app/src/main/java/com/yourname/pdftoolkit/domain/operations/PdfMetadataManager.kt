package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * PDF metadata information.
 */
data class PdfMetadata(
    val title: String?,
    val author: String?,
    val subject: String?,
    val keywords: String?,
    val creator: String?,
    val producer: String?,
    val creationDate: String?,
    val modificationDate: String?,
    val pageCount: Int,
    val fileSize: Long,
    val isEncrypted: Boolean,
    val pdfVersion: String?
)

/**
 * Editable metadata fields.
 */
data class EditableMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null
)

/**
 * Handles PDF metadata operations (read/write).
 */
class PdfMetadataManager {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Read metadata from a PDF.
     * 
     * @param context Android context
     * @param uri URI of the PDF
     * @return PdfMetadata object with all available information
     */
    suspend fun readMetadata(
        context: Context,
        uri: Uri
    ): Result<PdfMetadata> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            val info = document.documentInformation
            
            val metadata = PdfMetadata(
                title = info.title?.takeIf { it.isNotBlank() },
                author = info.author?.takeIf { it.isNotBlank() },
                subject = info.subject?.takeIf { it.isNotBlank() },
                keywords = info.keywords?.takeIf { it.isNotBlank() },
                creator = info.creator?.takeIf { it.isNotBlank() },
                producer = info.producer?.takeIf { it.isNotBlank() },
                creationDate = info.creationDate?.let { formatDate(it) },
                modificationDate = info.modificationDate?.let { formatDate(it) },
                pageCount = document.numberOfPages,
                fileSize = getFileSize(context, uri),
                isEncrypted = document.isEncrypted,
                pdfVersion = document.version.toString()
            )
            
            Result.success(metadata)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Update metadata in a PDF.
     * 
     * @param context Android context
     * @param inputUri URI of the PDF to update
     * @param outputStream Output stream for the updated PDF
     * @param metadata New metadata values (null values are not updated)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun updateMetadata(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        metadata: EditableMetadata,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            onProgress(0.2f)
            
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            val info = document.documentInformation
            
            onProgress(0.4f)
            
            // Update only provided fields
            metadata.title?.let { info.title = it }
            metadata.author?.let { info.author = it }
            metadata.subject?.let { info.subject = it }
            metadata.keywords?.let { info.keywords = it }
            
            // Update modification date
            info.modificationDate = Calendar.getInstance()
            
            onProgress(0.7f)
            
            // Save with updated metadata
            document.save(outputStream)
            
            onProgress(1.0f)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Remove all metadata from a PDF.
     */
    suspend fun removeMetadata(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            onProgress(0.2f)
            
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            
            onProgress(0.4f)
            
            // Clear all metadata
            val info = PDDocumentInformation()
            document.documentInformation = info
            
            onProgress(0.7f)
            
            document.save(outputStream)
            
            onProgress(1.0f)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Format a Calendar date to a readable string.
     */
    private fun formatDate(calendar: Calendar): String {
        return try {
            dateFormat.format(calendar.time)
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get file size from URI.
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
}
