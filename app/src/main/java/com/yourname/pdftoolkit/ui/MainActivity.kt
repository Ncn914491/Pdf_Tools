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
import com.yourname.pdftoolkit.data.SafUriManager
import com.yourname.pdftoolkit.ui.navigation.AppNavigation
import com.yourname.pdftoolkit.ui.theme.PDFToolkitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Main entry point for the PDF Toolkit app.
 * Handles intent-based PDF opening and sets up navigation.
 * 
 * IMPORTANT: This activity properly handles SAF URIs for Android 10+ compliance.
 * Files opened from external apps are either:
 * 1. Accessed directly if persistable permission can be taken
 * 2. Copied to cache and accessed via FileProvider if direct access fails
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var pendingPdfUri: Uri? = null
    private var pendingPdfName: String? = null
    
    // Compose state holders for handling intents while app is running
    private var pdfUriState: androidx.compose.runtime.MutableState<Uri?>? = null
    private var pdfNameState: androidx.compose.runtime.MutableState<String?>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle intent if app is opened with a PDF
        // This MUST happen before setContent so pendingPdfUri is set
        handleIntent(intent)
        
        Log.d(TAG, "onCreate: After handleIntent, pendingPdfUri=$pendingPdfUri, pendingPdfName=$pendingPdfName")
        
        setContent {
            PDFToolkitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Initialize state with pending values (set by handleIntent)
                    val pdfUri = remember { mutableStateOf(pendingPdfUri) }
                    val pdfName = remember { mutableStateOf(pendingPdfName) }
                    
                    // Store references for onNewIntent updates
                    pdfUriState = pdfUri
                    pdfNameState = pdfName
                    
                    Log.d(TAG, "Composing AppNavigation with initialPdfUri=${pdfUri.value}, initialPdfName=${pdfName.value}")
                    
                    AppNavigation(
                        navController = navController,
                        modifier = Modifier.fillMaxSize(),
                        initialPdfUri = pdfUri.value,
                        initialPdfName = pdfName.value
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Reset pending values
        pendingPdfUri = null
        pendingPdfName = null
        
        // Handle the new intent
        handleIntent(intent)
        
        // Update Compose state to trigger navigation
        Log.d(TAG, "onNewIntent: Updating Compose state with PDF=$pendingPdfUri")
        pdfUriState?.value = pendingPdfUri
        pdfNameState?.value = pendingPdfName
    }
    
    /**
     * Handle incoming intent to extract file URI.
     * Supports VIEW and SEND actions for PDFs.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        Log.d(TAG, "Handling intent action: ${intent.action}, data: ${intent.data}")
        
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    Log.d(TAG, "ACTION_VIEW with URI: $uri")
                    processUri(uri, intent)
                }
            }
            
            Intent.ACTION_SEND -> {
                val uri = getParcelableExtraCompat(intent)
                uri?.let {
                    Log.d(TAG, "ACTION_SEND with URI: $it")
                    processUri(it, intent)
                }
            }
        }
    }
    
    /**
     * Process a URI and determine file type.
     * For ACTION_VIEW and ACTION_SEND, immediately copies file to cache synchronously
     * because these intents don't support persistable permissions.
     */
    private fun processUri(originalUri: Uri, intent: Intent) {
        val mimeType = contentResolver.getType(originalUri)
        val fileName = getFileName(originalUri) ?: "document.pdf"
        
        Log.d(TAG, "Processing URI: $originalUri, mimeType: $mimeType, fileName: $fileName, action: ${intent.action}")
        
        // Only handle PDF files
        if (!isPdfUri(originalUri, mimeType)) {
            Log.w(TAG, "Not a PDF file, ignoring: $mimeType")
            return
        }
        
        // For ACTION_VIEW and ACTION_SEND, we MUST copy to cache synchronously
        // because these intents don't support persistable permissions and the
        // temporary permission expires as soon as onCreate finishes
        val accessibleUri = when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                Log.d(TAG, "ACTION_VIEW/SEND detected - copying to cache synchronously")
                // Copy synchronously using runBlocking to ensure it completes before onCreate finishes
                runBlocking {
                    copyToCacheSynchronous(originalUri, fileName)
                }
            }
            else -> {
                // For other intents (e.g., from internal navigation), try persistable permission first
                getAccessibleUri(originalUri, intent, fileName)
            }
        }
        
        if (accessibleUri == null) {
            Log.e(TAG, "Could not obtain access to URI: $originalUri - file will not be opened")
            return
        }
        
        Log.d(TAG, "Successfully got accessible URI: $accessibleUri (original was: $originalUri)")
        
        pendingPdfUri = accessibleUri
        pendingPdfName = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
        Log.d(TAG, "Set pending PDF: uri=$pendingPdfUri, name=$pendingPdfName")
    }
    
    /**
     * Attempts to get an accessible URI either by:
     * 1. Using persistable URI permission (for ACTION_OPEN_DOCUMENT results)
     * 2. Copying file to cache and using FileProvider (fallback)
     * 
     * NOTE: ACTION_VIEW and ACTION_SEND intents are handled directly in processUri
     * and always copied to cache immediately.
     */
    private fun getAccessibleUri(uri: Uri, intent: Intent, fileName: String): Uri? {
        // Try to take persistable permission (works for ACTION_OPEN_DOCUMENT)
        val permissionTaken = SafUriManager.takePersistablePermission(
            this, 
            uri, 
            intent.flags
        )
        
        if (permissionTaken && canAccessUri(uri)) {
            Log.d(TAG, "Access via persistable permission successful")
            
            // Also add to recent files for later access
            activityScope.launch(Dispatchers.IO) {
                SafUriManager.addRecentFile(applicationContext, uri, intent.flags)
            }
            
            return uri
        }
        
        // If persistable permission failed, copy to cache as fallback
        Log.d(TAG, "Persistable permission not available, copying to cache")
        return copyToCache(uri, fileName)
    }
    
    /**
     * Check if we can read from the given URI.
     */
    private fun canAccessUri(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { 
                // Successfully opened, we have access
                Log.d(TAG, "URI is accessible: $uri")
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
     * Copy file from content URI to app's cache directory synchronously.
     * Returns a FileProvider URI that can be used within the app.
     * 
     * This MUST be called synchronously in onCreate for ACTION_VIEW/SEND intents
     * because the temporary permission expires when onCreate finishes.
     */
    private suspend fun copyToCacheSynchronous(sourceUri: Uri, fileName: String): Uri? = withContext(Dispatchers.IO) {
        return@withContext try {
            val cacheDir = File(cacheDir, "shared_files")
            if (!cacheDir.exists()) {
                val created = cacheDir.mkdirs()
                Log.d(TAG, "Cache directory created: $created at ${cacheDir.absolutePath}")
            }
            
            // Clean old cached files (older than 1 hour)
            cleanOldCachedFiles(cacheDir)
            
            val extension = getFileExtension(fileName)
            val safeFileName = "shared_${System.currentTimeMillis()}.$extension"
            val targetFile = File(cacheDir, safeFileName)
            
            Log.d(TAG, "Starting copy: $sourceUri -> ${targetFile.absolutePath}")
            
            // Open input stream - this MUST work while we still have permission
            val inputStream = try {
                contentResolver.openInputStream(sourceUri)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException opening input stream: ${e.message}")
                Log.e(TAG, "URI: $sourceUri")
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Exception opening input stream: ${e.message}", e)
                return@withContext null
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Input stream is null for URI: $sourceUri")
                return@withContext null
            }
            
            // Copy the file
            try {
                var bytesCopied = 0L
                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        bytesCopied = input.copyTo(output)
                    }
                }
                
                Log.d(TAG, "Copied $bytesCopied bytes to ${targetFile.absolutePath}")
                
                if (targetFile.exists() && targetFile.length() > 0) {
                    // Use FileProvider to create a content URI
                    val resultUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.provider",
                        targetFile
                    )
                    Log.d(TAG, "Successfully copied to cache! File size: ${targetFile.length()} bytes")
                    Log.d(TAG, "FileProvider URI: $resultUri")
                    resultUri
                } else {
                    Log.e(TAG, "Copy failed - target file missing or empty. Exists: ${targetFile.exists()}, Size: ${targetFile.length()}")
                    null
                }
            } catch (e: IOException) {
                Log.e(TAG, "IOException during copy: ${e.message}", e)
                // Clean up partial file
                targetFile.delete()
                null
            } catch (e: Exception) {
                Log.e(TAG, "Exception during copy: ${e.message}", e)
                targetFile.delete()
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in copyToCacheSynchronous: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception in copyToCacheSynchronous: ${e.message}", e)
            null
        }
    }
    
    /**
     * Legacy method - kept for compatibility but uses the synchronous version
     */
    private fun copyToCache(sourceUri: Uri, fileName: String): Uri? {
        return runBlocking {
            copyToCacheSynchronous(sourceUri, fileName)
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