package com.yourname.pdftoolkit.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Manages the app's default output folder for saved PDFs and conversions.
 * Creates a "PDF Toolkit" folder in the device's Documents directory.
 */
object OutputFolderManager {
    
    private const val APP_FOLDER_NAME = "PDF Toolkit"
    
    /**
     * Get or create the app's output folder.
     * Works on all Android versions.
     */
    fun getOutputFolder(context: Context): File? {
        return try {
            val documentsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+, use app-specific external files directory
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            } else {
                // On older versions, use public Documents
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), APP_FOLDER_NAME)
            }
            
            val appFolder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(documentsDir, APP_FOLDER_NAME).also {
                    if (!it.exists()) it.mkdirs()
                }
            } else {
                documentsDir?.also {
                    if (!it.exists()) it.mkdirs()
                }
            }
            
            appFolder
        } catch (e: Exception) {
            // Fallback to internal storage
            File(context.filesDir, APP_FOLDER_NAME).also {
                if (!it.exists()) it.mkdirs()
            }
        }
    }
    
    /**
     * Create an output file in the app's folder.
     * Returns the file and a content URI for sharing.
     */
    fun createOutputFile(
        context: Context,
        fileName: String,
        mimeType: String = "application/pdf"
    ): OutputFile? {
        return try {
            val folder = getOutputFolder(context) ?: return null
            
            // Ensure unique filename
            val uniqueFileName = generateUniqueFileName(folder, fileName)
            val file = File(folder, uniqueFileName)
            
            // Create the file
            file.createNewFile()
            
            // Get content URI via FileProvider
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            OutputFile(file, contentUri, uniqueFileName)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create an output stream for writing to a file in the app's folder.
     */
    fun createOutputStream(
        context: Context,
        fileName: String
    ): OutputStreamResult? {
        return try {
            val outputFile = createOutputFile(context, fileName) ?: return null
            val outputStream = FileOutputStream(outputFile.file)
            
            OutputStreamResult(
                outputStream = outputStream,
                outputFile = outputFile
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Save content to Media Store (for better visibility in file managers).
     * Used on Android 10+ for public documents.
     */
    fun saveToMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
        content: ByteArray
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER_NAME")
            }
            
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            )
            
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content)
                }
            }
            
            uri
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get the path to the app's output folder for display.
     */
    fun getOutputFolderPath(context: Context): String {
        return try {
            val folder = getOutputFolder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Documents/$APP_FOLDER_NAME"
            } else {
                folder?.absolutePath ?: "Documents/$APP_FOLDER_NAME"
            }
        } catch (e: Exception) {
            "Documents/$APP_FOLDER_NAME"
        }
    }
    
    /**
     * List all files in the output folder.
     */
    fun listOutputFiles(context: Context): List<OutputFileInfo> {
        return try {
            val folder = getOutputFolder(context) ?: return emptyList()
            
            folder.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    OutputFileInfo(
                        file = file,
                        uri = uri,
                        name = file.name,
                        size = file.length(),
                        formattedSize = formatFileSize(file.length()),
                        lastModified = file.lastModified()
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Delete a file from the output folder.
     */
    fun deleteOutputFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get total size of all files in output folder.
     */
    fun getOutputFolderSize(context: Context): Long {
        return try {
            val folder = getOutputFolder(context) ?: return 0L
            folder.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Clear all files in output folder.
     */
    fun clearOutputFolder(context: Context): Boolean {
        return try {
            val folder = getOutputFolder(context) ?: return false
            folder.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun generateUniqueFileName(folder: File, originalName: String): String {
        val baseName = originalName.substringBeforeLast('.')
        val extension = originalName.substringAfterLast('.', "pdf")
        
        var fileName = originalName
        var counter = 1
        
        while (File(folder, fileName).exists()) {
            fileName = "${baseName}_$counter.$extension"
            counter++
        }
        
        return fileName
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

/**
 * Represents an output file with its file reference and content URI.
 */
data class OutputFile(
    val file: File,
    val contentUri: Uri,
    val fileName: String
)

/**
 * Result when creating an output stream.
 */
data class OutputStreamResult(
    val outputStream: OutputStream,
    val outputFile: OutputFile
)

/**
 * Information about a file in the output folder.
 */
data class OutputFileInfo(
    val file: File,
    val uri: Uri,
    val name: String,
    val size: Long,
    val formattedSize: String,
    val lastModified: Long
)
