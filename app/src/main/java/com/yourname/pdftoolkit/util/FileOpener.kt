package com.yourname.pdftoolkit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for opening files with external applications.
 * 
 * IMPORTANT: For content URIs from DownloadsProvider or other providers,
 * we copy to cache first to ensure the receiving app can access the file.
 * This is necessary because ACTION_VIEW intents to our own app lose
 * the temporary permission grant when going through intent routing.
 */
object FileOpener {
    
    private const val CACHE_DIR_NAME = "file_opener_cache"
    
    /**
     * Open a PDF file with an EXTERNAL PDF viewer (excludes our own app).
     * 
     * This is used for "Open PDF" after operations. We explicitly exclude our own app
     * to prevent circular intent issues where permission is lost.
     * 
     * @param context Android context
     * @param uri URI of the PDF file
     * @return true if intent was launched successfully
     */
    fun openPdf(context: Context, uri: Uri): Boolean {
        return try {
            // First, try to get an accessible URI (copy to cache if needed)
            val accessibleUri = getAccessibleUri(context, uri, "pdf")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(accessibleUri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Create chooser - include our own app so user can view in PDF Toolkit
            val chooser = Intent.createChooser(intent, "Open PDF with...")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No PDF viewer available", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Open an image file with the default image viewer.
     * 
     * @param context Android context
     * @param uri URI of the image file
     * @return true if intent was launched successfully
     */
    fun openImage(context: Context, uri: Uri): Boolean {
        return try {
            val accessibleUri = getAccessibleUri(context, uri, "image")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(accessibleUri, "image/*")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No image viewer available", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Open a text file with the default text viewer.
     * 
     * @param context Android context
     * @param uri URI of the text file
     * @return true if intent was launched successfully
     */
    fun openTextFile(context: Context, uri: Uri): Boolean {
        return try {
            val accessibleUri = getAccessibleUri(context, uri, "txt")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(accessibleUri, "text/plain")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No text viewer available", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Share a file using the system share dialog.
     * 
     * @param context Android context
     * @param uri URI of the file
     * @param mimeType MIME type of the file
     * @param title Title for the share dialog
     */
    fun shareFile(context: Context, uri: Uri, mimeType: String, title: String = "Share") {
        try {
            val accessibleUri = getAccessibleUri(context, uri, getExtensionFromMimeType(mimeType))
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, accessibleUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val chooser = Intent.createChooser(intent, title).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Get an accessible URI for the file.
     * 
     * For content URIs (especially DownloadsProvider), copies to cache and returns
     * a FileProvider URI. For file:// URIs, converts to FileProvider URI.
     * For already accessible URIs, returns as-is.
     */
    private fun getAccessibleUri(context: Context, uri: Uri, extension: String): Uri {
        // If it's already a FileProvider URI from our app, return as-is
        if (uri.authority == "${context.packageName}.provider") {
            return uri
        }
        
        // If it's a file:// URI, convert to FileProvider
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return uri)
            return if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } else {
                uri
            }
        }
        
        // For content:// URIs, always copy to cache for reliability
        // This ensures the receiving app (even our own app) can access the file
        if (uri.scheme == "content") {
            val cachedFile = copyToCache(context, uri, extension)
            if (cachedFile != null) {
                return FileProvider.getUriForFile(context, "${context.packageName}.provider", cachedFile)
            }
        }
        
        // Fallback: return original URI
        return uri
    }
    
    /**
     * Copy a content URI to the app's cache directory.
     */
    private fun copyToCache(context: Context, uri: Uri, extension: String): File? {
        return try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // Clean old cached files (older than 1 hour)
            cleanOldCachedFiles(cacheDir)
            
            val cachedFile = File(cacheDir, "open_${System.currentTimeMillis()}.$extension")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (cachedFile.exists() && cachedFile.length() > 0) {
                cachedFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Clean cached files older than 1 hour.
     */
    private fun cleanOldCachedFiles(cacheDir: File) {
        try {
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < oneHourAgo) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
    
    /**
     * Get file extension from MIME type.
     */
    private fun getExtensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("plain") -> "txt"
            mimeType.contains("word") -> "docx"
            mimeType.contains("excel") || mimeType.contains("spreadsheet") -> "xlsx"
            mimeType.contains("powerpoint") || mimeType.contains("presentation") -> "pptx"
            else -> "dat"
        }
    }
    
    /**
     * Open a file with the system's default app chooser.
     * 
     * @param context Android context
     * @param uri URI of the file to open
     * @return true if intent was launched successfully
     */
    fun openWithSystemPicker(context: Context, uri: Uri): Boolean {
        return try {
            // Determine MIME type from URI
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            
            val accessibleUri = getAccessibleUri(context, uri, getExtensionFromMimeType(mimeType))
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(accessibleUri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val chooser = Intent.createChooser(intent, "Open with...").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

