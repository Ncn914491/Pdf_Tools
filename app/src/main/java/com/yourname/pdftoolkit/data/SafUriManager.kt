package com.yourname.pdftoolkit.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import com.yourname.pdftoolkit.data.local.AppDatabase
import com.yourname.pdftoolkit.data.local.RecentFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Represents a persisted file entry with SAF URI and metadata.
 */
data class PersistedFile(
    val uriString: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastAccessed: Long
) {
    /**
     * Parse the URI from stored string.
     * May return null if the URI is invalid.
     */
    fun toUri(): Uri? {
        return try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            null
        }
    }
    
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("uriString", uriString)
            put("name", name)
            put("mimeType", mimeType)
            put("size", size)
            put("lastAccessed", lastAccessed)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): PersistedFile? {
            return try {
                PersistedFile(
                    uriString = json.getString("uriString"),
                    name = json.getString("name"),
                    mimeType = json.getString("mimeType"),
                    size = json.getLong("size"),
                    lastAccessed = json.getLong("lastAccessed")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Manages SAF (Storage Access Framework) URIs for persistent file access.
 * 
 * This class handles:
 * - Taking and releasing persistable URI permissions
 * - Storing and retrieving persisted file metadata
 * - Validating URI access
 * - Managing recent files list with proper SAF compliance
 * 
 * All files are stored as URI strings, NOT file paths.
 * This ensures proper scoped storage compliance on Android 10+.
 */
object SafUriManager {
    
    private const val TAG = "SafUriManager"
    private const val PREFS_NAME = "saf_uri_manager"
    private const val KEY_PERSISTED_FILES = "persisted_files"
    private const val KEY_MIGRATED_TO_ROOM = "migrated_to_room"
    private const val MAX_RECENT_FILES = 50
    
    /**
     * Take persistable URI permission for the given URI.
     * This must be called immediately after receiving a URI from ACTION_OPEN_DOCUMENT.
     * 
     * @param context Application context
     * @param uri The content URI to persist
     * @param intentFlags The flags from the intent data (data.flags)
     * @return true if permission was successfully taken, false otherwise
     */
    fun takePersistablePermission(
        context: Context,
        uri: Uri,
        intentFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
    ): Boolean {
        return try {
            // Only take the permissions that were actually granted
            val takeFlags = intentFlags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            // If no flags, at least try to take read permission
            val flagsToUse = if (takeFlags != 0) takeFlags else Intent.FLAG_GRANT_READ_URI_PERMISSION
            
            context.contentResolver.takePersistableUriPermission(uri, flagsToUse)
            Log.d(TAG, "Successfully took persistable permission for: $uri")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to take persistable permission: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error taking persistable permission: ${e.message}")
            false
        }
    }
    
    /**
     * Release persistable URI permission for the given URI.
     * Call this when the file is removed from the recent list.
     * 
     * @param context Application context
     * @param uri The content URI to release
     */
    fun releasePersistablePermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                       Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
            Log.d(TAG, "Released persistable permission for: $uri")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to release permission (may not have been persisted): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing permission: ${e.message}")
        }
    }
    
    /**
     * Check if we have valid access to the given URI.
     * 
     * @param context Application context
     * @param uri The content URI to check
     * @return true if we can read the URI, false otherwise
     */
    fun canAccessUri(context: Context, uri: Uri): Boolean {
        return try {
            // Try to open an input stream - this will fail if we don't have permission
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: SecurityException) {
            Log.d(TAG, "No access to URI (SecurityException): $uri")
            false
        } catch (e: IOException) {
            Log.d(TAG, "No access to URI (IOException): $uri")
            false
        } catch (e: Exception) {
            Log.d(TAG, "No access to URI (Exception): $uri")
            false
        }
    }
    
    /**
     * Get the list of URIs for which we have persistable permissions.
     * 
     * @param context Application context
     * @return List of persisted URI strings
     */
    fun getPersistedUriPermissions(context: Context): List<String> {
        return context.contentResolver.persistedUriPermissions.map { 
            it.uri.toString() 
        }
    }
    
    private fun getDao(context: Context) = AppDatabase.getDatabase(context).recentFilesDao()

    private suspend fun migrateIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED_TO_ROOM, false)) return

        val existingJson = prefs.getString(KEY_PERSISTED_FILES, "[]") ?: "[]"
        try {
            val filesArray = JSONArray(existingJson)
            val filesToMigrate = mutableListOf<RecentFileEntity>()

            for (i in 0 until filesArray.length()) {
                val fileJson = filesArray.optJSONObject(i) ?: continue
                val persistedFile = PersistedFile.fromJson(fileJson) ?: continue
                filesToMigrate.add(RecentFileEntity.fromPersistedFile(persistedFile))
            }

            if (filesToMigrate.isNotEmpty()) {
                getDao(context).insertAll(filesToMigrate)
            }

            prefs.edit {
                putBoolean(KEY_MIGRATED_TO_ROOM, true)
                remove(KEY_PERSISTED_FILES) // Clean up old data
            }
            Log.d(TAG, "Migrated ${filesToMigrate.size} files to Room")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed: ${e.message}")
        }
    }

    /**
     * Add a file to the recent files list with proper SAF permission handling.
     * 
     * @param context Application context
     * @param uri The content URI of the file
     * @param intentFlags Optional flags from the intent (for taking permission)
     * @return The PersistedFile entry if successful, null otherwise
     */
    suspend fun addRecentFile(
        context: Context,
        uri: Uri,
        intentFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
    ): PersistedFile? = withContext(Dispatchers.IO) {
        try {
            migrateIfNeeded(context)

            // Take persistable permission first
            takePersistablePermission(context, uri, intentFlags)
            
            // Get file metadata
            val (name, size, mimeType) = getFileMetadata(context, uri)
                ?: return@withContext null
            
            val persistedFile = PersistedFile(
                uriString = uri.toString(),
                name = name,
                mimeType = mimeType,
                size = size,
                lastAccessed = System.currentTimeMillis()
            )
            
            // Save to storage
            val dao = getDao(context)
            dao.insert(RecentFileEntity.fromPersistedFile(persistedFile))
            dao.prune(MAX_RECENT_FILES)
            
            persistedFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add recent file: ${e.message}")
            null
        }
    }
    
    /**
     * Get file metadata from a content URI.
     * 
     * @return Triple of (name, size, mimeType) or null if failed
     */
    private fun getFileMetadata(context: Context, uri: Uri): Triple<String, Long, String>? {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    
                    val name = if (nameIndex >= 0) {
                        cursor.getString(nameIndex) ?: "Unknown"
                    } else {
                        uri.lastPathSegment ?: "Unknown"
                    }
                    
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    
                    Triple(name, size, mimeType)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file metadata: ${e.message}")
            null
        }
    }
    
    /**
     * Load all persisted files from storage and validate their accessibility.
     * Removes entries that are no longer accessible.
     * 
     * @param context Application context
     * @return List of PersistedFile entries that are currently accessible
     */
    suspend fun loadRecentFiles(context: Context): List<PersistedFile> = withContext(Dispatchers.IO) {
        migrateIfNeeded(context)
        
        val dao = getDao(context)
        val entities = dao.getAll()
        
        val accessibleFiles = mutableListOf<PersistedFile>()
        
        for (entity in entities) {
            val persistedFile = entity.toPersistedFile()
            val uri = persistedFile.toUri()

            if (uri != null && canAccessUri(context, uri)) {
                accessibleFiles.add(persistedFile)
            } else {
                // Release permission for inaccessible URIs
                uri?.let { releasePersistablePermission(context, it) }
                dao.deleteByUri(entity.uriString)
                Log.d(TAG, "Removed inaccessible file from recent: ${persistedFile.name}")
            }
        }
        
        accessibleFiles
    }
    
    /**
     * Remove a specific file from the recent list.
     * 
     * @param context Application context
     * @param uriString The URI string to remove
     */
    suspend fun removeRecentFile(context: Context, uriString: String) = withContext(Dispatchers.IO) {
        migrateIfNeeded(context)
        val dao = getDao(context)
        dao.deleteByUri(uriString)
        
        try {
            val uri = Uri.parse(uriString)
            releasePersistablePermission(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release permission for removed file: ${e.message}")
        }
    }
    
    /**
     * Clear all persisted files and release their permissions.
     * 
     * @param context Application context
     */
    suspend fun clearAllRecentFiles(context: Context) = withContext(Dispatchers.IO) {
        migrateIfNeeded(context)
        val dao = getDao(context)
        
        // Release all permissions first
        val entities = dao.getAll()
        entities.forEach { entity ->
            try {
                val uri = Uri.parse(entity.uriString)
                releasePersistablePermission(context, uri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release permission: ${e.message}")
            }
        }
        
        dao.clearAll()
    }
    
    /**
     * Update the last accessed time for a file.
     * Call this when a file is opened from the recent list.
     * 
     * @param context Application context
     * @param uriString The URI string to update
     */
    suspend fun updateLastAccessed(context: Context, uriString: String) = withContext(Dispatchers.IO) {
        migrateIfNeeded(context)
        val dao = getDao(context)
        dao.updateLastAccessed(uriString, System.currentTimeMillis())
    }
}
