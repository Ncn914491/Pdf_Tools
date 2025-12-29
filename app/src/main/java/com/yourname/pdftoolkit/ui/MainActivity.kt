package com.yourname.pdftoolkit.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.navigation.compose.rememberNavController
import com.yourname.pdftoolkit.ui.navigation.AppNavigation
import com.yourname.pdftoolkit.ui.theme.PDFToolkitTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Main entry point for the PDF Toolkit app.
 * Handles intent-based PDF and document opening and sets up navigation.
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private var pendingPdfUri: Uri? = null
    private var pendingPdfName: String? = null
    private var pendingDocumentUri: Uri? = null
    private var pendingDocumentName: String? = null
    
    // Supported Office document MIME types
    private val officeMimeTypes = listOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // docx
        "application/msword", // doc
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // xlsx
        "application/vnd.ms-excel", // xls
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", // pptx
        "application/vnd.ms-powerpoint" // ppt
    )
    
    private val officeExtensions = listOf("docx", "doc", "xlsx", "xls", "pptx", "ppt")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle intent if app is opened with a document
        handleIntent(intent)
        
        setContent {
            PDFToolkitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    AppNavigation(
                        navController = navController,
                        modifier = Modifier.fillMaxSize(),
                        initialPdfUri = pendingPdfUri,
                        initialPdfName = pendingPdfName,
                        initialDocumentUri = pendingDocumentUri,
                        initialDocumentName = pendingDocumentName
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        
        // If we received a new document while app is open, we need to recreate
        if (pendingPdfUri != null || pendingDocumentUri != null) {
            recreate()
        }
    }
    
    /**
     * Handle incoming intent to extract file URI.
     * Supports VIEW and SEND actions for PDFs and Office documents.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        // Add read permission flag to the intent to ensure we can access the content
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    processUri(uri, intent)
                }
            }
            
            Intent.ACTION_SEND -> {
                val uri = getParcelableExtraCompat(intent)
                uri?.let {
                    processUri(it, intent)
                }
            }
        }
    }
    
    /**
     * Process a URI and determine file type.
     * Attempts to obtain permission and copies file to cache if needed.
     */
    private fun processUri(originalUri: Uri, intent: Intent) {
        val mimeType = contentResolver.getType(originalUri)
        val fileName = getFileName(originalUri)
        
        // Try to get read permission first
        val accessibleUri = getAccessibleUri(originalUri, intent, fileName ?: "document")
        
        if (accessibleUri == null) {
            Log.e(TAG, "Could not obtain access to URI: $originalUri")
            return
        }
        
        when {
            isPdfUri(originalUri, mimeType) -> {
                pendingPdfUri = accessibleUri
                pendingPdfName = fileName?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "PDF Document"
            }
            isOfficeDocument(originalUri, mimeType) -> {
                pendingDocumentUri = accessibleUri
                pendingDocumentName = fileName?.substringBeforeLast('.') ?: "Document"
            }
        }
    }
    
    /**
     * Attempts to get an accessible URI either by:
     * 1. Using persistable URI permission
     * 2. Copying file to cache and using FileProvider
     */
    private fun getAccessibleUri(uri: Uri, @Suppress("UNUSED_PARAMETER") intent: Intent, fileName: String): Uri? {
        // First, try to take persistable permission
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            // If successful, try to verify we can access it
            if (canAccessUri(uri)) {
                return uri
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "Cannot take persistable permission for: $uri")
        }
        
        // Check if we can access with intent flags
        if (canAccessUri(uri)) {
            return uri
        }
        
        // As a fallback, copy file to cache and return new URI
        return copyToCache(uri, fileName)
    }
    
    /**
     * Check if we can read from the given URI.
     */
    private fun canAccessUri(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { 
                // Successfully opened, we have access
                true 
            } ?: false
        } catch (e: SecurityException) {
            Log.d(TAG, "SecurityException accessing URI: ${e.message}")
            false
        } catch (e: IOException) {
            Log.d(TAG, "IOException accessing URI: ${e.message}")
            false
        } catch (e: Exception) {
            Log.d(TAG, "Exception accessing URI: ${e.message}")
            false
        }
    }
    
    /**
     * Copy file from content URI to app's cache directory.
     * Returns a FileProvider URI that can be used within the app.
     */
    private fun copyToCache(sourceUri: Uri, fileName: String): Uri? {
        return try {
            val cacheDir = File(cacheDir, "shared_files")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // Clean old cached files (older than 1 hour)
            cleanOldCachedFiles(cacheDir)
            
            val extension = getFileExtension(fileName)
            val safeFileName = "shared_${System.currentTimeMillis()}.$extension"
            val targetFile = File(cacheDir, safeFileName)
            
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (targetFile.exists() && targetFile.length() > 0) {
                FileProvider.getUriForFile(
                    this,
                    "$packageName.provider",
                    targetFile
                )
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException copying file: ${e.message}")
            null
        } catch (e: IOException) {
            Log.e(TAG, "IOException copying file: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Exception copying file: ${e.message}")
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
            Log.w(TAG, "Error cleaning cache: ${e.message}")
        }
    }
    
    /**
     * Get file extension from filename.
     */
    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1).lowercase()
        } else {
            "pdf"
        }
    }
    
    private fun getParcelableExtraCompat(intent: Intent): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
    
    /**
     * Check if the URI points to a PDF file.
     */
    private fun isPdfUri(uri: Uri, mimeType: String?): Boolean {
        return mimeType == "application/pdf" || 
               uri.toString().endsWith(".pdf", ignoreCase = true)
    }
    
    /**
     * Check if the URI points to an Office document.
     */
    private fun isOfficeDocument(uri: Uri, mimeType: String?): Boolean {
        if (mimeType != null && officeMimeTypes.any { it.equals(mimeType, ignoreCase = true) }) {
            return true
        }
        
        val path = uri.toString().lowercase()
        return officeExtensions.any { path.endsWith(".$it") }
    }
    
    /**
     * Get the display name of the file from URI.
     */
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        
        try {
            when (uri.scheme) {
                "content" -> {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                    }
                }
                "file" -> {
                    fileName = uri.lastPathSegment
                }
            }
        } catch (e: Exception) {
            // Fall back to URI parsing
            fileName = uri.lastPathSegment
        }
        
        return fileName
    }
}